package co.electriccoin.zcash.ui.screen.taxexport

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.ExportTaxUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaxExportViewModel(
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val exportTax: ExportTaxUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<TaxExportState> =
        getSelectedWalletAccount
            .observe()
            .map {
                createState(it)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(null)
            )

    private fun createState(selectedAccount: WalletAccount?) =
        TaxExportState(
            exportButton =
                ButtonState(
                    text = stringRes(R.string.taxExport_download),
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                    onClick = ::onExportClick
                ),
            onBack = ::onBack,
            text =
                stringRes(
                    R.string.tax_export_message,
                    if (selectedAccount is ZashiAccount) {
                        stringRes(R.string.zashi_wallet_name)
                    } else {
                        stringRes(R.string.keystone_wallet_name)
                    }
                )
        )

    private fun onExportClick() =
        viewModelScope.launch {
            exportTax()
        }

    private fun onBack() {
        navigationRouter.back()
    }
}
