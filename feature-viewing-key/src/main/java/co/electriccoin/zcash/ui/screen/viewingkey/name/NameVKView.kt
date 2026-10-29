package co.electriccoin.zcash.ui.screen.viewingkey.name

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.compose.AccountFormView
import co.electriccoin.zcash.ui.common.compose.AccountNameField
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Composable
internal fun NameVKView(state: NameVKState) {
    AccountFormView(state = state.form, onBack = state.onBack) {
        AccountNameField(state = state.nameField)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        ZashiButton(
            state = state.continueButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(NameVKTag.CONTINUE_BUTTON)
        )
    }
}

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        NameVKView(NameVKState.preview)
    }

@PreviewScreens
@Composable
private fun FilledPreview() =
    ZcashTheme {
        NameVKView(NameVKState.previewFilled)
    }

@PreviewScreens
@Composable
private fun LimitPreview() =
    ZcashTheme {
        NameVKView(NameVKState.previewLimit)
    }

@PreviewScreens
@Composable
private fun ColoredPreview() =
    ZcashTheme {
        NameVKView(NameVKState.previewColored)
    }

@PreviewScreens
@Composable
private fun DuplicatePreview() =
    ZcashTheme {
        NameVKView(NameVKState.previewDuplicate)
    }

@PreviewScreens
@Composable
private fun ImportingPreview() =
    ZcashTheme {
        NameVKView(NameVKState.previewImporting)
    }
