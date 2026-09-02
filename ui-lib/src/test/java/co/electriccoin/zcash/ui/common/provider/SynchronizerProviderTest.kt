package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletCoordinator
import co.electriccoin.zcash.ui.common.migration.MigrationSyncedHook
import co.electriccoin.zcash.ui.common.usecase.RecoverFromSeedMismatchUseCase
import co.electriccoin.zcash.ui.common.usecase.SeedMismatchRecoveryException
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * MOB-1397: covers the seed-mismatch auto-recovery collector that lives on
 * [SynchronizerProviderImpl] (moved here from the previously activity-scoped
 * [co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel] so it survives regardless of UI
 * lifecycle and only ever runs once per process — see [SynchronizerProviderImpl]'s KDoc).
 *
 * The collector runs on this provider's own internal [kotlinx.coroutines.CoroutineScope], which
 * uses a real dispatcher rather than a test dispatcher, so these tests synchronize on real
 * completion signals ([CompletableDeferred]) and short real-time delays instead of virtual time.
 */
class SynchronizerProviderTest {
    private val isSeedMismatchState = MutableStateFlow(false)
    private val walletCoordinator =
        mockk<WalletCoordinator> {
            every { synchronizer } returns MutableStateFlow<Synchronizer?>(null)
            every { isSeedMismatch } returns isSeedMismatchState
        }
    private val persistableWalletProvider = mockk<PersistableWalletProvider>(relaxed = true)
    private val migrationSyncedHook = lazy<MigrationSyncedHook> { error("not expected to resolve in these tests") }
    private val recoverFromSeedMismatch = mockk<RecoverFromSeedMismatchUseCase>()
    private val navigateToErrorUseCase = mockk<NavigateToErrorUseCase>(relaxed = true)

    private fun createProvider() =
        SynchronizerProviderImpl(
            walletCoordinator = walletCoordinator,
            persistableWalletProvider = persistableWalletProvider,
            migrationSyncedHook = migrationSyncedHook,
            recoverFromSeedMismatch = recoverFromSeedMismatch,
            navigateToErrorUseCase = navigateToErrorUseCase,
        )

    @Test
    fun seedMismatchTriggersRecovery() {
        val recovered = CompletableDeferred<Unit>()
        coEvery { recoverFromSeedMismatch() } coAnswers { recovered.complete(Unit) }
        createProvider()

        isSeedMismatchState.value = true

        awaitSignal(recovered)
        coVerify(exactly = 1) { recoverFromSeedMismatch() }
    }

    /**
     * The intermediate [quiesce] calls give the collector's real background dispatcher a chance to
     * actually observe the `false` state before flipping back to `true` — StateFlow only guarantees
     * the latest value, so two writes with no real dispatch in between could otherwise collapse
     * into a single emission.
     */
    @Test
    fun recoveryFailureIsLoggedAndDoesNotCrashTheCollector() {
        val firstAttempt = CompletableDeferred<Unit>()
        val secondAttempt = CompletableDeferred<Unit>()
        var invocationCount = 0
        coEvery { recoverFromSeedMismatch() } coAnswers {
            invocationCount += 1
            if (invocationCount == 1) firstAttempt.complete(Unit) else secondAttempt.complete(Unit)
            error("erase failed")
        }
        createProvider()

        isSeedMismatchState.value = true
        awaitSignal(firstAttempt)

        quiesce()
        isSeedMismatchState.value = false
        quiesce()
        isSeedMismatchState.value = true
        awaitSignal(secondAttempt)

        coVerify(exactly = 2) { recoverFromSeedMismatch() }
        verify(exactly = 0) { navigateToErrorUseCase(any(), any()) }
    }

    @Test
    fun terminalFailureRoutesToNavigateToErrorUseCase() {
        val routed = CompletableDeferred<Unit>()
        val exception = SeedMismatchRecoveryException(3)
        coEvery { recoverFromSeedMismatch() } throws exception
        every { navigateToErrorUseCase(any(), any()) } answers { routed.complete(Unit) }
        createProvider()

        isSeedMismatchState.value = true

        awaitSignal(routed)
        verify(exactly = 1) { navigateToErrorUseCase(ErrorArgs.General(exception), any()) }
    }

    /**
     * The collector's coroutine is cancelled by the propagated [CancellationException], so unlike
     * [recoveryFailureIsLoggedAndDoesNotCrashTheCollector] (which keeps the collector alive), a
     * further flip must not retrigger recovery.
     */
    @Test
    fun cancellationExceptionIsNotSwallowedAndStopsTheCollector() {
        val firstAttempt = CompletableDeferred<Unit>()
        coEvery { recoverFromSeedMismatch() } coAnswers {
            firstAttempt.complete(Unit)
            throw CancellationException("cancelled")
        }
        createProvider()

        isSeedMismatchState.value = true
        awaitSignal(firstAttempt)

        quiesce()
        isSeedMismatchState.value = false
        quiesce()
        isSeedMismatchState.value = true
        quiesce()

        coVerify(exactly = 1) { recoverFromSeedMismatch() }
        verify(exactly = 0) { navigateToErrorUseCase(any(), any()) }
    }

    private fun awaitSignal(signal: CompletableDeferred<Unit>) =
        runBlocking { withTimeout(SIGNAL_TIMEOUT) { signal.await() } }

    private fun quiesce() = runBlocking { delay(QUIESCENCE_WINDOW) }

    private companion object {
        private val SIGNAL_TIMEOUT = 5.seconds
        private val QUIESCENCE_WINDOW = 200.milliseconds
    }
}
