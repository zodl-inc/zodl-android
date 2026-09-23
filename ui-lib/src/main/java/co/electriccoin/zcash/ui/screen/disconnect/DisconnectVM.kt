package co.electriccoin.zcash.ui.screen.disconnect

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.destructive
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.DisconnectUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class DisconnectVM(
    private val args: DisconnectArgs,
    private val disconnect: DisconnectUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val initLce = mutableLce<WalletAccount>()
    private val confirmationDialogFlow = MutableStateFlow<ZashiConfirmationState?>(null)
    private val disconnectLce = mutableLce<Unit>()

    init {
        initLce.execute {
            val account = disconnect.getHWWalletAccount(args.accountStorageKeyId)
            if (account == null) navigationRouter.back()
            account ?: error("No hardware wallet account")
        }
    }

    private val screenStateFlow =
        combine(initLce.state, confirmationDialogFlow, disconnectLce.state) { init, confirmationDialog, lce ->
            init.success?.let { hwAccount ->
                createState(hwAccount, confirmationDialog, lce.loading)
            }
        }

    val state: StateFlow<LceState<DisconnectState>> =
        screenStateFlow
            .withLce(groupLce(initLce, disconnectLce)) {
                errorStateMapper.mapToState(
                    error = it,
                    title = stringRes(R.string.disconnectHWWallet_failureTitle),
                    message = stringRes(R.string.disconnect_hw_wallet_error_message),
                    primaryStyle = ButtonStyle.DESTRUCTIVE2,
                )
            }.stateIn(this)

    private fun createState(
        hwAccount: WalletAccount,
        confirmationDialog: ZashiConfirmationState?,
        isLoading: Boolean,
    ): DisconnectState =
        DisconnectState(
            header = stringRes(R.string.disconnectHWWallet_title),
            title =
                if (hwAccount is LedgerAccount) {
                    stringRes(R.string.ledger_disconnect_title)
                } else {
                    stringRes(R.string.deleteKeystoneTitle)
                },
            subtitle = stringRes(R.string.deleteKeystoneDesc),
            warningTitle = stringRes(R.string.disconnectHWWallet_mayInclude),
            warningItems =
                listOf(
                    stringRes(R.string.disconnectHWWallet_bullet1),
                    stringRes(R.string.disconnectHWWallet_bullet2),
                    stringRes(R.string.disconnectHWWallet_bullet3),
                ),
            connectedTitle =
                if (hwAccount is LedgerAccount) {
                    stringRes(R.string.ledgerHW)
                } else {
                    stringRes(R.string.keystoneHW)
                },
            connectedStatus = stringRes(R.string.currentlyConnected),
            infoText = stringRes(R.string.connectedHWInfo),
            disconnectButton =
                ButtonState(
                    text = stringRes(R.string.disconnectHWWallet_title),
                    style = ButtonStyle.DESTRUCTIVE1,
                    isLoading = isLoading,
                    onClick = { onDisconnectClick(hwAccount) }
                ),
            confirmationDialog = confirmationDialog,
            onBack = ::onBack,
        )

    private fun onBack() = navigationRouter.back()

    private fun onDisconnectClick(hwAccount: WalletAccount) {
        confirmationDialogFlow.value = createConfirmationState(hwAccount)
    }

    private fun createConfirmationState(hwAccount: WalletAccount): ZashiConfirmationState =
        ZashiConfirmationState.destructive(
            title = stringRes(R.string.deleteWallet_sheet_title),
            message = stringRes(R.string.disconnectHWWallet_sheetDesc),
            primaryText = stringRes(R.string.disconnectHWWallet_title),
            secondaryText = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
            onPrimary = { onConfirmDisconnect(hwAccount) },
            onBack = ::onCancelConfirmation,
        )

    private fun onConfirmDisconnect(hwAccount: WalletAccount) {
        confirmationDialogFlow.value = null
        disconnectLce.execute {
            disconnect(hwAccount)
            navigationRouter.backToRoot()
        }
    }

    private fun onCancelConfirmation() {
        confirmationDialogFlow.value = null
    }
}
