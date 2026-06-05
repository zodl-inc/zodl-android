@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package co.electriccoin.zcash.ui.design.component

import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue

@Composable
fun ZashiScreenDialog(
    state: DialogState?,
    properties: DialogProperties = DialogProperties()
) {
    val parent = LocalView.current.parent
    SideEffect {
        (parent as? DialogWindowProvider)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        (parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }

    state?.let {
        Dialog(
            positive = state.positive,
            negative = state.negative,
            onDismissRequest = state.onDismissRequest,
            title = state.title,
            message = state.message,
            properties = properties,
        )
    }
}

@Composable
private fun Dialog(
    positive: ButtonState,
    negative: ButtonState,
    title: StringResource,
    message: StringResource,
    onDismissRequest: (() -> Unit),
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            // AlertDialog renders in a separate Compose Popup window.
            // The activity-level testTagsAsResourceId doesn't reach here,
            // so we re-enable it inline so the testTag surfaces to
            // Maestro / uiautomator as a resource-id.
            ZashiButton(
                state = positive,
                modifier =
                    Modifier.semantics {
                        testTagsAsResourceId = true
                        testTag = ZashiScreenDialogTag.CONFIRM
                    }
            )
        },
        dismissButton = {
            ZashiButton(
                state = negative,
                modifier =
                    Modifier.semantics {
                        testTagsAsResourceId = true
                        testTag = ZashiScreenDialogTag.DISMISS
                    },
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors()
            )
        },
        title = {
            Text(
                text = title.getValue(),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textXl,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message.getValue(),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textMd
            )
        },
        properties = properties,
        containerColor = ZashiColors.Surfaces.bgPrimary,
        titleContentColor = ZashiColors.Text.textPrimary,
        textContentColor = ZashiColors.Text.textPrimary,
        modifier = modifier,
    )
}

data class DialogState(
    val positive: ButtonState,
    val negative: ButtonState,
    val onDismissRequest: (() -> Unit),
    val title: StringResource,
    val message: StringResource,
)

object ZashiScreenDialogTag {
    const val CONFIRM = "DIALOG_CONFIRM"
    const val DISMISS = "DIALOG_DISMISS"
}
