package co.electriccoin.zcash.ui.screen.disconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.destructive
import co.electriccoin.zcash.ui.common.model.HWWalletAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.DisconnectUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.accountlist.AccountListArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Disconnects the hardware wallet account [DisconnectArgs.accountKeyId] names, or the selected
 * account when it names none. Opening it for anything but a hardware wallet account goes back.
 * A disconnect started from the Wallets & Hardware screen (a named account) returns to that list,
 * as removing a view-only account does; one started from the advanced settings returns to the root.
 */
class DisconnectVM(
    args: DisconnectArgs,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    getWalletAccounts: GetWalletAccountsUseCase,
    private val disconnect: DisconnectUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val confirmationDialogFlow = MutableStateFlow<ZashiConfirmationState?>(null)
    private val disconnectLce = mutableLce<Unit>()

    private val accountKeyId = args.accountKeyId

    init {
        viewModelScope.launch {
            val account =
                if (accountKeyId == null) {
                    observeSelectedWalletAccount.require().first()
                } else {
                    getWalletAccounts.require().first().findByKeyId(accountKeyId)
                }
            if (account !is HWWalletAccount) navigationRouter.back()
        }
    }

    /**
     * Skips non-hardware and missing accounts instead of mapping them to null, so the screen keeps
     * showing the disconnected account while it is deleted, the selection moves to the software
     * wallet and the navigation unwinds it.
     */
    private val hwAccount: Flow<HWWalletAccount> =
        if (accountKeyId == null) {
            observeSelectedWalletAccount()
        } else {
            getWalletAccounts.observe().map { it?.findByKeyId(accountKeyId) }
        }.mapNotNull { it as? HWWalletAccount }

    private val screenStateFlow =
        combine(hwAccount, confirmationDialogFlow, disconnectLce.state) { hwAccount, confirmationDialog, lce ->
            createState(hwAccount, confirmationDialog, lce.loading)
        }

    val state: StateFlow<LceState<DisconnectState>> =
        screenStateFlow
            .withLce(disconnectLce) {
                errorStateMapper.mapToState(
                    error = it,
                    title = stringRes(R.string.disconnectHWWallet_failureTitle),
                    message = stringRes(R.string.disconnect_hardware_wallet_error_message),
                    primaryStyle = ButtonStyle.PRIMARY,
                    secondaryStyle = ButtonStyle.SECONDARY,
                    isSupportFirst = true,
                    icon = R.drawable.ic_disconnect_sheet_info,
                )
            }.stateIn(this)

    private fun createState(
        hwAccount: HWWalletAccount,
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
            icon =
                if (hwAccount is LedgerAccount) {
                    co.electriccoin.zcash.ui.design.R.drawable.ic_item_ledger
                } else {
                    co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone
                },
            connectedTitle = hwAccount.name.takeUnless { it.isBlankText() } ?: vendorLabel(hwAccount),
            connectedStatus = stringRes(R.string.currentlyConnected),
            infoText =
                if (hwAccount is LedgerAccount) {
                    stringRes(R.string.connectedHWInfo_ledger)
                } else {
                    stringRes(R.string.connectedHWInfo)
                },
            disconnectButton =
                ButtonState(
                    text = stringRes(R.string.disconnectHWWallet_button),
                    style = ButtonStyle.DESTRUCTIVE1,
                    isLoading = isLoading,
                    onClick = { onDisconnectClick(hwAccount) }
                ),
            confirmationDialog = confirmationDialog,
            onBack = ::onBack,
        )

    /**
     * The vendor label stands in only for a name that would render empty; a hardware wallet
     * account's own name is what the Wallets & Hardware list shows for it.
     */
    private fun vendorLabel(hwAccount: HWWalletAccount): StringResource =
        if (hwAccount is LedgerAccount) {
            stringRes(R.string.ledgerHW)
        } else {
            stringRes(R.string.keystoneHW)
        }

    private fun StringResource.isBlankText() = this is StringResource.ByString && value.isBlank()

    private fun List<WalletAccount>.findByKeyId(keyId: String): WalletAccount? =
        firstOrNull { it.sdkAccount.accountUuid.toStorageKeyId() == keyId }

    private fun onBack() = navigationRouter.back()

    private fun onDisconnectClick(hwAccount: HWWalletAccount) {
        confirmationDialogFlow.value = createConfirmationState(hwAccount)
    }

    private fun createConfirmationState(hwAccount: HWWalletAccount): ZashiConfirmationState =
        ZashiConfirmationState.destructive(
            title = stringRes(R.string.deleteWallet_sheet_title),
            message = stringRes(R.string.disconnectHWWallet_sheetDesc),
            primaryText = stringRes(R.string.disconnectHWWallet_button),
            secondaryText = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
            onPrimary = { onConfirmDisconnect(hwAccount) },
            onBack = ::onCancelConfirmation,
            primaryStyle = ButtonStyle.DESTRUCTIVE1,
            icon = R.drawable.ic_disconnect_sheet_info,
        )

    private fun onConfirmDisconnect(hwAccount: HWWalletAccount) {
        confirmationDialogFlow.value = null
        disconnectLce.execute {
            disconnect(hwAccount)
            if (accountKeyId == null) {
                navigationRouter.backToRoot()
            } else {
                navigationRouter.backTo(AccountListArgs::class)
            }
        }
    }

    private fun onCancelConfirmation() {
        confirmationDialogFlow.value = null
    }
}
