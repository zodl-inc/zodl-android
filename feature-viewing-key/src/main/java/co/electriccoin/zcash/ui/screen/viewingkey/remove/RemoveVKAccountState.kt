package co.electriccoin.zcash.ui.screen.viewingkey.remove

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.viewingkey.R

/**
 * The sheet that confirms removing a view-only account and says what removing keeps.
 *
 * @param isDismissable false while the account is being removed: the sheet then stays up until the
 *        removal ends, whatever the user drags, taps or presses.
 */
@Immutable
internal data class RemoveVKAccountState(
    val icon: ImageResource.ByDrawable,
    val title: StringResource,
    val message: StringResource,
    val removeButton: ButtonState,
    val cancelButton: ButtonState,
    val isDismissable: Boolean,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview: RemoveVKAccountState = preview(isRemoving = false)

        val previewRemoving: RemoveVKAccountState = preview(isRemoving = true)

        /**
         * While [isRemoving], Remove Account shows its progress and neither button reacts.
         */
        fun create(
            isRemoving: Boolean,
            onRemoveClick: () -> Unit,
            onCancelClick: () -> Unit,
            onBack: () -> Unit,
        ) = RemoveVKAccountState(
            icon = ImageResource.ByDrawable(R.drawable.view_only_ic_remove),
            title = stringRes(R.string.view_only_remove_title),
            message = stringRes(R.string.view_only_remove_message),
            removeButton =
                ButtonState(
                    text = stringRes(R.string.view_only_remove_account),
                    style = ButtonStyle.DESTRUCTIVE1,
                    isEnabled = !isRemoving,
                    isLoading = isRemoving,
                    onClick = onRemoveClick,
                ),
            cancelButton =
                ButtonState(
                    text = stringRes(R.string.view_only_remove_cancel),
                    style = ButtonStyle.PRIMARY,
                    isEnabled = !isRemoving,
                    onClick = onCancelClick,
                ),
            isDismissable = !isRemoving,
            onBack = onBack,
        )

        private fun preview(isRemoving: Boolean) =
            create(
                isRemoving = isRemoving,
                onRemoveClick = {},
                onCancelClick = {},
                onBack = {},
            )
    }
}

internal object RemoveVKAccountTag {
    const val REMOVE_BUTTON = "view_only_remove_account_remove_button"
    const val CANCEL_BUTTON = "view_only_remove_account_cancel_button"
}
