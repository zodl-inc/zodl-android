package co.electriccoin.zcash.ui.screen.voting.polldescription

import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class VotePollDescriptionState(
    val title: StringResource,
    val description: StringResource,
    val discussionUrl: String?,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
