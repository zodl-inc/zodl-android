package co.electriccoin.zcash.ui.screen.swap.quote

import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

internal sealed interface SwapQuoteState : ModalBottomSheetState {
    data class Success(
        val title: StringResource,
        val providerIcon: ImageResource,
        val rotateIcon: Boolean,
        val from: SwapTokenAmountState,
        val to: SwapTokenAmountState,
        val items: List<SwapQuoteInfoItem>,
        val amount: SwapQuoteInfoItem,
        val primaryButton: ButtonState,
        val infoText: StringResource?,
        // Per-provider rows for the Comparison tab; null (or < 2 entries) hides the Breakdown|Comparison tabs.
        val comparison: List<SwapProviderQuoteState>? = null,
        override val onBack: () -> Unit,
    ) : SwapQuoteState

    data class Error(
        val icon: ImageResource,
        val title: StringResource,
        val subtitle: StringResource,
        val negativeButton: ButtonState,
        val positiveButton: ButtonState,
        override val onBack: () -> Unit,
    ) : SwapQuoteState
}

data class SwapQuoteInfoItem(
    val description: StringResource,
    val title: StyledStringResource,
    val subtitle: StyledStringResource? = null,
)

/** A single provider's quote shown as a selectable row in the Comparison tab (MOB-1396). */
data class SwapProviderQuoteState(
    val provider: SwapProvider,
    val icon: ImageResource,
    val name: StringResource,
    val amount: StringResource,
    val fiatAmount: StringResource,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)
