package co.electriccoin.zcash.ui.screen.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

@Composable
internal fun VoteOptionDisplayColor.accentColor(): Color =
    when (this) {
        VoteOptionDisplayColor.SUPPORT -> ZashiColors.Utility.SuccessGreen.utilitySuccess500
        VoteOptionDisplayColor.OPPOSE -> ZashiColors.Utility.ErrorRed.utilityError500
        VoteOptionDisplayColor.ABSTAIN -> ZashiColors.Utility.HyperBlue.utilityBlueDark700
        VoteOptionDisplayColor.PURPLE -> ZashiColors.Utility.Purple.utilityPurple500
        VoteOptionDisplayColor.WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange500
        VoteOptionDisplayColor.INDIGO -> ZashiColors.Utility.Indigo.utilityIndigo500
        VoteOptionDisplayColor.BRAND -> ZashiColors.Surfaces.brandBg
        VoteOptionDisplayColor.GRAY -> ZashiColors.Utility.Gray.utilityGray500
        VoteOptionDisplayColor.INDIGO_DARK -> ZashiColors.Utility.Indigo.utilityIndigo700
    }
