package co.electriccoin.zcash.ui.screen.swap.detail.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapSupportView(state: SwapSupportState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { state, contentPadding ->
            Content(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, false),
                state = state,
                contentPadding = contentPadding
            )
        }
    )
}

@Composable
private fun Content(
    state: SwapSupportState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                ),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        color = ZashiColors.Utility.ErrorRed.utilityError50,
                        shape = CircleShape
                    ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = ZashiColors.Utility.ErrorRed.utilityError600
            )
        }
        Spacer(12.dp)
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Start
        )
        Spacer(12.dp)
        Text(
            text = state.message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Start
        )
        Spacer(32.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.reportIssueButton
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        Content(
            SwapSupportState(
                title = stringRes(R.string.transaction_detail_support_disclaimer_title),
                message = stringRes(R.string.transaction_detail_support_disclaimer_message),
                reportIssueButton =
                    ButtonState(
                        text = stringRes(R.string.transaction_detail_report_issue),
                        onClick = { }
                    ),
                onBack = { }
            ),
            PaddingValues(horizontal = 0.dp, vertical = 16.dp)
        )
    }
