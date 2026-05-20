package co.electriccoin.zcash.ui.screen.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

data class VoteColors(
    val bg: Color,
    val labelColor: Color,
    val textColor: Color,
)

/**
 * Color logic for vote answer containers and result bars:
 * - SUPPORT → green
 * - OPPOSE  → red
 * - ABSTAIN / GRAY → gray
 * - everything else → indigo (blue)
 */
@Composable
fun VoteOptionDisplayColor.answerColors(): VoteColors =
    when (this) {
        VoteOptionDisplayColor.SUPPORT -> {
            VoteColors(
                bg = ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                labelColor = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                textColor = ZashiColors.Utility.SuccessGreen.utilitySuccess800,
            )
        }

        VoteOptionDisplayColor.OPPOSE -> {
            VoteColors(
                bg = ZashiColors.Utility.ErrorRed.utilityError50,
                labelColor = ZashiColors.Utility.ErrorRed.utilityError500,
                textColor = ZashiColors.Utility.ErrorRed.utilityError600,
            )
        }

        VoteOptionDisplayColor.ABSTAIN, VoteOptionDisplayColor.GRAY -> {
            VoteColors(
                bg = ZashiColors.Utility.Gray.utilityGray50,
                labelColor = ZashiColors.Text.textTertiary,
                textColor = ZashiColors.Text.textSecondary,
            )
        }

        else -> {
            VoteColors(
                bg = ZashiColors.Utility.Indigo.utilityIndigo50,
                labelColor = ZashiColors.Utility.Indigo.utilityIndigo700,
                textColor = ZashiColors.Utility.Indigo.utilityIndigo800,
            )
        }
    }

@Composable
fun voteResultBarColor(isWinner: Boolean): Color =
    if (isWinner) {
        ZashiColors.Utility.Indigo.utilityIndigo500
    } else {
        ZashiColors.Utility.Gray.utilityGray700
    }
