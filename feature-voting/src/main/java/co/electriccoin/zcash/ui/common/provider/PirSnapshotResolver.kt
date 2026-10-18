package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.spackle.rethrowIfCancellation
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface PirSnapshotResolver {
    suspend fun resolve(
        endpoints: List<String>,
        expectedSnapshotHeight: Long
    ): String
}

class HttpPirSnapshotResolver(
    private val httpClientProvider: HttpClientProvider
) : PirSnapshotResolver {
    override suspend fun resolve(
        endpoints: List<String>,
        expectedSnapshotHeight: Long
    ): String {
        val normalizedEndpoints =
            endpoints
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map(String::trimTrailingSlash)
                .distinct()

        if (normalizedEndpoints.isEmpty()) {
            throw PirSnapshotResolverException.NoEndpointsConfigured
        }

        // Route PIR probes through the proper privacy path:
        // - Tor OFF: OHTTP (relay sees clearnet IP, gateway does not)
        // - Tor ON: direct over Tor
        // This prevents IP leaks to PIR servers when Tor is disabled.
        val torEnabled = httpClientProvider.isTorEnabled()
        val ohttpClient = if (!torEnabled) httpClientProvider.createOhttp() else null

        val outcomes =
            coroutineScope {
                normalizedEndpoints
                    .map { url ->
                        async {
                            probeWithRouting(url, expectedSnapshotHeight, torEnabled, ohttpClient)
                        }
                    }.awaitAll()
            }

        ohttpClient?.dispose()

        return outcomes
            .firstOrNull { outcome ->
                outcome.status is PirSnapshotProbeStatus.Matching
            }?.url ?: throw PirSnapshotResolverException.NoMatchingEndpoint(
            expected = expectedSnapshotHeight,
            details = outcomes
        )
    }

    private suspend fun probeWithRouting(
        url: String,
        expectedSnapshotHeight: Long,
        torEnabled: Boolean,
        ohttpClient: OhttpHttpClient?
    ): PirSnapshotProbeOutcome {
        val status =
            runCatching {
                val rootUrl = "$url/root"
                val bodyText =
                    if (!torEnabled && ohttpClient != null) {
                        val response =
                            try {
                                ohttpClient.execute(
                                    OhttpHttpClient.Request(
                                        url = rootUrl,
                                        headers = listOf("Cache-Control" to "no-cache")
                                    )
                                )
                            } catch (exception: Exception) {
                                exception.rethrowIfCancellation()
                                val errorType = exception::class.simpleName ?: "unknown error"
                                throw IllegalStateException("PIR probe via OHTTP failed for $rootUrl: $errorType", exception)
                            }
                        if (response.status !in 200..299) {
                            throw IllegalStateException("PIR probe returned HTTP ${response.status}")
                        }
                        String(response.body)
                    } else {
                        httpClientProvider.create().use { client ->
                            client.get(rootUrl).bodyAsText()
                        }
                    }

                val rootInfo = pirSnapshotJson.decodeFromString<PirRootInfo>(bodyText)
                val height =
                    rootInfo.height
                        ?: return@runCatching PirSnapshotProbeStatus.MissingHeight
                if (height == expectedSnapshotHeight) {
                    PirSnapshotProbeStatus.Matching(height)
                } else {
                    PirSnapshotProbeStatus.Mismatched(height)
                }
            }.getOrElse { throwable ->
                val reason =
                    when (throwable) {
                        is ResponseException -> {
                            "HTTP ${throwable.response.status.value}"
                        }
                        else -> {
                            throwable.message ?: throwable::class.simpleName ?: "unknown error"
                        }
                    }
                PirSnapshotProbeStatus.Unreachable(reason)
            }

        return PirSnapshotProbeOutcome(
            url = url,
            status = status
        )
    }
}

sealed interface PirSnapshotProbeStatus {
    data class Matching(
        val height: Long
    ) : PirSnapshotProbeStatus

    data class Mismatched(
        val height: Long
    ) : PirSnapshotProbeStatus

    data object MissingHeight : PirSnapshotProbeStatus

    data class Unreachable(
        val reason: String
    ) : PirSnapshotProbeStatus
}

data class PirSnapshotProbeOutcome(
    val url: String,
    val status: PirSnapshotProbeStatus
) {
    val shortDescription: String
        get() =
            when (status) {
                is PirSnapshotProbeStatus.Matching -> "$url: matching@${status.height}"
                is PirSnapshotProbeStatus.Mismatched -> "$url: mismatched@${status.height}"
                PirSnapshotProbeStatus.MissingHeight -> "$url: missing-height"
                is PirSnapshotProbeStatus.Unreachable -> "$url: unreachable(${status.reason})"
            }
}

sealed class PirSnapshotResolverException(
    message: String
) : IllegalStateException(message) {
    data object NoEndpointsConfigured : PirSnapshotResolverException(
        "No PIR endpoints are configured."
    )

    data class NoMatchingEndpoint(
        val expected: Long,
        val details: List<PirSnapshotProbeOutcome>
    ) : PirSnapshotResolverException(
            buildString {
                append("No PIR server matches the round's expected snapshot height ")
                append(expected)
                append(". Voting cannot proceed until a PIR server reports the matching snapshot. [")
                append(details.joinToString(separator = "; ") { outcome -> outcome.shortDescription })
                append(']')
            }
        )
}

@Serializable
private data class PirRootInfo(
    val root29: String? = null,
    val root25: String? = null,
    @SerialName("num_ranges")
    val numRanges: Int? = null,
    @SerialName("pir_depth")
    val pirDepth: Int? = null,
    val height: Long? = null
)

private val pirSnapshotJson =
    Json {
        ignoreUnknownKeys = true
    }

private fun String.trimTrailingSlash(): String =
    if (endsWith('/')) dropLast(1) else this
