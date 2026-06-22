package co.electriccoin.zcash.ui.screen.tor.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
fun TorSettingsView(state: TorSettingsState) {
    var isOptInSelected by remember(state.isOptedIn) { mutableStateOf(state.isOptedIn) }

    val isButtonDisabled by remember {
        derivedStateOf {
            (state.isOptedIn && isOptInSelected) || (!state.isOptedIn && !isOptInSelected)
        }
    }

    ZashiBaseSettingsOptIn(
        header = stringResource(R.string.tor_settings_title),
        image = R.drawable.ic_tor_settings,
        onDismiss = state.onDismiss,
        info = null,
        content = {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tor_settings_subtitle),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = isOptInSelected,
                title = stringResource(R.string.exchange_rate_opt_in_option_title),
                subtitle = stringResource(R.string.torSetup_enableDesc),
                onClick = { isOptInSelected = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Option(
                modifier = Modifier.fillMaxWidth(),
                isChecked = !isOptInSelected,
                title = stringResource(R.string.exchange_rate_opt_out_option_title),
                subtitle = stringResource(R.string.torSetup_disableDesc),
                onClick = { isOptInSelected = false }
            )
        },
        footer = {
            ZashiButton(
                text = stringResource(R.string.exchange_rate_opt_in_save),
                modifier = Modifier.fillMaxWidth(),
                onClick = { state.onSaveClick(isOptInSelected) },
                enabled = !isButtonDisabled,
                colors = ZashiButtonDefaults.primaryColors(),
                hapticFeedbackType = HapticFeedbackType.Confirm
            )
        },
    )
}

@PreviewScreens
@Composable
private fun SettingsExchangeRateOptInPreview() =
    ZcashTheme {
        BlankSurface {
            TorSettingsView(
                state =
                    TorSettingsState(
                        isOptedIn = true,
                        onSaveClick = {},
                        onDismiss = {}
                    )
            )
        }
    }
