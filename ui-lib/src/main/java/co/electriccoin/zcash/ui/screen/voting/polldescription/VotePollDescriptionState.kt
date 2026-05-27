package co.electriccoin.zcash.ui.screen.voting.polldescription

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class VotePollDescriptionState(
    val title: StringResource,
    val description: StringResource,
    val discussionUrl: String?,
    val onDiscussionClick: () -> Unit,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            VotePollDescriptionState(
                title = stringRes("ZF Grant Funding — Q3 2026"),
                description =
                    stringRes(
                        "This round covers the allocation of Zcash Foundation grant funds for Q3 2026."
                    ),
                discussionUrl = null,
                onDiscussionClick = {},
                onBack = {},
            )
    }
}
