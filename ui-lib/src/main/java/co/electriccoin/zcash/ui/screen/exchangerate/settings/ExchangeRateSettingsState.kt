package co.electriccoin.zcash.ui.screen.exchangerate.settings

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

internal data class ExchangeRateSettingsState(
    val isOptedIn: SimpleCheckboxState,
    val isOptedOut: SimpleCheckboxState,
    val currencyField: CurrencyFieldState?,
    val saveButton: ButtonState,
    val info: StringResource?,
    val onBack: () -> Unit,
)

data class SimpleCheckboxState(
    val isChecked: Boolean,
    val onClick: () -> Unit,
)

/**
 * The "Select currency" field shown on the settings screen when the feature is enabled.
 * [code] is the ISO 4217 code (e.g. "USD") and [name] is the localized currency name.
 */
data class CurrencyFieldState(
    val code: StringResource,
    val name: StringResource,
    val onClick: () -> Unit,
)
