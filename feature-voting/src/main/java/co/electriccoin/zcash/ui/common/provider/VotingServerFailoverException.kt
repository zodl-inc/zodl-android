package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

internal class VotingServerFailoverException(
    val path: String,
    val serverUrls: List<String>,
    val lastError: Throwable?
) : IllegalStateException(
        "All configured vote servers failed for $path",
        lastError
    )

internal suspend fun <T> withVoteServerFailover(
    path: String,
    serverUrls: List<String>,
    shouldTryNext: (Throwable) -> Boolean = ::shouldTryNextVoteServer,
    operation: suspend (String) -> T
): T {
    val normalizedServerUrls = serverUrls.normalizedVoteServerUrls()
    if (normalizedServerUrls.isEmpty()) {
        throw VotingServerFailoverException(
            path = path,
            serverUrls = emptyList(),
            lastError = null
        )
    }

    var lastError: Throwable? = null
    for (serverUrl in normalizedServerUrls) {
        try {
            return operation(serverUrl)
        } catch (exception: TimeoutCancellationException) {
            // MOB-1811: a per-server attempt is now bounded by the caller wrapping [operation]
            // in withConfigRequestTimeoutFallback (VotingApiProvider.kt) to stop a vote server
            // that's dropping - not refusing - Tor connections from hanging this whole failover
            // walk. TimeoutCancellationException EXTENDS CancellationException, so it MUST be
            // caught here, before the plain CancellationException clause below, or a deliberate
            // per-attempt timeout would be misread as genuine outer cancellation and abort the
            // walk instead of falling through to the next server.
            lastError = exception
            if (!shouldTryNext(exception)) {
                throw exception
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            lastError = exception
            if (!shouldTryNext(exception)) {
                throw exception
            }
        }
    }

    throw VotingServerFailoverException(
        path = path,
        serverUrls = normalizedServerUrls,
        lastError = lastError
    )
}

internal fun shouldTryNextVoteServer(throwable: Throwable): Boolean =
    when (throwable) {
        // MOB-1811: must precede the plain CancellationException branch below - a
        // TimeoutCancellationException is this attempt's own bounded deadline firing, not a
        // reason to give up on the remaining servers.
        is TimeoutCancellationException -> true

        is CancellationException -> false

        is VotingConfigException -> false

        is ResponseException -> throwable.response.status.value >= HTTP_BAD_REQUEST

        else -> true
    }

internal fun List<String>.normalizedVoteServerUrls(): List<String> =
    map(String::trim)
        .filter(String::isNotEmpty)
        .map { serverUrl -> serverUrl.trimEnd('/') }
        .distinct()

private const val HTTP_BAD_REQUEST = 400
