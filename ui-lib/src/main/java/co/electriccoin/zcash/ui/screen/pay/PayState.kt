package co.electriccoin.zcash.ui.screen.pay

import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.SendMaxChipState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.screen.swap.SwapErrorFooterState

internal data class PayState(
    val info: IconButtonState,
    val address: TextFieldState,
    val addressPlaceholder: StringResource,
    val asset: AssetCardState,
    val abContact: ChipButtonState? = null,
    val abButton: IconButtonState,
    val qrButton: IconButtonState,
    val amount: NumberTextFieldState,
    val amountFiat: NumberTextFieldState,
    val amountError: StringResource? = null,
    val max: SendMaxChipState? = null,
    /** The one line above the main button: the fee hint after a Max fill, or the Max failure (which wins). */
    val maxFeedback: MaxFeedback? = null,
    /** True when no USD price is known: the USD field shows a dash instead of its currency placeholder. */
    val isFiatPriceUnavailable: Boolean = false,
    val zecAmount: StyledStringResource,
    val slippage: ButtonState,
    val infoFooter: StringResource?,
    val errorFooter: SwapErrorFooterState?,
    val primaryButton: ButtonState?,
    val isABHintVisible: Boolean,
    val onBack: () -> Unit,
)
