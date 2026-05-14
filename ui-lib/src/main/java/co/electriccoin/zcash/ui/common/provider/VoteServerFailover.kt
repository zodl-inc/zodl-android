package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

internal suspend fun <T> withVoteServerFailover(
    path: String,
    serverUrls: List<String>,
    shouldTryNext: (Throwable) -> Boolean = ::shouldTryNextVoteServer,
    operation: suspend (String) -> T
): T {
    val normalizedServerUrls = serverUrls.normalizedVoteServerUrls()
    if (normalizedServerUrls.isEmpty()) {
        failVoteServerFailover(
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
                failVoteServerOperation(exception)
            }
        }
    }

    failVoteServerFailover(
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

private fun failVoteServerFailover(
    path: String,
    serverUrls: List<String>,
    lastError: Throwable?
): Nothing =
    throw VotingServerFailoverException(
        path = path,
        serverUrls = serverUrls,
        lastError = lastError
    )

private fun failVoteServerOperation(exception: Exception): Nothing =
    throw exception

private const val HTTP_BAD_REQUEST = 400
