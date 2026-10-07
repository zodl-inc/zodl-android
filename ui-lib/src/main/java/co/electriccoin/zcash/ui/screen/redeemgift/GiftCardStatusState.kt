package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

/**
 * A gift card status screen: the content centered below the status bar, tinted by [background], with the buttons
 * straight on the screen below it. There is no app bar; back acts as [onBack].
 *
 * @param background the tint of the gradient behind the content; `null` keeps the plain background.
 * @param secondaryButton shown above [primaryButton].
 */
@Immutable
data class GiftCardStatusState(
    val background: Background?,
    val image: ImageResource,
    val title: StringResource,
    val subtitle: StyledStringResource,
    val primaryButton: ButtonState?,
    val secondaryButton: ButtonState?,
    val onBack: () -> Unit,
) {
    enum class Background { PENDING, EMPTY, SUCCESS }
}
