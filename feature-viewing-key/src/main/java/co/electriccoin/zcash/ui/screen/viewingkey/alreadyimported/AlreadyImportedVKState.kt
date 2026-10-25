package co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.viewingkey.R

/**
 * The sheet that ends the import when the key belongs to an account the wallet already holds.
 */
@Immutable
internal data class AlreadyImportedVKState(
    val icon: ImageResource.ByDrawable,
    val title: StringResource,
    val message: StringResource,
    val closeButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview: AlreadyImportedVKState = create(onBack = {})

        fun create(onBack: () -> Unit) =
            AlreadyImportedVKState(
                icon = ImageResource.ByDrawable(R.drawable.view_only_ic_alert),
                title = stringRes(R.string.view_only_already_imported_title),
                message = stringRes(R.string.view_only_already_imported_message),
                closeButton =
                    ButtonState(
                        text = stringRes(R.string.view_only_already_imported_close),
                        style = ButtonStyle.PRIMARY,
                        onClick = onBack,
                    ),
                onBack = onBack,
            )
    }
}
