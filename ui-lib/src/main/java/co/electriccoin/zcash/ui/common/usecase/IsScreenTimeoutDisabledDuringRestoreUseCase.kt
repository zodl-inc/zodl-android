package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProvider
import co.electriccoin.zcash.ui.common.provider.KeepScreenOnSyncSessionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Whether the screen is kept on: the user asked for it on the Keep Zodl Open screen and the sync that
 * screen announced has not finished yet.
 *
 * The request is the [KeepScreenOnSyncSessionProvider] session, which covers a restore, a resync and a
 * hardware-wallet account import alike. A wallet restored before that session existed still counts
 * through the remembered answer and its restoring state. Either way the screen is released as soon as
 * no sync is running: the synchronizer reports [Synchronizer.Status.SYNCED], or the sync stopped short
 * of it as [Synchronizer.Status.DISCONNECTED] or [Synchronizer.Status.STOPPED]. It is held again if a
 * sync resumes while the session is still active.
 */
class IsScreenTimeoutDisabledDuringRestoreUseCase(
    private val walletRepository: WalletRepository,
    private val isKeepScreenOnDuringRestoreProvider: IsKeepScreenOnDuringRestoreProvider,
    private val keepScreenOnSyncSessionProvider: KeepScreenOnSyncSessionProvider,
    private val synchronizerProvider: SynchronizerProvider,
) {
    fun observe(): Flow<Boolean> =
        combine(
            walletRepository.walletRestoringState,
            isKeepScreenOnDuringRestoreProvider.observe(),
            keepScreenOnSyncSessionProvider.observe(),
            observeStatus(),
        ) { restoringState, isKeepScreenOnDuringRestore, isSessionActive, status ->
            val isRequested =
                isSessionActive == true ||
                    (isKeepScreenOnDuringRestore == true && restoringState in KEEP_OPEN_STATES)
            isRequested && status !in RELEASING_STATUSES
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeStatus(): Flow<Synchronizer.Status?> =
        synchronizerProvider.synchronizer.flatMapLatest { it?.status ?: flowOf(null) }

    companion object {
        private val KEEP_OPEN_STATES = listOf(WalletRestoringState.RESTORING, WalletRestoringState.RESYNCING)

        private val RELEASING_STATUSES =
            listOf(Synchronizer.Status.SYNCED, Synchronizer.Status.DISCONNECTED, Synchronizer.Status.STOPPED)
    }
}
