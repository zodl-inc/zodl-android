package co.electriccoin.zcash.ui.screen.viewingkey.importvk

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.compose.AccountFormHeaderState
import co.electriccoin.zcash.ui.common.compose.AccountFormState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.viewingkey.vkAvatar
import co.electriccoin.zcash.viewingkey.R

/**
 * The screen that takes the full viewing key. [toString] leaves out the text of [field], which is
 * the key.
 *
 * @param scanButton opens the QR scanner; it sits at the end of [field].
 */
@Immutable
internal data class ImportVKState(
    val form: AccountFormState,
    val fieldLabel: StringResource,
    val field: TextFieldState,
    val placeholder: StringResource,
    val scanButton: IconButtonState,
    val disclaimerIcon: ImageResource.ByDrawable,
    val disclaimer: StringResource,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
) {
    override fun toString(): String =
        "ImportVKState(field=<redacted>, canContinue=${continueButton.isEnabled}, " +
            "isLoading=${continueButton.isLoading})"

    companion object {
        val preview: ImportVKState = preview(text = "", isSubmitting = false)

        val previewFilled: ImportVKState = preview(text = "uview1preview", isSubmitting = false)

        /**
         * The screen for [text]. Continue is enabled while there is text and nothing is being
         * submitted.
         */
        fun create(
            text: String,
            isSubmitting: Boolean,
            onValueChange: (String) -> Unit,
            onScanClick: () -> Unit,
            onContinueClick: () -> Unit,
            onBack: () -> Unit,
        ) = ImportVKState(
            form =
                AccountFormState(
                    title = stringRes(R.string.view_only_import_title),
                    subtitle = stringRes(R.string.view_only_import_subtitle),
                    header =
                        AccountFormHeaderState(
                            avatar = vkAvatar(),
                            heading = stringRes(R.string.view_only_paste_heading),
                            message = stringRes(R.string.view_only_paste_message),
                        ),
                ),
            fieldLabel = stringRes(R.string.view_only_paste_label),
            field =
                TextFieldState(
                    value = stringRes(text),
                    isEnabled = !isSubmitting,
                    onValueChange = onValueChange,
                ),
            placeholder = stringRes(R.string.view_only_paste_placeholder),
            scanButton =
                IconButtonState(
                    icon = R.drawable.view_only_ic_qr,
                    contentDescription = stringRes(R.string.view_only_paste_scan),
                    isEnabled = !isSubmitting,
                    onClick = onScanClick,
                ),
            disclaimerIcon = ImageResource.ByDrawable(R.drawable.view_only_ic_info),
            disclaimer = stringRes(R.string.view_only_paste_disclaimer),
            continueButton =
                ButtonState(
                    text = stringRes(R.string.view_only_continue),
                    style = ButtonStyle.PRIMARY,
                    isEnabled = text.isNotBlank() && !isSubmitting,
                    isLoading = isSubmitting,
                    onClick = onContinueClick,
                ),
            onBack = onBack,
        )

        private fun preview(
            text: String,
            isSubmitting: Boolean,
        ) = create(
            text = text,
            isSubmitting = isSubmitting,
            onValueChange = {},
            onScanClick = {},
            onContinueClick = {},
            onBack = {},
        )
    }
}

internal object ImportVKTag {
    const val FIELD = "view_only_paste_field"
    const val SCAN_BUTTON = "view_only_paste_scan_button"
    const val CONTINUE_BUTTON = "view_only_paste_continue_button"
}
