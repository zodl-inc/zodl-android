package co.electriccoin.zcash.ui.screen.exchangerate.optin

import co.electriccoin.zcash.ui.screen.exchangerate.settings.CurrencyFieldState

data class ExchangeRateOptInState(
    val currencyField: CurrencyFieldState?,
    val onEnableClick: () -> Unit,
    val onBack: () -> Unit,
    val onSkipClick: () -> Unit,
)
