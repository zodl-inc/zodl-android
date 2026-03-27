package co.electriccoin.zcash.ui.screen.disconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.usecase.DisconnectUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DisconnectVM(
    private val disconnect: DisconnectUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val keystoneAccountFlow = MutableStateFlow<KeystoneAccount?>(null)
    private val confirmationDialogFlow = MutableStateFlow<ZashiConfirmationState?>(null)

    val state: StateFlow<DisconnectState?> =
        combine(
            flow {
                val keystoneAccount = disconnect.getKeystoneAccount()
                if (keystoneAccount != null) {
                    keystoneAccountFlow.value = keystoneAccount
                    emit(keystoneAccount)
                } else {
                    navigationRouter.back()
                    emit(null)
                }
            },
            confirmationDialogFlow
        ) { keystoneAccount, confirmationDialog ->
            keystoneAccount?.let { createState(it, confirmationDialog) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createState(
        keystoneAccount: KeystoneAccount,
        confirmationDialog: ZashiConfirmationState?
    ): DisconnectState =
        DisconnectState(
            header = stringRes(R.string.disconnect_hardware_wallet_header),
            title = stringRes(R.string.disconnect_hardware_wallet_title),
            subtitle = stringRes(R.string.disconnect_hardware_wallet_subtitle),
            warningTitle = stringRes(R.string.disconnect_hardware_wallet_warning_title),
            warningItems =
                listOf(
                    stringRes(R.string.disconnect_hardware_wallet_warning_item_1),
                    stringRes(R.string.disconnect_hardware_wallet_warning_item_2),
                    stringRes(R.string.disconnect_hardware_wallet_warning_item_3),
                ),
            connectedTitle = stringRes(R.string.disconnect_hardware_wallet_connected_title),
            connectedStatus = stringRes(R.string.disconnect_hardware_wallet_connected_status),
            infoText = stringRes(R.string.disconnect_hardware_wallet_info),
            disconnectButton =
                ButtonState(
                    text = stringRes(R.string.disconnect_hardware_wallet_button),
                    style = ButtonStyle.DESTRUCTIVE1,
                    onClick = { onDisconnectClick(keystoneAccount) }
                ),
            confirmationDialog = confirmationDialog,
            onBack = ::onBack,
        )

    private fun onBack() = navigationRouter.back()

    private fun onDisconnectClick(keystoneAccount: KeystoneAccount) {
        confirmationDialogFlow.value = createConfirmationState(keystoneAccount)
    }

    private fun createConfirmationState(keystoneAccount: KeystoneAccount): ZashiConfirmationState =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = stringRes(R.string.disconnect_hardware_wallet_confirmation_title),
            message = stringRes(R.string.disconnect_hardware_wallet_confirmation_message),
            primaryAction =
                ButtonState(
                    text = stringRes(R.string.disconnect_hardware_wallet_confirmation_confirm),
                    style = ButtonStyle.DESTRUCTIVE2,
                    onClick = { onConfirmDisconnect(keystoneAccount) }
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(R.string.disconnect_hardware_wallet_confirmation_cancel),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::onCancelConfirmation
                ),
            onBack = ::onCancelConfirmation
        )

    private fun onConfirmDisconnect(keystoneAccount: KeystoneAccount) {
        confirmationDialogFlow.value = null
        viewModelScope.launch {
            disconnect(keystoneAccount)
        }
    }

    private fun onCancelConfirmation() {
        confirmationDialogFlow.value = null
    }
}
