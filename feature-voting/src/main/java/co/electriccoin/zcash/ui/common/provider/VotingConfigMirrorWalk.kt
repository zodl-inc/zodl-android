package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import kotlinx.coroutines.CancellationException

/**
 * Generic mirror walk shared by the static and dynamic voting-config fetch legs (MOB-1809):
 * tries [sources] in order and returns the first [operation] call that succeeds. [operation]
 * must only ever fail with a [VotingConfigException] (its call sites wrap every transport and
 * decode/validate failure into one) — a coroutine cancellation is never a [VotingConfigException]
 * and so is never intercepted here, propagating untouched. [shouldTryNext] decides whether a
 * given [VotingConfigException] is retryable (fall through to the next source) or authoritative
 * (thrown immediately, no further sources tried) — the same shape as
 * [shouldTryNextVoteServer]/[withVoteServerFailover] for the vote-server leg, but with a
 * different exhaustion contract: when every source fails, the FIRST (canonical origin's) error
 * is thrown verbatim, not wrapped in a joined/aggregate exception, since every mirror is
 * expected to serve identical content and the canonical origin's own failure is the one worth
 * reporting.
 */
internal suspend fun <TSource, T> walkConfigSources(
    sources: List<TSource>,
    emptyMessage: String,
    describe: (TSource) -> String,
    shouldTryNext: (Throwable) -> Boolean,
    operation: suspend (TSource) -> T
): T {
    if (sources.isEmpty()) {
        throw VotingConfigException(emptyMessage)
    }

    var firstError: VotingConfigException? = null
    sources.forEachIndexed { index, source ->
        try {
            val result = operation(source)
            if (index > 0) {
                Twig.info { "Voting config resolved via ${describe(source)} (attempt ${index + 1})" }
            } else {
                Twig.debug { "Voting config resolved via ${describe(source)}" }
            }
            return result
        } catch (exception: VotingConfigException) {
            exception.rethrowIfNotRetryable(shouldTryNext)
            if (firstError == null) firstError = exception
            Twig.warn(exception) { "Voting config mirror ${describe(source)} failed, trying next mirror" }
        }
    }

    throw firstError ?: VotingConfigException(emptyMessage)
}

private fun VotingConfigException.rethrowIfNotRetryable(shouldTryNext: (Throwable) -> Boolean) {
    if (!shouldTryNext(this)) {
        throw this
    }
}

/**
 * Never swallow a coroutine cancellation while wrapping a config-fetch failure — rethrowing it
 * here (instead of inline at every call site) keeps each call site's own throw count within
 * detekt's ThrowsCount budget.
 */
internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
