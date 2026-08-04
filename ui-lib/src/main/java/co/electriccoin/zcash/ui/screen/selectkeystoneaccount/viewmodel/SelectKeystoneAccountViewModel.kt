package co.electriccoin.zcash.ui.screen.selectkeystoneaccount.viewmodel

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.DeriveKeystoneAccountUnifiedAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.connectkeystone.neworactive.KeystoneNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.selectkeystoneaccount.SelectKeystoneAccount
import co.electriccoin.zcash.ui.screen.selectkeystoneaccount.model.SelectKeystoneAccountState
import com.keystone.module.ZcashAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SelectKeystoneAccountViewModel(
    private val args: SelectKeystoneAccount,
    parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val deriveKeystoneAccountUnifiedAddress: DeriveKeystoneAccountUnifiedAddressUseCase,
    private val navigateToErrorUseCase: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val accounts = parseKeystoneUrToZashiAccounts(args.ur)
    private val account = accounts.accounts.firstOrNull()

    private val selectedAccount = MutableStateFlow(account)
    private val derivedAddress = MutableStateFlow<String?>(null)

    init {
        account?.let { deriveAddress(it) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deriveAddress(account: ZcashAccount) =
        viewModelScope.launch {
            try {
                derivedAddress.value = deriveKeystoneAccountUnifiedAddress(account)
            } catch (e: Exception) {
                Twig.warn(e) { "Scanned Keystone account can't be used by this app" }
                navigateToErrorUseCase(ErrorArgs.KeystoneAccountUnsupported(e)) { replace(it) }
            }
        }

    val state =
        combine(selectedAccount, derivedAddress) { selection, address -> createState(selection, address) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun createState(
        selection: ZcashAccount?,
        address: String?
    ): SelectKeystoneAccountState =
        SelectKeystoneAccountState(
            onBackClick = ::onBackClick,
            title = stringRes(co.electriccoin.zcash.ui.R.string.keystone_addHWWallet_title),
            subtitle = stringRes(co.electriccoin.zcash.ui.R.string.keystone_addHWWallet_desc),
            items =
                listOfNotNull(
                    if (account != null && address != null) {
                        createCheckboxState(account, address, selection)
                    } else {
                        null
                    }
                ),
            positiveButtonState =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.R.string.select_keystone_account_positive),
                    onClick = { if (selection != null) onConfirmClick() },
                    isEnabled = selection != null,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                )
        )

    private fun createCheckboxState(
        account: ZcashAccount,
        address: String,
        selection: ZcashAccount?
    ) = ZashiExpandedCheckboxListItemState(
        title =
            account.name
                ?.takeIf { it.isNotBlank() }
                ?.let { stringRes(it) }
                ?: stringRes(co.electriccoin.zcash.ui.R.string.keystone_wallet),
        subtitle = stringResByAddress(address),
        icon = R.drawable.ic_item_keystone,
        isSelected = selection == account,
        onClick = { onSelectAccountClick(account) },
        info = null
    )

    private fun onSelectAccountClick(account: ZcashAccount) {
        selectedAccount.update {
            if (it == account) {
                null
            } else {
                account
            }
        }
    }

    private fun onBackClick() = navigationRouter.backToRoot()

    private fun onConfirmClick() = navigationRouter.forward(KeystoneNewOrActiveArgs(args.ur))
}
