package co.electriccoin.zcash.ui.screen.viewingkey.remove

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.viewingkey.VKSheetContent

/**
 * Laid out like the flow's other sheets: the trash icon in a circle, a centred title and message,
 * then Remove Account over Cancel. While the removal runs the sheet cannot be dragged or tapped
 * away; back is ignored by the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoveVKAccountView(state: RemoveVKAccountState?) {
    val isDismissable by rememberUpdatedState(state?.isDismissable != false)
    ZashiScreenModalBottomSheet(
        state = state,
        sheetGesturesEnabled = isDismissable,
        sheetState =
            rememberScreenModalBottomSheetState(
                confirmValueChange = { it != SheetValue.Hidden || isDismissable }
            ),
        shape = RoundedCornerShape(34.dp),
        content = { innerState, contentPadding ->
            VKSheetContent(
                icon = innerState.icon,
                title = innerState.title,
                message = innerState.message,
                contentPadding = contentPadding,
            ) {
                ZashiButton(
                    state = innerState.removeButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(RemoveVKAccountTag.REMOVE_BUTTON),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
                ZashiButton(
                    state = innerState.cancelButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(RemoveVKAccountTag.CANCEL_BUTTON),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        },
    )
}

@PreviewScreens
@Composable
private fun RemoveAccountPreview() =
    ZcashTheme {
        RemoveVKAccountView(RemoveVKAccountState.preview)
    }

@PreviewScreens
@Composable
private fun RemovingAccountPreview() =
    ZcashTheme {
        RemoveVKAccountView(RemoveVKAccountState.previewRemoving)
    }
