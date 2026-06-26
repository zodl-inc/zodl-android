package co.electriccoin.zcash.ui.screen.texunsupported

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TEXUnsupportedView(
    state: TEXUnsupportedState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    state ?: return
    InfoBottomSheetView(
        onBack = state.onBack,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.restoreInfo_gotIt),
                onClick = state.onBack,
            ),
        sheetState = sheetState,
    ) {
        Image(painterResource(R.drawable.ic_swap_quote_error), contentDescription = null)
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.texKeystone_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        val description =
            (stringRes(R.string.texKeystone_warn1) + stringRes(" ")).withStyle(
                StyledStringStyle(
                    color = StringResourceColor.PRIMARY,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) +
                styledStringResource(
                    R.string.texKeystone_warn2,
                    StringResourceColor.TERTIARY,
                )
        Text(
            text = description.getValue(),
            style = ZashiTypography.textSm,
        )
        Spacer(24.dp)
        Text(
            text = stringResource(R.string.texKeystone_workaround),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textMd,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(16.dp)
        StepItem(
            badge = stringResource(R.string.tex_unsupported_step_1_badge),
            title = stringResource(R.string.texKeystone_step1_title),
            description = stringResource(R.string.texKeystone_step1_desc),
            icon = R.drawable.ic_tex_unsupported_1,
        )
        Spacer(28.dp)
        StepItem(
            badge = stringResource(R.string.tex_unsupported_step_2_badge),
            title = stringResource(R.string.texKeystone_step2_title),
            description = stringResource(R.string.texKeystone_step2_desc),
            icon = R.drawable.ic_tex_unsupported_2,
        )
    }
}

@Composable
private fun StepItem(
    badge: String,
    title: String,
    description: String,
    icon: Int,
) {
    Row(verticalAlignment = Alignment.Top) {
        Image(painterResource(icon), contentDescription = null)
        Spacer(16.dp)
        Column(modifier = Modifier.weight(1f)) {
            Spacer(4.dp)
            ZashiBadge(
                text = badge,
                colors = ZashiBadgeDefaults.infoColors(),
                contentPadding = PaddingValues(6.dp, 2.dp),
                shape = RoundedCornerShape(6.dp),
            )
            Spacer(8.dp)
            Text(
                text = title,
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
            )
            Spacer(4.dp)
            Text(
                text = description,
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        TEXUnsupportedView(state = TEXUnsupportedState(onBack = {}))
    }
