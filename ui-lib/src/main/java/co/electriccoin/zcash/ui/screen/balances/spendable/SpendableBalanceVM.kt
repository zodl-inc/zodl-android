package co.electriccoin.zcash.ui.screen.balances.spendable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import cash.z.ecc.sdk.extension.typicalFee
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.LoadedAccountBalances
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.isPending
import co.electriccoin.zcash.ui.common.usecase.GetTransactionsUseCase
import co.electriccoin.zcash.ui.common.usecase.ListTransactionData
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class SpendableBalanceVM(
    accountDataSource: AccountDataSource,
    getTransactions: GetTransactionsUseCase,
    private val navigationRouter: NavigationRouter,
    private val shieldFunds: ShieldFundsUseCase,
) : ViewModel() {
    val state =
        combine(
            accountDataSource.selectedAccount,
            getTransactions.observe()
        ) { account, transactions ->
            createState(account, transactions)
        }.filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue =
                    createState(
                        account =
                            accountDataSource.allAccounts.value
                                .orEmpty()
                                .firstOrNull { it.isSelected },
                        transactions = null
                    )
            )

    @Suppress("ReturnCount")
    private fun createState(account: WalletAccount?, transactions: List<ListTransactionData>?): SpendableBalanceState? {
        if (account == null) return null
        val balances = account.loadedBalances ?: return null
        val canShield = account.isSpendable && balances.isShieldingAvailable
        return SpendableBalanceState(
            title = stringRes(R.string.balances_spendableBalance_title),
            message = createMessage(balances, transactions, canShield),
            positive = createPositiveButton(canShield),
            onBack = ::onBack,
            rows = createInfoRows(balances, transactions),
            shieldButton = createShieldButtonState(balances, canShield)
        )
    }

    private fun createMessage(
        balances: LoadedAccountBalances,
        transactions: List<ListTransactionData>?,
        canShield: Boolean
    ): StringResource {
        val pending =
            when {
                balances.isAllShielded -> {
                    stringRes(R.string.balances_everythingDone)
                }

                balances.totalBalance > balances.spendableShieldedBalance &&
                    transactions.orEmpty().any { it.transaction.isPending } -> {
                    stringRes(R.string.balances_infoPending)
                }

                balances.totalBalance > balances.spendableShieldedBalance -> {
                    stringRes(R.string.balances_infoSyncing)
                }

                else -> {
                    null
                }
            }

        val shielding =
            stringRes(
                R.string.balances_infoShielding,
                CURRENCY_TICKER,
                stringRes(Zatoshi.typicalFee, HIDDEN),
                CURRENCY_TICKER
            ).takeIf { canShield }

        return if (pending != null && shielding != null) {
            pending + stringRes("\n\n") + shielding
        } else {
            pending ?: shielding ?: stringRes("")
        }
    }

    private fun createPositiveButton(canShield: Boolean) =
        ButtonState(
            text =
                if (canShield) {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.balances_dismiss)
                } else {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok)
                },
            onClick = ::onBack
        )

    private fun createInfoRows(
        balances: LoadedAccountBalances,
        transactions: List<ListTransactionData>?
    ): List<SpendableBalanceRowState> {
        val hasPendingTransaction = transactions.orEmpty().any { it.transaction.isPending }
        return listOfNotNull(
            SpendableBalanceRowState(
                title = stringRes(R.string.balances_spendableBalance),
                icon = imageRes(R.drawable.ic_balance_shield),
                value =
                    stringRes(balances.spendableShieldedBalance)
            ),
            when {
                balances.totalShieldedBalance > balances.spendableShieldedBalance &&
                    balances.isShieldedPending &&
                    hasPendingTransaction -> {
                    SpendableBalanceRowState(
                        title = stringRes(R.string.balances_pending),
                        icon = loadingImageRes(),
                        value = stringRes(balances.pendingShieldedBalance)
                    )
                }

                balances.totalShieldedBalance > balances.spendableShieldedBalance && hasPendingTransaction -> {
                    SpendableBalanceRowState(
                        title = stringRes(R.string.balances_pending),
                        icon = loadingImageRes(),
                        value =
                            stringRes(balances.totalShieldedBalance - balances.spendableShieldedBalance)
                    )
                }

                else -> {
                    null
                }
            },
        )
    }

    private fun createShieldButtonState(
        balances: LoadedAccountBalances,
        canShield: Boolean
    ): SpendableBalanceShieldButtonState? =
        SpendableBalanceShieldButtonState(
            amount = balances.transparentBalance,
            onShieldClick = ::onShieldClick
        ).takeIf { canShield }

    private fun onBack() = navigationRouter.back()

    private fun onShieldClick() = shieldFunds(closeCurrentScreen = true)
}
