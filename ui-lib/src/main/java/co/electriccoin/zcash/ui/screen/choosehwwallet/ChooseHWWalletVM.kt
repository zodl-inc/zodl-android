package co.electriccoin.zcash.ui.screen.choosehwwallet

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

class ChooseHWWalletVM(
    getWalletAccounts: GetWalletAccountsUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<ChooseHWWalletState> =
        getWalletAccounts
            .observe()
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(getWalletAccounts.observe().value)
            )

    private fun createState(accounts: List<WalletAccount>?) =
        ChooseHWWalletState(
            title = stringRes(R.string.chooseHWWallet_title),
            subtitle = stringRes(R.string.chooseHWWallet_subtitle),
            cards =
                listOfNotNull(
                    HWWalletCardState(
                        image = imageRes(R.drawable.img_hw_wallet_keystone),
                        contentDescription = stringRes(R.string.accounts_keystone),
                        testTag = ChooseHWWalletTag.KEYSTONE_CARD,
                        onClick = ::onKeystoneClick,
                    ).takeIf { accounts.orEmpty().none { account -> account is KeystoneAccount } },
                    HWWalletCardState(
                        image = imageRes(R.drawable.img_hw_wallet_ledger),
                        contentDescription = stringRes(R.string.accounts_ledger),
                        testTag = ChooseHWWalletTag.LEDGER_CARD,
                        onClick = ::onLedgerClick,
                    ).takeIf { accounts.orEmpty().none { account -> account is LedgerAccount } },
                ),
            onBack = ::onBack,
        )

    private fun onKeystoneClick() = navigationRouter.forward(ConnectKeystoneArgs)

    private fun onLedgerClick() = navigationRouter.forward(LedgerConnectArgs)

    private fun onBack() = navigationRouter.back()
}
