package co.electriccoin.zcash.ui.screen.exchangerate.picker

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.Itemizable
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import co.electriccoin.zcash.ui.screen.home.common.CommonErrorScreenState

data class CurrencyConversionPickerState(
    val data: CurrencyConversionPickerDataState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            CurrencyConversionPickerState(
                onBack = {},
                data =
                    CurrencyConversionPickerDataState.Success(
                        listOf(
                            CurrencyConversionPickerItemState(
                                key = "USD",
                                code = stringRes("USD"),
                                name = stringResByFiatDisplayName(FiatCurrency("USD")),
                                isSelected = true,
                                onClick = {}
                            ),
                            CurrencyConversionPickerItemState(
                                key = "EUR",
                                code = stringRes("EUR"),
                                name = stringResByFiatDisplayName(FiatCurrency("EUR")),
                                isSelected = false,
                                onClick = {}
                            ),
                            CurrencyConversionPickerItemState(
                                key = "JPY",
                                code = stringRes("JPY"),
                                name = stringResByFiatDisplayName(FiatCurrency("JPY")),
                                isSelected = false,
                                onClick = {}
                            ),
                        )
                    )
            )

        val previewLoading =
            CurrencyConversionPickerState(
                onBack = {},
                data = CurrencyConversionPickerDataState.Loading
            )

        val previewError =
            CurrencyConversionPickerState(
                onBack = {},
                data =
                    CurrencyConversionPickerDataState.Error(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle"),
                        buttonState = ButtonState(stringRes("text"))
                    )
            )
    }
}

sealed interface CurrencyConversionPickerDataState {
    data object Loading : CurrencyConversionPickerDataState

    data class Success(
        val items: List<CurrencyConversionPickerItemState>
    ) : CurrencyConversionPickerDataState

    data class Error(
        override val title: StringResource,
        override val subtitle: StringResource,
        override val buttonState: ButtonState
    ) : CurrencyConversionPickerDataState,
        CommonErrorScreenState
}

data class CurrencyConversionPickerItemState(
    override val key: String,
    val code: StringResource,
    val name: StringResource,
    val isSelected: Boolean,
    val onClick: () -> Unit,
) : Itemizable {
    override val contentType: Any = "currency_item"
}
