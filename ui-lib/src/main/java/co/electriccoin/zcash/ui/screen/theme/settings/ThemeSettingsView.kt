package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.annotation.StringRes
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
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exchangerate.settings.Option

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
            AppearanceMode.OLED -> R.string.theme_settings_option_oled to R.string.theme_settings_option_oled_desc
        }

@get:StringRes
private val AppearanceMode.titleRes: Int
    get() = labels.first

@get:StringRes
private val AppearanceMode.subtitleRes: Int
    get() = labels.second

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsPreview() =
    ZcashTheme {
        BlankSurface {
            ThemeSettingsView(state = previewState(AppearanceMode.SYSTEM))
        }
    }

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeSettingsOledPreview() =
    ZcashTheme(forceDarkMode = true, appearanceMode = AppearanceMode.OLED) {
        BlankSurface {
            ThemeSettingsView(state = previewState(AppearanceMode.OLED))
        }
    }

private fun previewState(selected: AppearanceMode) =
    ThemeSettingsState(
        options =
            AppearanceMode.entries.map { mode ->
                AppearanceModeOptionState(
                    mode = mode,
                    isChecked = mode == selected,
                    onClick = {}
                )
            },
        saveButton =
            ButtonState(
                text = stringRes(R.string.currencyConversion_saveBtn),
                onClick = {}
            ),
        onBack = {}
    )
