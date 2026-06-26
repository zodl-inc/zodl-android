package co.electriccoin.zcash.ui.screen.exchangerateunavailable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.OptInExchangeRateUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExchangeRateUnavailableVM(
    private val navigationRouter: NavigationRouter,
    private val optInExchangeRate: OptInExchangeRateUseCase,
    exchangeRateRepository: ExchangeRateRepository,
) : ViewModel() {
    private val expectedCurrency =
        (exchangeRateRepository.state.value as? ExchangeRateState.Data)?.expectedCurrency ?: FiatCurrency.USD

    val state: StateFlow<ExchangeRateUnavailableState> = MutableStateFlow(createState()).asStateFlow()

    private fun createState() =
        ExchangeRateUnavailableState(
            title = stringRes(R.string.send_currencyUnavailable_title, expectedCurrency.code),
            subtitle =
                stringRes(
                    R.string.send_currencyUnavailable_desc,
                    stringResByFiatDisplayName(expectedCurrency)
                ),
            switchToUsdButton =
                ButtonState(
                    text = stringRes(R.string.send_currencyUnavailable_switchToUSD),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::onSwitchToUsd
                ),
            continueInZecButton =
                ButtonState(
                    text = stringRes(R.string.send_currencyUnavailable_continueInZEC),
                    style = ButtonStyle.SECONDARY,
                    onClick = ::onContinueInZec
                ),
            onBack = ::onContinueInZec,
        )

    private fun onSwitchToUsd() {
        viewModelScope.launch {
            optInExchangeRate(optIn = true, fiatCurrency = FiatCurrency.USD)
            navigationRouter.back()
        }
    }

    private fun onContinueInZec() = navigationRouter.back()
}
