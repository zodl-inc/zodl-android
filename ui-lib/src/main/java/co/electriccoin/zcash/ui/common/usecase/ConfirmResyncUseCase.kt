package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.screen.restoresuccess.WrapRestoreSuccessArgs

class ConfirmResyncUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val isKeepScreenOnDuringRestoreProvider: IsKeepScreenOnDuringRestoreProvider,
    private val navigationRouter: NavigationRouter
) {
    suspend operator fun invoke(blockHeight: BlockHeight) {
        // val synchronizer = synchronizerProvider.getSynchronizer()
        // synchronizer.rescanFromHeight(blockHeight)
        synchronizerProvider.resetSynchronizer()
        isKeepScreenOnDuringRestoreProvider.clear()
        navigationRouter.replaceAll(WrapRestoreSuccessArgs)
    }
}
