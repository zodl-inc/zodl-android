package co.electriccoin.zcash.ui.screen.theme.darklook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exchangerate.settings.Option

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeDarkLookView(state: ThemeDarkLookState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { innerState, contentPadding ->
            BottomSheetContent(
                state = innerState,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f, false)
            )
        },
    )
}

@Composable
private fun BottomSheetContent(
    state: ThemeDarkLookState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                )
    ) {
        Text(
            text = stringResource(R.string.theme_settings_darklook_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(8.dp)
        Text(
            text = stringResource(R.string.theme_settings_darklook_subtitle),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm
        )
        Spacer(24.dp)
        state.options.forEachIndexed { index, option ->
            if (index > 0) {
                Spacer(12.dp)
            }
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = option.isChecked,
                title = stringResource(option.titleRes),
                subtitle = stringResource(option.subtitleRes),
                onClick = option.onClick
            )
        }
        Spacer(24.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.saveButton,
            defaultPrimaryColors = ZashiButtonDefaults.primaryColors(),
        )
    }
}

private val DarkLookOptionState.titleRes: Int
    get() = if (isOledEnabled) R.string.theme_settings_option_oled else R.string.theme_settings_option_classic

private val DarkLookOptionState.subtitleRes: Int
    get() = if (isOledEnabled) R.string.theme_settings_option_oled_desc else R.string.theme_settings_option_classic_desc

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ThemeDarkLookPreview() =
    ZcashTheme {
        ThemeDarkLookView(state = previewState())
    }

private fun previewState() =
    ThemeDarkLookState(
        options =
            listOf(false, true).map { isOled ->
                DarkLookOptionState(
                    isOledEnabled = isOled,
                    isChecked = !isOled,
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
