package co.electriccoin.zcash.ui.screen.balances.breakdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.BalancePools
import co.electriccoin.zcash.ui.common.usecase.GetBalancePoolsUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.MathContext

class BalanceBreakdownVM(
    getBalancePools: GetBalancePoolsUseCase,
    exchangeRateRepository: ExchangeRateRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<BalanceBreakdownState?> =
        combine(
            getBalancePools.observe(),
            exchangeRateRepository.state,
        ) { pools, exchangeRate ->
            createState(pools, exchangeRate)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createState(pools: BalancePools?, exchangeRate: ExchangeRateState): BalanceBreakdownState? {
        if (pools == null) return null
        return BalanceBreakdownState(
            title = stringRes(R.string.balance_breakdown_title),
            subtitle = stringRes(R.string.balance_breakdown_subtitle),
            total =
                BalanceBreakdownItemState(
                    title = stringRes(R.string.balance_breakdown_total),
                    amount = pools.total,
                    fiat = fiatOf(pools.total, exchangeRate)
                ),
            pools =
                listOf(
                    poolItem(R.string.balance_breakdown_pool_ironwood, pools.ironwood, exchangeRate),
                    poolItem(R.string.balance_breakdown_pool_orchard, pools.orchard, exchangeRate),
                    poolItem(R.string.balance_breakdown_pool_sapling, pools.sapling, exchangeRate),
                    poolItem(R.string.balance_breakdown_pool_transparent, pools.transparent, exchangeRate),
                ),
            positive =
                ButtonState(
                    text = stringRes(R.string.balance_breakdown_got_it),
                    onClick = ::onBack
                ),
            onBack = ::onBack,
        )
    }

    private fun poolItem(
        titleRes: Int,
        amount: Zatoshi,
        exchangeRate: ExchangeRateState
    ) = BalanceBreakdownItemState(
        title = stringRes(titleRes),
        amount = amount,
        fiat = fiatOf(amount, exchangeRate)
    )

    private fun fiatOf(amount: Zatoshi, exchangeRate: ExchangeRateState): StringResource? {
        val data = exchangeRate as? ExchangeRateState.Data
        val conversion = data?.currencyConversion ?: return null
        return stringResByDynamicCurrencyNumber(
            amount =
                amount
                    .convertZatoshiToZec()
                    .multiply(
                        BigDecimal(conversion.priceOfZec),
                        MathContext.DECIMAL128
                    ),
            ticker = data.expectedCurrency.symbol,
            tickerLocation = TickerLocation.BEFORE
        )
    }

    private fun onBack() = navigationRouter.back()
}
