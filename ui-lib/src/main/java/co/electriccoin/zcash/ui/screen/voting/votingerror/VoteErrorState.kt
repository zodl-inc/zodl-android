package co.electriccoin.zcash.ui.screen.voting.votingerror

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

data class VoteErrorState(
    val title: StringResource,
    val message: StringResource,
    val actionButton: ButtonState,
    val onBack: () -> Unit,
)

data class VoteConfigErrorState(
    val message: StringResource,
    val dismissButton: ButtonState,
    val onBack: () -> Unit,
)
