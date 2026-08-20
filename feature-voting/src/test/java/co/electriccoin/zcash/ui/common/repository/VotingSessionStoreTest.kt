package co.electriccoin.zcash.ui.common.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class VotingSessionStoreTest {
    @Test
    fun clearDraftVoteRemovesOnlySubmittedProposalDraft() {
        val store = VotingSessionStoreImpl()

        store.restoreDraftVotes(
            accountUuid = "account",
            roundId = "round",
            draftVotes =
                mapOf(
                    1 to 10,
                    2 to 20
                )
        )

        store.clearDraftVote(
            accountUuid = "account",
            roundId = "round",
            proposalId = 1
        )

        assertEquals(
            mapOf(2 to 20),
            store.state.value.draftVotesFor(
                accountUuid = "account",
                roundId = "round"
            )
        )
    }
}
