package co.electriccoin.zcash.ui.screen.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.LoadedAccountBalances
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.BalancePools
import co.electriccoin.zcash.ui.common.usecase.GetBalancePoolsUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.balances.breakdown.BalanceBreakdownArgs
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
    getBalancePools: GetBalancePoolsUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<BalanceWidgetState> =
        combine(
            accountDataSource.selectedAccount.filterNotNull(),
            exchangeRateRepository.state,
            // The headline total must include locked value (see WalletBalance.locked's kdoc and
            // GetBalancePoolsUseCase) the same way the Balance Breakdown sheet's total does —
            // account.totalBalance's raw sum deliberately excludes it. Everything else in this
            // state (spendability, the expand-balance button) stays on the RAW account figures on
            // purpose: that logic answers "what can the user actually spend right now", which
            // locked value is not.
            getBalancePools.observe(),
        ) { account, exchangeRateUsd, pools ->
            createState(account, exchangeRateUsd, pools)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue =
                createState(
                    account = accountDataSource.allAccounts.value?.firstOrNull { it.isSelected },
                    exchangeRate = exchangeRateRepository.state.value,
                    pools = null,
                )
        )

    private fun createState(
        account: WalletAccount?,
        exchangeRate: ExchangeRateState,
        pools: BalancePools?,
    ) = BalanceWidgetState(
        totalBalance = pools?.total ?: account?.totalBalance,
        exchangeRate = if (args.isExchangeRateButtonEnabled) exchangeRate else null,
        button =
            when {
                !args.isBalanceButtonEnabled -> null
                account == null -> null
                else -> createBalanceButtonState(account)
            },
        showDust = args.showDust,
        onBalanceClick =
            if (args.isBalanceBreakdownEnabled && account != null) ::onBalanceClick else null
    )

    private fun createBalanceButtonState(account: WalletAccount): BalanceButtonState? =
        spendablePillState(account.loadedBalances, ::onBalanceButtonClick)

    private fun onBalanceButtonClick() = navigationRouter.forward(SpendableBalanceArgs)

    private fun onBalanceClick() = navigationRouter.forward(BalanceBreakdownArgs)
}

/**
 * The Spendable pill under the total, shared by Send and Pay (Figma "Send Max: Round 2"):
 * - balances not loaded yet (syncing): spinner;
 * - total 0: no pill (a new wallet has none);
 * - something spendable: the spendable amount, also when everything is spendable;
 * - nothing spendable but shielded funds present or pending (confirming): spinner, waiting helps;
 * - transparent funds only: 0, not the spinner, since waiting will not help; the sheet offers Shield.
 *
 * It works on the raw account figures on purpose: it answers what the user can spend now, which locked value is not.
 */
internal fun spendablePillState(balances: LoadedAccountBalances?, onClick: () -> Unit): BalanceButtonState? {
    fun pill(amount: Zatoshi?) =
        BalanceButtonState(
            icon = R.drawable.ic_balances_expand,
            text = stringRes(R.string.widget_balances_button_spendable),
            amount = amount,
            onClick = onClick
        )

    val b = balances ?: return pill(amount = null)
    val zero = Zatoshi(0)
    return when {
        b.totalBalance == zero -> null
        b.spendableShieldedBalance > zero -> pill(b.spendableShieldedBalance)
        b.isShieldedPending || b.totalShieldedBalance > zero -> pill(amount = null)
        else -> pill(zero)
    }
}

data class BalanceWidgetArgs(
    val showDust: Boolean,
    val isBalanceButtonEnabled: Boolean,
    val isExchangeRateButtonEnabled: Boolean,
    val isBalanceBreakdownEnabled: Boolean = false,
)
