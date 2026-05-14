package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow

class ConfirmResyncUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val isKeepScreenOnDuringRestoreProvider: IsKeepScreenOnDuringRestoreProvider,
    private val walletRestoringStateProvider: WalletRestoringStateProvider,
    private val navigationRouter: NavigationRouter
) {
    suspend operator fun invoke(blockHeight: BlockHeight) {
        val synchronizer = synchronizerProvider.getSynchronizer()
        synchronizer.rewindToHeight(blockHeight)
        walletRestoringStateProvider.store(WalletRestoringState.RESYNCING)
        synchronizerProvider.resetSynchronizer()
        isKeepScreenOnDuringRestoreProvider.clear()
        navigationRouter.replaceAll(KeepOpenArgs(KeepOpenFlow.RESYNC))
    }
}
