package co.electriccoin.zcash.ui.screen.voting.tallying

import co.electriccoin.zcash.ui.design.util.StringResource

data class VoteTallyingState(
    val roundTitle: StringResource,
    val endedLabel: StringResource,
    val proposalCount: StringResource,
    val onBack: () -> Unit,
)
