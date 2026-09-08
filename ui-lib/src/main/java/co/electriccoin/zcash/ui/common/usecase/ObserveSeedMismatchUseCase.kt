package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter

/**
 * Runs [RecoverFromSeedMismatchUseCase] whenever [SynchronizerProvider.isSeedMismatch] flips to `true`.
 * Collects forever and is launched once from the application class, so recovery does not depend on the
 * UI lifecycle. A terminal [SeedMismatchRecoveryException] is routed to the error screen; other failures
 * are logged and the collector keeps running.
 */
class ObserveSeedMismatchUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val recoverFromSeedMismatch: RecoverFromSeedMismatchUseCase,
    private val navigateToError: NavigateToErrorUseCase,
) {
    suspend operator fun invoke() {
        synchronizerProvider.isSeedMismatch
            .filter { it }
            .collect { runRecovery() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runRecovery() {
        try {
            recoverFromSeedMismatch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SeedMismatchRecoveryException) {
            Twig.error(e) { "Seed-mismatch recovery failed, retries exhausted" }
            navigateToError(ErrorArgs.General(e))
        } catch (e: Exception) {
            Twig.error(e) { "Seed-mismatch recovery failed" }
        }
    }
}
