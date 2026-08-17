package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exchangerate.settings.Option
import co.electriccoin.zcash.ui.screen.exchangerate.settings.SimpleCheckboxState

@Composable
internal fun ThemeSettingsView(state: ThemeSettingsState) {
    ZashiBaseSettingsOptIn(
        header = stringResource(id = R.string.theme_settings_title),
        image = R.drawable.ic_theme_settings,
        onDismiss = state.onBack,
        info = null,
        content = {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.theme_settings_subtitle),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = state.isClassicThemeSelected.isChecked,
                title = stringResource(R.string.theme_settings_option_classic),
                subtitle = stringResource(R.string.theme_settings_option_classic_desc),
                onClick = state.isClassicThemeSelected.onClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = state.isOledThemeSelected.isChecked,
                title = stringResource(R.string.theme_settings_option_oled),
                subtitle = stringResource(R.string.theme_settings_option_oled_desc),
                onClick = state.isOledThemeSelected.onClick
            )
        },
        footer = {
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = state.saveButton,
                defaultPrimaryColors = ZashiButtonDefaults.primaryColors(),
            )
        },
    )
}

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsPreview() =
    ZcashTheme {
        BlankSurface {
            ThemeSettingsView(state = previewState())
        }
    }

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsOledPreview() =
    ZcashTheme(forceDarkMode = true, isOledDark = true) {
        BlankSurface {
            ThemeSettingsView(state = previewState())
        }
    }

private fun previewState() =
    ThemeSettingsState(
        isClassicThemeSelected =
            SimpleCheckboxState(
                isChecked = false,
                onClick = {}
            ),
        isOledThemeSelected =
            SimpleCheckboxState(
                isChecked = true,
                onClick = {}
            ),
        saveButton =
            ButtonState(
                text = stringRes(R.string.currencyConversion_saveBtn),
                onClick = {}
            ),
        onBack = {}
    )
