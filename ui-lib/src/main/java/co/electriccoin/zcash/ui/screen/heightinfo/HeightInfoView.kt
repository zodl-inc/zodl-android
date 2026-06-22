package co.electriccoin.zcash.ui.screen.heightinfo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.rememberModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.HeightInfoState
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeightInfoView(
    state: HeightInfoState,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    InfoBottomSheetView(
        onBack = state.onBack,
        primaryButton =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.restoreInfo_gotIt),
                onClick = state.onBack,
            ),
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.keystone_wbh_info_title),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
        )
        Spacer(12.dp)
        Row {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
                tint = ZashiColors.Text.textTertiary,
            )
            Spacer(8.dp)
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZashiColors.Text.textPrimary)) {
                            append(stringResource(R.string.keystone_wbh_info_message_bold))
                        }
                        append(" ")
                        append(stringResource(R.string.keystone_wbh_info_message))
                    },
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        HeightInfoView(
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    skipHiddenState = true,
                    initialValue = SheetValue.Expanded,
                ),
            state = HeightInfoState { },
        )
    }
