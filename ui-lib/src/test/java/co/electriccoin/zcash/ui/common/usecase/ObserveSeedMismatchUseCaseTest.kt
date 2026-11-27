package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * MOB-1397: [ObserveSeedMismatchUseCase] runs recovery on every `true` flip of
 * [SynchronizerProvider.isSeedMismatch], keeps collecting after a failed recovery, and routes a terminal
 * [SeedMismatchRecoveryException] to the error screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSeedMismatchUseCaseTest {
    private val isSeedMismatchState = MutableStateFlow(false)
    private val synchronizerProvider =
        mockk<SynchronizerProvider> {
            every { isSeedMismatch } returns isSeedMismatchState
        }
    private val recoverFromSeedMismatch = mockk<RecoverFromSeedMismatchUseCase>()
    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

    private val useCase =
        ObserveSeedMismatchUseCase(
            synchronizerProvider = synchronizerProvider,
            recoverFromSeedMismatch = recoverFromSeedMismatch,
            navigateToError = navigateToError,
        )

    @Test
    fun seedMismatchTriggersRecovery() =
        runTest {
            coEvery { recoverFromSeedMismatch() } returns Unit
            backgroundScope.launch { useCase() }
            runCurrent()

            isSeedMismatchState.value = true
            runCurrent()

            coVerify(exactly = 1) { recoverFromSeedMismatch() }
        }

    @Test
    fun recoveryFailureIsLoggedAndDoesNotCrashTheCollector() =
        runTest {
            coEvery { recoverFromSeedMismatch() } throws RuntimeException("erase failed")
            backgroundScope.launch { useCase() }
            runCurrent()

            isSeedMismatchState.value = true
            runCurrent()
            isSeedMismatchState.value = false
            runCurrent()
            isSeedMismatchState.value = true
            runCurrent()

            coVerify(exactly = 2) { recoverFromSeedMismatch() }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    @Test
    fun terminalFailureRoutesToNavigateToError() =
        runTest {
            val exception = SeedMismatchRecoveryException(3)
            coEvery { recoverFromSeedMismatch() } throws exception
            backgroundScope.launch { useCase() }
            runCurrent()

            isSeedMismatchState.value = true
            runCurrent()

            verify(exactly = 1) { navigateToError(ErrorArgs.General(exception), any()) }
        }

    /**
     * The collector's coroutine is cancelled by the propagated [CancellationException], so unlike
     * [recoveryFailureIsLoggedAndDoesNotCrashTheCollector] a further flip must not retrigger recovery.
     */
    @Test
    fun cancellationExceptionIsNotSwallowedAndStopsTheCollector() =
        runTest {
            coEvery { recoverFromSeedMismatch() } throws CancellationException("cancelled")
            val job = backgroundScope.launch { useCase() }
            runCurrent()

            isSeedMismatchState.value = true
            runCurrent()
            isSeedMismatchState.value = false
            runCurrent()
            isSeedMismatchState.value = true
            runCurrent()

            assertTrue(job.isCancelled)
            coVerify(exactly = 1) { recoverFromSeedMismatch() }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }
}
