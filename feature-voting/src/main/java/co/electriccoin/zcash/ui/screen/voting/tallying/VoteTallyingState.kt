package co.electriccoin.zcash.ui.screen.voting.tallying

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class VoteTallyingState(
    val roundTitle: StringResource,
    val endedLabel: StringResource,
    val proposalCount: StringResource,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            VoteTallyingState(
                roundTitle = stringRes("ZF Grant Funding — Q3 2026"),
                endedLabel = stringRes("Jan 20, 2026"),
                proposalCount = stringRes("2"),
                onBack = {},
            )
    }
}
