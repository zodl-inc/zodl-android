package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

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
