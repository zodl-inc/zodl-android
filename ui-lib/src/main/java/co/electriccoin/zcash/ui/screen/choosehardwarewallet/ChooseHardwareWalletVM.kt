package co.electriccoin.zcash.ui.screen.choosehardwarewallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.connect.ConnectKeystoneArgs
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ChooseHardwareWalletVM(
    getWalletAccounts: GetWalletAccountsUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<ChooseHardwareWalletState> =
        getWalletAccounts
            .observe()
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(getWalletAccounts.observe().value)
            )

    private fun createState(accounts: List<WalletAccount>?) =
        ChooseHardwareWalletState(
            title = stringRes(R.string.chooseHardwareWallet_title),
            subtitle = stringRes(R.string.chooseHardwareWallet_subtitle),
            cards =
                listOfNotNull(
                    HardwareWalletCardState(
                        image = imageRes(R.drawable.img_hardware_wallet_keystone),
                        contentDescription = stringRes(R.string.accounts_keystone),
                        testTag = ChooseHardwareWalletTag.KEYSTONE_CARD,
                        onClick = ::onKeystoneClick,
                    ).takeIf { accounts.orEmpty().none { account -> account is KeystoneAccount } },
                    HardwareWalletCardState(
                        image = imageRes(R.drawable.img_hardware_wallet_ledger),
                        contentDescription = stringRes(R.string.accounts_ledger),
                        testTag = ChooseHardwareWalletTag.LEDGER_CARD,
                        onClick = ::onLedgerClick,
                    ).takeIf { accounts.orEmpty().none { account -> account is LedgerAccount } },
                ),
            onBack = ::onBack,
        )

    private fun onKeystoneClick() = navigationRouter.forward(ConnectKeystoneArgs)

    private fun onLedgerClick() = navigationRouter.forward(LedgerConnectArgs)

    private fun onBack() = navigationRouter.back()
}
