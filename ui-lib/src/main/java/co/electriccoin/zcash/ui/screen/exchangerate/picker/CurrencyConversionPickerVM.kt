package co.electriccoin.zcash.ui.screen.exchangerate.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.common.repository.FiatCurrenciesData
import co.electriccoin.zcash.ui.common.repository.FiatCurrencyRepository
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectFiatCurrencyUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencyConversionPickerVM(
    private val args: CurrencyConversionPickerArgs,
    private val navigateToSelectFiatCurrency: NavigateToSelectFiatCurrencyUseCase,
    private val fiatCurrencyRepository: FiatCurrencyRepository,
) : ViewModel() {
    init {
        fiatCurrencyRepository.ensureLoaded()
    }

    val state: StateFlow<CurrencyConversionPickerState> =
        fiatCurrencyRepository.currencies
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(fiatCurrencyRepository.currencies.value)
            )

    /**
     * Unlike the swap asset picker, the idle record (nothing loaded, nothing failed) maps to
     * [CurrencyConversionPickerDataState.Loading] rather than to an error, because the fetch is
     * kicked off from this ViewModel's initializer and is therefore always about to start.
     */
    private fun createState(data: FiatCurrenciesData) =
        CurrencyConversionPickerState(
            data =
                when {
                    data.data != null -> {
                        CurrencyConversionPickerDataState.Success(
                            data.data.map {
                                CurrencyConversionPickerItemState(
                                    key = it.code,
                                    code = stringRes(it.code),
                                    name = stringResByFiatDisplayName(it),
                                    isSelected = it.code == args.selectedCode,
                                    onClick = { onCurrencyClick(it) }
                                )
                            }
                        )
                    }

                    data.error != null -> {
                        CurrencyConversionPickerDataState.Error(
                            stringRes(co.electriccoin.zcash.ui.design.R.string.coinVote_error_title),
                            stringRes(co.electriccoin.zcash.ui.R.string.currencyConversion_pickerErrorDesc),
                            ButtonState(
                                text = stringRes(co.electriccoin.zcash.ui.design.R.string.disconnectHWWallet_tryAgain),
                                onClick = ::onRetryClick
                            )
                        )
                    }

                    else -> {
                        CurrencyConversionPickerDataState.Loading
                    }
                },
            onBack = ::onBack
        )

    private fun onCurrencyClick(currency: FiatCurrency) =
        viewModelScope.launch { navigateToSelectFiatCurrency.onSelected(currency, args) }

    private fun onBack() = viewModelScope.launch { navigateToSelectFiatCurrency.onSelectionCancelled(args) }

    private fun onRetryClick() = fiatCurrencyRepository.requestRefresh()
}
