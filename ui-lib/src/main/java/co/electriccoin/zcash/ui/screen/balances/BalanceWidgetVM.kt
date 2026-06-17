package co.electriccoin.zcash.ui.screen.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.balances.spendable.SpendableBalanceArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class BalanceWidgetVM(
    private val args: BalanceWidgetArgs,
    accountDataSource: AccountDataSource,
    exchangeRateRepository: ExchangeRateRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<BalanceWidgetState> =
        combine(
            accountDataSource.selectedAccount.filterNotNull(),
            exchangeRateRepository.state,
        ) { account, exchangeRateUsd ->
            createState(account, exchangeRateUsd)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue =
                createState(
                    account = accountDataSource.allAccounts.value?.firstOrNull { it.isSelected },
                    exchangeRate = exchangeRateRepository.state.value
                )
        )

    @Suppress("CyclomaticComplexMethod")
    private fun createState(account: WalletAccount?, exchangeRate: ExchangeRateState) =
        BalanceWidgetState(
            totalBalance = account?.totalBalance ?: Zatoshi(0),
            exchangeRate = if (args.isExchangeRateButtonEnabled) exchangeRate else null,
            button =
                when {
                    !args.isBalanceButtonEnabled -> {
                        null
                    }

                    account == null -> {
                        null
                    }

                    account.isAllShielded -> {
                        null
                    }

                    account.totalBalance > account.spendableShieldedBalance &&
                        account.isShieldedPending &&
                        account.totalShieldedBalance > Zatoshi(0) &&
                        account.spendableShieldedBalance == Zatoshi(0) -> {
                        BalanceButtonState(
                            icon = R.drawable.ic_balances_expand,
                            text = stringRes(R.string.widget_balances_button_spendable),
                            amount = null,
                            onClick = ::onBalanceButtonClick
                        )
                    }

                    account.totalBalance > account.spendableShieldedBalance &&
                        !account.isShieldedPending &&
                        account.totalShieldedBalance > Zatoshi(0) &&
                        account.spendableShieldedBalance == Zatoshi(0) &&
                        account.totalTransparentBalance == Zatoshi(0) -> {
                        BalanceButtonState(
                            icon = R.drawable.ic_balances_expand,
                            text = stringRes(R.string.widget_balances_button_spendable),
                            amount = null,
                            onClick = ::onBalanceButtonClick
                        )
                    }

                    account.totalBalance > account.spendableShieldedBalance -> {
                        BalanceButtonState(
                            icon = R.drawable.ic_balances_expand,
                            text = stringRes(R.string.widget_balances_button_spendable),
                            amount = account.spendableShieldedBalance,
                            onClick = ::onBalanceButtonClick
                        )
                    }

                    else -> {
                        null
                    }
                },
            showDust = args.showDust
        )

    private fun onBalanceButtonClick() = navigationRouter.forward(SpendableBalanceArgs)
}

data class BalanceWidgetArgs(
    val showDust: Boolean,
    val isBalanceButtonEnabled: Boolean,
    val isExchangeRateButtonEnabled: Boolean,
)
