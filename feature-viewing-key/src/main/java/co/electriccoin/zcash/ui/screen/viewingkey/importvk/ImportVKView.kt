package co.electriccoin.zcash.ui.screen.viewingkey.importvk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.compose.AccountFormView
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiImageButton
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldPlaceholder
import co.electriccoin.zcash.ui.design.component.ZodlViewingKeyTextField
import co.electriccoin.zcash.ui.design.component.ZodlViewingKeyTextFieldDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.getValue

@Composable
internal fun ImportVKView(state: ImportVKState) {
    AccountFormView(state = state.form, onBack = state.onBack) {
        KeyField(state)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        Disclaimer(state)
        Spacer(Modifier.height(24.dp))
        ZashiButton(
            state = state.continueButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ImportVKTag.CONTINUE_BUTTON)
        )
    }
}

@Composable
private fun KeyField(state: ImportVKState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.fieldLabel.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Inputs.Default.label
        )
        Spacer(Modifier.height(6.dp))
        ZodlViewingKeyTextField(
            state = state.field,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ImportVKTag.FIELD),
            placeholder = { ZashiTextFieldPlaceholder(state.placeholder) },
            keyboardOptions = ZodlViewingKeyTextFieldDefaults.keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = { if (state.continueButton.isEnabled) state.continueButton.onClick() }
                ),
            suffix = {
                ZashiImageButton(
                    state = state.scanButton,
                    modifier =
                        Modifier
                            .size(SCAN_BUTTON_SIZE)
                            .testTag(ImportVKTag.SCAN_BUTTON),
                )
            },
        )
    }
}

@Composable
private fun Disclaimer(state: ImportVKState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.disclaimerIcon.Compose(modifier = Modifier.size(16.dp))
        Text(
            text = state.disclaimer.getValue(),
            modifier = Modifier.weight(1f),
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary,
            overflow = TextOverflow.Clip,
        )
    }
}

private val SCAN_BUTTON_SIZE = 36.dp

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        ImportVKView(ImportVKState.preview)
    }

@PreviewScreens
@Composable
private fun FilledPreview() =
    ZcashTheme {
        ImportVKView(ImportVKState.previewFilled)
    }

@PreviewScreens
@Composable
private fun InvalidKeyPreview() =
    ZcashTheme {
        ImportVKView(ImportVKState.previewFilled)
        ZashiConfirmationBottomSheet(InvalidVKSheet.preview)
    }
