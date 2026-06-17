package co.electriccoin.zcash.ui.screen.exchangerate.picker

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.Itemizable
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName

data class CurrencyConversionPickerState(
    val items: List<CurrencyConversionPickerItemState>,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            CurrencyConversionPickerState(
                onBack = {},
                items =
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
    }
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
