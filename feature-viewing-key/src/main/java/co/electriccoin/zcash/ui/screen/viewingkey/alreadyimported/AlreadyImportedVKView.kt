package co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.viewingkey.VKSheetContent

/**
 * Laid out like the flow's invalid-key and invalid-code sheets: the alert in a circle, a centred
 * title and message, and one button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlreadyImportedVKView(state: AlreadyImportedVKState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        shape = RoundedCornerShape(34.dp),
        content = { innerState, contentPadding ->
            VKSheetContent(
                icon = innerState.icon,
                title = innerState.title,
                message = innerState.message,
                contentPadding = contentPadding,
            ) {
                ZashiButton(
                    state = innerState.closeButton,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        },
    )
}

@PreviewScreens
@Composable
private fun AlreadyImportedPreview() =
    ZcashTheme {
        AlreadyImportedVKView(AlreadyImportedVKState.preview)
    }
