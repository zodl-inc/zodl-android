package co.electriccoin.zcash.ui.screen.exchangerateunavailable

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class ExchangeRateUnavailableState(
    val title: StringResource,
    val subtitle: StringResource,
    val switchToUsdButton: ButtonState,
    val continueInZecButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            ExchangeRateUnavailableState(
                title = stringRes("JPY conversion unavailable"),
                subtitle = stringRes("Live rates for Japanese yen can't be fetched right now."),
                switchToUsdButton = ButtonState(stringRes("Switch to USD")),
                continueInZecButton = ButtonState(stringRes("Continue in ZEC")),
                onBack = {},
            )
    }
}
