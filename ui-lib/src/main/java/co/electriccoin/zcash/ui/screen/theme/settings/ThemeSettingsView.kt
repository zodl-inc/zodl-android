package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exchangerate.settings.Option

private const val DISABLED_ALPHA = 0.4f

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
            state.options.forEachIndexed { index, option ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Option(
                    modifier = Modifier.fillMaxWidth(),
                    isChecked = option.isChecked,
                    title = stringResource(option.mode.titleRes),
                    subtitle = stringResource(option.mode.subtitleRes),
                    onClick = option.onClick
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            ZashiCheckbox(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (state.oledCheckbox.isEnabled) 1f else DISABLED_ALPHA),
                state =
                    CheckboxState(
                        title = stringRes(R.string.theme_settings_option_oled),
                        subtitle = stringRes(R.string.theme_settings_option_oled_desc),
                        isChecked = state.oledCheckbox.isChecked,
                        onClick = if (state.oledCheckbox.isEnabled) state.oledCheckbox.onClick else ({}),
                    ),
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

private val AppearanceMode.labels: Pair<Int, Int>
    get() =
        when (this) {
            AppearanceMode.SYSTEM -> R.string.theme_settings_option_system to R.string.theme_settings_option_system_desc
            AppearanceMode.LIGHT -> R.string.theme_settings_option_light to R.string.theme_settings_option_light_desc
            AppearanceMode.DARK -> R.string.theme_settings_option_classic to R.string.theme_settings_option_classic_desc
        }

private val AppearanceMode.titleRes: Int
    get() = labels.first

private val AppearanceMode.subtitleRes: Int
    get() = labels.second

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsPreview() =
    ZcashTheme {
        BlankSurface {
            ThemeSettingsView(state = previewState(selected = AppearanceMode.SYSTEM, oledChecked = false))
        }
    }

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsOledPreview() =
    ZcashTheme(forceDarkMode = true, isOledEnabled = true) {
        BlankSurface {
            ThemeSettingsView(state = previewState(selected = AppearanceMode.DARK, oledChecked = true))
        }
    }

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsLightPreview() =
    ZcashTheme {
        BlankSurface {
            ThemeSettingsView(state = previewState(selected = AppearanceMode.LIGHT, oledChecked = false, oledEnabled = false))
        }
    }

private fun previewState(
    selected: AppearanceMode,
    oledChecked: Boolean,
    oledEnabled: Boolean = selected != AppearanceMode.LIGHT,
) = ThemeSettingsState(
    options =
        AppearanceMode.entries.map { mode ->
            AppearanceModeOptionState(
                mode = mode,
                isChecked = mode == selected,
                onClick = {}
            )
        },
    oledCheckbox =
        OledCheckboxState(
            isChecked = oledChecked,
            isEnabled = oledEnabled,
            onClick = {}
        ),
    saveButton =
        ButtonState(
            text = stringRes(R.string.currencyConversion_saveBtn),
            onClick = {}
        ),
    onBack = {}
)
