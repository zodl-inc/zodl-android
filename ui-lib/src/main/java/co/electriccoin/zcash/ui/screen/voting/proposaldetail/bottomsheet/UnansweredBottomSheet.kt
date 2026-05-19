package co.electriccoin.zcash.ui.screen.voting.proposaldetail.bottomsheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.R as DesignR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnansweredBottomSheet(
    unansweredCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZashiColors.Surfaces.bgPrimary,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                    .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color =
                    ZashiColors.Utility.ErrorRed.utilityError500
                        .copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter =
                        androidx.compose.ui.res
                            .painterResource(DesignR.drawable.ic_info),
                    contentDescription = null,
                    tint = ZashiColors.Utility.ErrorRed.utilityError500,
                    modifier = Modifier.padding(12.dp)
                )
            }

            VerticalSpacer(16.dp)

            Text(
                text = stringRes(R.string.vote_proposal_detail_unanswered_title).getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )

            VerticalSpacer(8.dp)

            val message =
                if (unansweredCount == 1) {
                    stringRes(R.string.vote_proposal_detail_unanswered_message_singular)
                } else {
                    stringRes(R.string.vote_proposal_detail_unanswered_message_plural, unansweredCount)
                }

            Text(
                text = message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            VerticalSpacer(24.dp)

            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ButtonState(
                        text = stringRes(R.string.vote_confirm_cta),
                        style = ButtonStyle.SECONDARY,
                        onClick = onConfirm
                    )
            )

            VerticalSpacer(12.dp)

            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ButtonState(
                        text = stringRes(R.string.vote_proposal_detail_unanswered_go_back),
                        style = ButtonStyle.PRIMARY,
                        onClick = onDismiss
                    )
            )
        }
    }
}
