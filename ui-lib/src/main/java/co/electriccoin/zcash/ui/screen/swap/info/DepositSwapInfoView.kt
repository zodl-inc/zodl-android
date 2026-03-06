package co.electriccoin.zcash.ui.screen.swap.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSwapInfoView(onBack: () -> Unit) {
    ZashiScreenModalBottomSheet(
        onDismissRequest = onBack
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, false)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = it.calculateBottomPadding()
                    )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = stringResource(R.string.swap_deposit_info_title),
                    style = ZashiTypography.textXl,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
            Spacer(12.dp)
            Text(
                text = stringResource(R.string.swap_deposit_info_step_1),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
            Spacer(32.dp)
            ZashiBulletText(
                bulletTexts = listOf(stringResource(R.string.swap_deposit_info_step_2)),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
            Spacer(32.dp)
            ZashiBulletText(
                bulletTexts = listOf(stringResource(R.string.swap_deposit_info_step_3)),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
            Spacer(32.dp)
            ZashiBulletText(
                bulletTexts = listOf(stringResource(R.string.swap_deposit_info_step_4)),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
            Spacer(32.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(co.electriccoin.zcash.ui.design.R.string.general_ok),
                onClick = onBack
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        DepositSwapInfoView { }
    }
