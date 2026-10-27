package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
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
    private val httpClientProvider: HttpClientProvider,
    private val votingChainConfigRepository: VotingChainConfigRepository
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

        // ZIP #10 voting routes: Tor on -> Tor; Tor off -> OHTTP for the predefined config (fail closed
        // if the gateway cannot reach the host), direct for a user-selected custom config.
        val route =
            when {
                httpClientProvider.isTorEnabled() -> PirProbeRoute.Direct
                !votingChainConfigRepository.get().isOnDefaultConfig -> PirProbeRoute.Direct
                else -> PirProbeRoute.Ohttp(httpClientProvider.createOhttp())
            }

        val outcomes =
            coroutineScope {
                normalizedEndpoints
                    .map { url ->
                        async {
                            probe(route, url, expectedSnapshotHeight)
                        }
                    }.awaitAll()
            }

        return outcomes
            .firstOrNull { outcome ->
                outcome.status is PirSnapshotProbeStatus.Matching
            }?.url ?: throw PirSnapshotResolverException.NoMatchingEndpoint(
            expected = expectedSnapshotHeight,
            details = outcomes
        )
    }

    private suspend fun probe(
        route: PirProbeRoute,
        url: String,
        expectedSnapshotHeight: Long
    ): PirSnapshotProbeOutcome {
        val status =
            runCatching {
                val rootUrl = "$url/root"
                val bodyText =
                    when (route) {
                        is PirProbeRoute.Ohttp -> fetchViaOhttp(route.client, rootUrl)

                        // A fresh client per probe: under Tor each gets its own circuit (MOB-1808).
                        PirProbeRoute.Direct -> httpClientProvider.create().use { it.get(rootUrl).bodyAsText() }
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

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchViaOhttp(
        client: OhttpHttpClient,
        rootUrl: String
    ): String {
        check(client.canHandle(rootUrl)) { "PIR server $rootUrl is not reachable through the OHTTP gateway" }
        val response =
            try {
                client.execute(
                    OhttpHttpClient.Request(
                        url = rootUrl,
                        headers = listOf("Cache-Control" to "no-cache")
                    )
                )
            } catch (exception: Exception) {
                exception.rethrowIfCancellation()
                error("PIR probe via OHTTP failed: ${exception::class.simpleName}")
            }
        check(response.status in HTTP_SUCCESS) { "HTTP ${response.status}" }
        return response.body.decodeToString()
    }
}

private sealed interface PirProbeRoute {
    data object Direct : PirProbeRoute

    data class Ohttp(
        val client: OhttpHttpClient
    ) : PirProbeRoute
}

private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private val HTTP_SUCCESS = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX

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
