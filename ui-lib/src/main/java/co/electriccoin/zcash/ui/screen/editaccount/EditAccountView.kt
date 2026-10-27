package co.electriccoin.zcash.ui.screen.editaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.compose.AccountFormView
import co.electriccoin.zcash.ui.common.compose.AccountNameField
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.getValue

/**
 * The account form with an edit top bar: the name field, then for a view-only account the removal
 * entry (the confirmation hint and the destructive Remove Account button), then Save changes.
 */
@Composable
internal fun EditAccountView(state: EditAccountState) {
    AccountFormView(state = state.form, onBack = state.onBack) {
        AccountNameField(state = state.nameField)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        state.remove?.let { remove ->
            RemoveInfo(remove)
            Spacer(Modifier.height(16.dp))
            ZashiButton(
                state = remove.button,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(EditAccountTag.REMOVE_BUTTON)
            )
            Spacer(Modifier.height(8.dp))
        }
        ZashiButton(
            state = state.saveButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(EditAccountTag.SAVE_BUTTON)
        )
    }
}

@Composable
private fun RemoveInfo(state: EditAccountRemoveState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.icon.Compose(modifier = Modifier.size(16.dp))
        Text(
            text = state.info.getValue(),
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary,
        )
    }
}

@PreviewScreens
@Composable
private fun DefaultPreview() =
    ZcashTheme {
        EditAccountView(EditAccountState.preview)
    }

@PreviewScreens
@Composable
private fun LimitPreview() =
    ZcashTheme {
        EditAccountView(EditAccountState.previewLimit)
    }

@PreviewScreens
@Composable
private fun ColoredPreview() =
    ZcashTheme {
        EditAccountView(EditAccountState.previewColored)
    }

@PreviewScreens
@Composable
private fun DuplicatePreview() =
    ZcashTheme {
        EditAccountView(EditAccountState.previewDuplicate)
    }

@PreviewScreens
@Composable
private fun ZodlPreview() =
    ZcashTheme {
        EditAccountView(EditAccountState.previewZodl)
    }

@PreviewScreens
@Composable
private fun ZodlColoredPreview() =
    ZcashTheme {
        EditAccountView(EditAccountState.previewZodlColored)
    }
