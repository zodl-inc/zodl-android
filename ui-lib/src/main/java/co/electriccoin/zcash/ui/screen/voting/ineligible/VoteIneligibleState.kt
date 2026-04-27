package co.electriccoin.zcash.ui.screen.voting.ineligible

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class VoteIneligibleState(
    val title: StringResource,
    val body: StringResource,
    val closeButton: ButtonState,
    val onBack: () -> Unit,
)
