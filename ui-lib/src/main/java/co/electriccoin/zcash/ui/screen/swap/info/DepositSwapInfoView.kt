package co.electriccoin.zcash.ui.screen.swap.info

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSwapInfoView(onBack: () -> Unit) {
    InfoBottomSheetView(
        onBack = onBack,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                onClick = onBack,
            ),
    ) {
        Text(
            text = stringResource(R.string.depositFunds_title),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.depositFunds_desc),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(32.dp)
        ZashiBulletText(
            bulletTexts = listOf(stringResource(R.string.depositFunds_bulletPoint1)),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(32.dp)
        ZashiBulletText(
            bulletTexts = listOf(stringResource(R.string.depositFunds_bulletPoint2)),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(32.dp)
        ZashiBulletText(
            bulletTexts = listOf(stringResource(R.string.depositFunds_bulletPoint3)),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme { DepositSwapInfoView { } }
