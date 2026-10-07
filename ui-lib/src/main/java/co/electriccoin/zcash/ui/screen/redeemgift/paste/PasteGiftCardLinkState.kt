package co.electriccoin.zcash.ui.screen.redeemgift.paste

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * [toString] leaves out the text of [field], which can be a gift card link and so carries a spending secret.
 *
 * @param field the link as entered. While the text cannot be a gift card link its error is set, but to an empty text:
 * it only paints the field's error border, and the message shown is [invalidHint].
 * @param fieldButton pastes from the clipboard while [field] is empty and clears it otherwise.
 * @param invalidHint shown under [field] while its text is not a gift card link.
 */
@Immutable
data class PasteGiftCardLinkState(
    val title: StringResource,
    val image: ImageResource.ByDrawable,
    val heading: StringResource,
    val subtitle: StringResource,
    val fieldLabel: StringResource,
    val field: TextFieldState,
    val placeholder: StringResource,
    val fieldButton: ButtonState,
    val invalidHint: StringResource?,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
) {
    override fun toString(): String =
        "PasteGiftCardLinkState(field=<redacted>, isInvalid=${invalidHint != null}, " +
            "canContinue=${continueButton.isEnabled})"

    companion object {
        val preview: PasteGiftCardLinkState = preview(text = "", isValid = false, isInvalid = false)

        val previewPasted: PasteGiftCardLinkState =
            preview(
                text = "https://gift.zodl.com/#v=1&key=zgift1preview&height=3100000",
                isValid = true,
                isInvalid = false
            )

        val previewInvalid: PasteGiftCardLinkState =
            preview(text = "zodl.com/gift/8f2k", isValid = false, isInvalid = true)

        /**
         * The screen for [text]. Continue is enabled while [isValid]; [isInvalid] marks the field and shows the hint.
         */
        fun create(
            text: String,
            isValid: Boolean,
            isInvalid: Boolean,
            onValueChange: (String) -> Unit,
            onPasteClick: () -> Unit,
            onClearClick: () -> Unit,
            onContinueClick: () -> Unit,
            onBack: () -> Unit,
        ) = PasteGiftCardLinkState(
            title = stringRes(R.string.redeemGift_title),
            image = ImageResource.ByDrawable(R.drawable.ic_gift_closed),
            heading = stringRes(R.string.redeemGift_paste_title),
            subtitle = stringRes(R.string.redeemGift_paste_subtitle),
            fieldLabel = stringRes(R.string.redeemGift_paste_label),
            field =
                TextFieldState(
                    value = stringRes(text),
                    error = stringRes("").takeIf { isInvalid },
                    onValueChange = onValueChange
                ),
            placeholder = stringRes(R.string.redeemGift_paste_placeholder),
            fieldButton =
                if (text.isEmpty()) {
                    ButtonState(
                        text = stringRes(R.string.redeemGift_paste_paste),
                        style = ButtonStyle.SECONDARY,
                        icon = R.drawable.ic_copy,
                        onClick = onPasteClick
                    )
                } else {
                    ButtonState(
                        text = stringRes(R.string.redeemGift_paste_clear),
                        style = ButtonStyle.SECONDARY,
                        onClick = onClearClick
                    )
                },
            invalidHint = stringRes(R.string.redeemGift_paste_invalid).takeIf { isInvalid },
            continueButton =
                ButtonState(
                    text = stringRes(R.string.redeemGift_paste_continue),
                    style = ButtonStyle.PRIMARY,
                    isEnabled = isValid,
                    onClick = onContinueClick
                ),
            onBack = onBack
        )

        private fun preview(
            text: String,
            isValid: Boolean,
            isInvalid: Boolean
        ) = create(
            text = text,
            isValid = isValid,
            isInvalid = isInvalid,
            onValueChange = {},
            onPasteClick = {},
            onClearClick = {},
            onContinueClick = {},
            onBack = {}
        )
    }
}
