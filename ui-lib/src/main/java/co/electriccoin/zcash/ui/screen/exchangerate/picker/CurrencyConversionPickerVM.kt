package co.electriccoin.zcash.ui.screen.exchangerate.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.common.model.supportedFiatCurrencies
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectFiatCurrencyUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CurrencyConversionPickerVM(
    private val args: CurrencyConversionPickerArgs,
    private val navigateToSelectFiatCurrency: NavigateToSelectFiatCurrencyUseCase,
) : ViewModel() {
    val state: StateFlow<CurrencyConversionPickerState> = MutableStateFlow(createState())

    private fun createState() =
        CurrencyConversionPickerState(
            items =
                supportedFiatCurrencies.map {
                    CurrencyConversionPickerItemState(
                        key = it.code,
                        code = stringRes(it.code),
                        name = stringResByFiatDisplayName(it),
                        isSelected = it.code == args.selectedCode,
                        onClick = { onCurrencyClick(it) }
                    )
                },
            onBack = ::onBack
        )

    private fun onCurrencyClick(currency: FiatCurrency) =
        viewModelScope.launch { navigateToSelectFiatCurrency.onSelected(currency, args) }

    private fun onBack() = viewModelScope.launch { navigateToSelectFiatCurrency.onSelectionCancelled(args) }
}
