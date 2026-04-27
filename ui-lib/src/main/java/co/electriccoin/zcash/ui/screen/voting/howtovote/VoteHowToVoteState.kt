package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteHowToVoteState(
    val title: StringResource,
    val subtitle: StringResource? = null,
    val steps: List<VoteStep>,
    val infoText: StringResource? = null,
    val isKeystoneUser: Boolean = false,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
)

@Immutable
data class VoteStep(
    val number: String,
    val title: StringResource,
    val description: StringResource,
)
