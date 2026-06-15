package co.electriccoin.zcash.ui.screen.exchangerate.optin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectFiatCurrencyUseCase
import co.electriccoin.zcash.ui.common.usecase.OptInExchangeRateUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import co.electriccoin.zcash.ui.screen.exchangerate.settings.CurrencyFieldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExchangeRateOptInVM(
    private val optInExchangeRate: OptInExchangeRateUseCase,
    private val navigationRouter: NavigationRouter,
    private val preferredFiatProvider: PreferredFiatProvider,
    private val navigateToSelectFiatCurrency: NavigateToSelectFiatCurrencyUseCase,
) : ViewModel() {
    private val selectedCurrency = MutableStateFlow<FiatCurrency?>(null)

    val state: StateFlow<ExchangeRateOptInState?> =
        selectedCurrency
            .filterNotNull()
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    init {
        viewModelScope.launch {
            selectedCurrency.update { preferredFiatProvider.get() ?: FiatCurrency.USD }
        }
    }

    private fun createState(currency: FiatCurrency) =
        ExchangeRateOptInState(
            currencyField =
                if (VersionInfo.IS_CMC_AVAILABLE) {
                    CurrencyFieldState(
                        code = stringRes(currency.code),
                        name = stringResByFiatDisplayName(currency),
                        onClick = ::onSelectCurrencyClick
                    )
                } else {
                    null
                },
            onBack = ::dismissOptInExchangeRateUsd,
            onEnableClick = ::optInExchangeRateUsd,
            onSkipClick = ::onSkipClick
        )

    private fun onSkipClick() = viewModelScope.launch { optInExchangeRate(false) }

    private fun optInExchangeRateUsd() =
        viewModelScope.launch { optInExchangeRate(true, selectedCurrency.value) }

    private fun dismissOptInExchangeRateUsd() = navigationRouter.back()

    private fun onSelectCurrencyClick() =
        viewModelScope.launch {
            navigateToSelectFiatCurrency(selectedCurrency.value)?.let { picked ->
                selectedCurrency.update { picked }
            }
        }
}
