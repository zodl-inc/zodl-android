package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.exchangerate.picker.CurrencyConversionPickerArgs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Opens the currency conversion picker and suspends until the user either picks a [FiatCurrency]
 * or dismisses the picker. The picked currency is simply returned to the caller; it is NOT persisted
 * here (persistence happens only when the user taps Save / Enable). Modeled on
 * [NavigateToSelectSwapBlockchainUseCase].
 */
class NavigateToSelectFiatCurrencyUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline = MutableSharedFlow<SelectFiatCurrencyPipelineResult>()

    suspend operator fun invoke(selected: FiatCurrency?): FiatCurrency? {
        val args = CurrencyConversionPickerArgs(selectedCode = selected?.code)
        navigationRouter.forward(args)
        val result = pipeline.first { it.args.requestId == args.requestId }
        return when (result) {
            is SelectFiatCurrencyPipelineResult.Cancelled -> null
            is SelectFiatCurrencyPipelineResult.Selected -> result.fiatCurrency
        }
    }

    suspend fun onSelectionCancelled(args: CurrencyConversionPickerArgs) {
        pipeline.emit(SelectFiatCurrencyPipelineResult.Cancelled(args))
        navigationRouter.back()
    }

    suspend fun onSelected(fiatCurrency: FiatCurrency, args: CurrencyConversionPickerArgs) {
        pipeline.emit(SelectFiatCurrencyPipelineResult.Selected(fiatCurrency, args))
        navigationRouter.back()
    }
}

private sealed interface SelectFiatCurrencyPipelineResult {
    val args: CurrencyConversionPickerArgs

    data class Cancelled(
        override val args: CurrencyConversionPickerArgs
    ) : SelectFiatCurrencyPipelineResult

    data class Selected(
        val fiatCurrency: FiatCurrency,
        override val args: CurrencyConversionPickerArgs
    ) : SelectFiatCurrencyPipelineResult
}
