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

@Composable
fun VoteOptionDisplayColor.answerColors(): VoteColors =
    when (this) {
        VoteOptionDisplayColor.SUPPORT -> {
            VoteColors(
                bg = ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                labelColor = ZashiColors.Utility.SuccessGreen.utilitySuccess500,
                textColor = ZashiColors.Utility.SuccessGreen.utilitySuccess500,
            )
        }

        VoteOptionDisplayColor.OPPOSE -> {
            VoteColors(
                bg = ZashiColors.Utility.ErrorRed.utilityError50,
                labelColor = ZashiColors.Utility.ErrorRed.utilityError500,
                textColor = ZashiColors.Utility.ErrorRed.utilityError500,
            )
        }

        VoteOptionDisplayColor.ABSTAIN, VoteOptionDisplayColor.GRAY -> {
            VoteColors(
                bg = ZashiColors.Utility.Gray.utilityGray50,
                labelColor = ZashiColors.Utility.Gray.utilityGray500,
                textColor = ZashiColors.Utility.Gray.utilityGray500,
            )
        }

        else -> {
            VoteColors(
                bg = ZashiColors.Utility.HyperBlue.utilityBlueDark50,
                labelColor = ZashiColors.Utility.HyperBlue.utilityBlueDark500,
                textColor = ZashiColors.Utility.HyperBlue.utilityBlueDark500,
            )
        }
    }
