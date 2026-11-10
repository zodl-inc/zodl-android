package co.electriccoin.zcash.ui.screen.zns.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.exchangerate.settings.Option

@Composable
fun ZnsSettingsView(state: ZnsSettingsState) {
    var isEnableSelected by remember(state.isEnabled) { mutableStateOf(state.isEnabled) }

    ZashiBaseSettingsOptIn(
        header = stringResource(R.string.zns_settings_title),
        image = R.drawable.ic_settings_address_book,
        onDismiss = state.onDismiss,
        info = null,
        content = {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.zns_settings_subtitle),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Option(
                modifier = Modifier.fillMaxWidth().testTag(ZnsSettingsTag.ENABLE_OPTION),
                isChecked = isEnableSelected,
                title = stringResource(R.string.zns_settings_enable),
                subtitle = stringResource(R.string.zns_settings_enableDesc),
                onClick = { isEnableSelected = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Option(
                modifier = Modifier.fillMaxWidth().testTag(ZnsSettingsTag.DISABLE_OPTION),
                isChecked = !isEnableSelected,
                title = stringResource(R.string.zns_settings_disable),
                subtitle = stringResource(R.string.zns_settings_disableDesc),
                onClick = { isEnableSelected = false }
            )
        },
        footer = {
            ZashiButton(
                text = stringResource(R.string.zns_settings_save),
                modifier = Modifier.fillMaxWidth().testTag(ZnsSettingsTag.SAVE_BUTTON),
                onClick = { state.onSaveClick(isEnableSelected) },
                enabled = isEnableSelected != state.isEnabled,
                colors = ZashiButtonDefaults.primaryColors(),
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        },
    )
}

object ZnsSettingsTag {
    const val ENABLE_OPTION = "zns_settings_enable"
    const val DISABLE_OPTION = "zns_settings_disable"
    const val SAVE_BUTTON = "zns_settings_save"
}

@PreviewScreens
@Composable
private fun ZnsSettingsPreview() =
    ZcashTheme {
        BlankSurface {
            ZnsSettingsView(
                state =
                    ZnsSettingsState(
                        isEnabled = false,
                        onSaveClick = {},
                        onDismiss = {}
                    )
            )
        }
    }
