package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProvider
import co.electriccoin.zcash.ui.common.provider.KeepScreenOnSyncSessionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MOB-2041: the screen stays on for the sync the Keep Zodl Open screen announced, including a
 * hardware-wallet import that never enters a restoring state, and is released once the wallet is synced.
 */
class IsScreenTimeoutDisabledDuringRestoreUseCaseTest {
    private val restoringState = MutableStateFlow(WalletRestoringState.SYNCING)
    private val keepScreenOnDuringRestore = MutableStateFlow<Boolean?>(null)
    private val syncSession = MutableStateFlow<Boolean?>(null)
    private val statusFlow = MutableStateFlow(Synchronizer.Status.SYNCING)
    private val synchronizerFlow =
        MutableStateFlow<Synchronizer?>(
            mockk<Synchronizer> { every { status } returns statusFlow }
        )

    private val synchronizerProvider =
        mockk<SynchronizerProvider> { every { synchronizer } returns synchronizerFlow }

    private val useCase =
        IsScreenTimeoutDisabledDuringRestoreUseCase(
            walletRepository = mockk<WalletRepository> { every { walletRestoringState } returns restoringState },
            isKeepScreenOnDuringRestoreProvider =
                mockk<IsKeepScreenOnDuringRestoreProvider> { every { observe() } returns keepScreenOnDuringRestore },
            keepScreenOnSyncSessionProvider =
                mockk<KeepScreenOnSyncSessionProvider> { every { observe() } returns syncSession },
            synchronizerProvider = synchronizerProvider,
        )

    private suspend fun isDisabled() = useCase.observe().first()

    @Test
    fun hardwareWalletImportKeepsTheScreenOnWhileSyncing() =
        runTest {
            keepScreenOnDuringRestore.value = true
            syncSession.value = true

            assertTrue(isDisabled())
        }

    @Test
    fun anActiveSessionReleasesTheScreenOnceSynced() =
        runTest {
            syncSession.value = true
            statusFlow.value = Synchronizer.Status.SYNCED

            assertFalse(isDisabled())
        }

    @Test
    fun anActiveSessionKeepsTheScreenOnWhileTheSynchronizerIsReset() =
        runTest {
            syncSession.value = true
            synchronizerFlow.value = null

            assertTrue(isDisabled())
        }

    @Test
    fun anUncheckedChoiceNeverKeepsTheScreenOn() =
        runTest {
            keepScreenOnDuringRestore.value = false
            syncSession.value = false
            restoringState.value = WalletRestoringState.RESTORING

            assertFalse(isDisabled())
        }

    @Test
    fun aRememberedAnswerAloneDoesNotKeepARoutineSyncAwake() =
        runTest {
            keepScreenOnDuringRestore.value = true

            assertFalse(isDisabled())
        }

    @Test
    fun aRestoreStartedBeforeTheSessionExistedStillKeepsTheScreenOn() =
        runTest {
            keepScreenOnDuringRestore.value = true
            restoringState.value = WalletRestoringState.RESTORING

            assertTrue(isDisabled())
        }

    @Test
    fun aRestoreStartedBeforeTheSessionExistedIsReleasedOnceSynced() =
        runTest {
            keepScreenOnDuringRestore.value = true
            restoringState.value = WalletRestoringState.RESYNCING
            statusFlow.value = Synchronizer.Status.SYNCED

            assertFalse(isDisabled())
        }
}
