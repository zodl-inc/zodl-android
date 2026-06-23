package co.electriccoin.zcash.ui.screen.tor.optin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiInfoRow
import co.electriccoin.zcash.ui.design.component.ZashiTextButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@Composable
fun TorOptInView(state: TorOptInState) {
    ZashiBaseSettingsOptIn(
        header = stringResource(id = R.string.torSetup_title),
        image = R.drawable.ic_tor_settings,
        onDismiss = state.onBack,
        content = {
            Spacer(20.dp)
            Text(
                text = stringResource(R.string.tor_settings_subtitle),
                color = ZashiColors.Text.textTertiary,
                fontSize = 14.sp,
            )
            Spacer(24.dp)
            ZashiInfoRow(
                icon = R.drawable.ic_tor_opt_in_item_1,
                title = stringResource(R.string.torSetup_option1_title),
                subtitle = stringResource(R.string.torSetup_option1_desc),
            )
            Spacer(20.dp)
            ZashiInfoRow(
                icon = R.drawable.ic_tor_opt_in_item_2,
                title = stringResource(R.string.torSetup_option2_title),
                subtitle = stringResource(R.string.torSetup_option2_desc),
            )
            Spacer(20.dp)
            ZashiInfoRow(
                icon = R.drawable.ic_tor_opt_in_item_3,
                title = stringResource(R.string.torSetup_option3_title),
                subtitle = stringResource(R.string.torSetup_option3_desc),
            )
        },
        info = null,
        footer = {
            ZashiTextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = state.onSkipClick,
            ) {
                Text(
                    text = stringResource(R.string.torSetup_learn_btnOut),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold
                )
            }
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.torSetup_learn_btnIn),
                onClick = state.onEnableClick,
                colors = ZashiButtonDefaults.primaryColors()
            )
        }
    )
}

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun CurrencyConversionOptInPreview() =
    ZcashTheme {
        BlankSurface {
            TorOptInView(
                state =
                    TorOptInState(
                        onEnableClick = {},
                        onBack = {},
                        onSkipClick = {},
                    )
            )
        }
    }
