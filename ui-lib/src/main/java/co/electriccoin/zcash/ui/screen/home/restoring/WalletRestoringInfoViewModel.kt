package co.electriccoin.zcash.ui.screen.home.restoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.WalletSnapshotDataSource
import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WalletRestoringInfoViewModel(
    walletSnapshotDataSource: WalletSnapshotDataSource,
    private val navigationRouter: NavigationRouter
) : ViewModel() {
    val state =
        walletSnapshotDataSource
            .observe()
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(walletSnapshotDataSource.observe().value)
            )

    private fun createState(snapshot: WalletSnapshot?) =
        WalletRestoringInfoState(
            onBack = ::onBack,
            info =
                stringRes(R.string.smartBanner_help_restore_warning)
                    .takeIf { snapshot?.isSpendable in listOf(null, false) }
        )

    private fun onBack() = navigationRouter.back()
}
