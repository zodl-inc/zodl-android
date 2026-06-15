package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState

@Immutable
data class VoteHowToVoteState(
    val title: StringResource,
    val subtitle: StringResource? = null,
    val steps: List<VoteStep>,
    val infoText: StringResource? = null,
    val walletHeaderIcons: WalletHeaderIconsState,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            VoteHowToVoteState(
                title = stringRes("How to vote"),
                subtitle = stringRes("Follow the steps below to cast your vote."),
                steps = listOf(VoteStep.preview),
                infoText = null,
                walletHeaderIcons =
                    WalletHeaderIconsState(
                        isKeystone = false,
                        badgeIcon = R.drawable.ic_vote_thumbs_up,
                    ),
                continueButton = ButtonState.preview,
                onBack = {},
            )
    }
}

@Immutable
data class VoteStep(
    val number: String,
    val title: StringResource,
    val description: StringResource,
) {
    companion object {
        val preview =
            VoteStep(
                number = "1",
                title = stringRes("Open your wallet"),
                description = stringRes("Make sure your wallet is synced before voting."),
            )
    }
}
