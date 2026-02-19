package co.electriccoin.zcash.ui.screen.home.reporting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.R
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

@Composable
fun CrashReportOptInView(state: CrashReportOptInState) {
    ZashiBaseSettingsOptIn(
        header = stringResource(R.string.crash_report_detail_title),
        image = R.drawable.crash_report_detail,
        imageSize = DpSize(Dp.Unspecified, 48.dp),
        onDismiss = state.onBack,
        content = {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.crash_report_detail_subtitle),
                color = ZashiColors.Text.textTertiary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            ZashiInfoRow(
                icon = R.drawable.ic_crash_report_1,
                title = stringResource(R.string.crash_report_detail_item_title_1),
                subtitle = stringResource(R.string.crash_report_detail_item_subtitle_1),
            )
            Spacer(modifier = Modifier.height(16.dp))
            ZashiInfoRow(
                icon = R.drawable.ic_crash_report_2,
                title = stringResource(R.string.crash_report_detail_item_title_2),
                subtitle = stringResource(R.string.crash_report_detail_item_subtitle_2),
            )
        },
        info = null,
        footer = {
            ZashiTextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = state.onOptOutClick,
            ) {
                Text(
                    text = stringResource(R.string.crash_report_detail_opt_out),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold
                )
            }
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.crash_report_detail_opt_in),
                onClick = state.onOptInClick,
                colors = ZashiButtonDefaults.primaryColors()
            )
        }
    )
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            CrashReportOptInView(
                state =
                    CrashReportOptInState(
                        onOptInClick = {},
                        onBack = {},
                        onOptOutClick = {},
                    )
            )
        }
    }
