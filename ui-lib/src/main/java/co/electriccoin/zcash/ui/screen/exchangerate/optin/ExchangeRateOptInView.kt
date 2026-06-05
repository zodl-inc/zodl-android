package co.electriccoin.zcash.ui.screen.exchangerate.optin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiInfoRow
import co.electriccoin.zcash.ui.design.component.ZashiTextButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exchangerate.settings.CurrencyField
import co.electriccoin.zcash.ui.screen.exchangerate.settings.CurrencyFieldState

@Composable
fun ExchangeRateOptInView(state: ExchangeRateOptInState) {
    ZashiBaseSettingsOptIn(
        header = stringResource(id = R.string.exchange_rate_opt_in_subtitle),
        image = R.drawable.exchange_rate,
        onDismiss = state.onBack,
        content = {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.exchange_rate_opt_in_description),
                color = ZashiColors.Text.textTertiary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            ZashiInfoRow(
                icon = R.drawable.ic_exchange_rate_info_1,
                title = stringResource(R.string.exchange_rate_info_title_1),
                subtitle =
                    if (VersionInfo.IS_CMC_AVAILABLE) {
                        stringResource(R.string.exchange_rate_info_subtitle_1)
                    } else {
                        stringResource(R.string.exchange_rate_info_subtitle_1_no_cmc)
                    },
            )
            Spacer(modifier = Modifier.height(16.dp))
            ZashiInfoRow(
                icon = R.drawable.ic_exchange_rate_info_2,
                title = stringResource(R.string.exchange_rate_info_title_2),
                subtitle = stringResource(R.string.exchange_rate_info_subtitle_2),
            )
            state.currencyField?.let { field ->
                Spacer(modifier = Modifier.height(24.dp))
                CurrencyField(
                    modifier = Modifier.fillMaxWidth(),
                    state = field
                )
            }
        },
        info = null,
        footer = {
            ZashiTextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = state.onSkipClick,
            ) {
                Text(
                    text = stringResource(R.string.exchange_rate_opt_in_skip),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold
                )
            }
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.exchange_rate_opt_in_enable),
                onClick = state.onEnableClick,
                colors = ZashiButtonDefaults.primaryColors(),
                hapticFeedbackType = HapticFeedbackType.Confirm
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
            ExchangeRateOptInView(
                state =
                    ExchangeRateOptInState(
                        currencyField =
                            CurrencyFieldState(
                                code = stringRes("USD"),
                                name = stringRes("US Dollar"),
                                onClick = {}
                            ),
                        onEnableClick = {},
                        onBack = {},
                        onSkipClick = {},
                    )
            )
        }
    }
