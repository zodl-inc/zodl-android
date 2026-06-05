package co.electriccoin.zcash.ui.screen.voting.results

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VoteResultsVMTest {
    @Test
    fun votedResultLabelUsesSubmittedProposalSelection() {
        val proposal = proposal()
        val recovery =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                proposalSelections =
                    mapOf(
                        proposal.id to VotingProposalSelection(choiceId = 2, numOptions = 2)
                    ),
                submittedAtEpochSeconds = 1L
            )

        assertVotedLabel("No", proposal.votedResultLabel(recovery))
    }

    @Test
    fun votedResultLabelIsNullForSkippedProposalAfterSubmission() {
        val proposal = proposal()
        val recovery =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                submittedAtEpochSeconds = 1L
            )

        assertNull(proposal.votedResultLabel(recovery))
    }

    @Test
    fun votedResultLabelUsesFallbackForUnknownChoice() {
        val proposal = proposal()
        val recovery =
            VotingRecoverySnapshot(
                accountUuid = "account",
                roundId = "round",
                proposalSelections =
                    mapOf(
                        proposal.id to VotingProposalSelection(choiceId = 99, numOptions = 2)
                    )
            )

        assertVotedLabel("Voted", proposal.votedResultLabel(recovery))
    }

    @Test
    fun votedResultLabelIsAbsentWithoutRecovery() {
        assertNull(proposal().votedResultLabel(recovery = null))
    }

    private fun assertVotedLabel(
        expectedAnswer: String,
        actual: StringResource?
    ) {
        val resource = assertIs<StringResource.ByResource>(actual)
        assertEquals(R.string.vote_results_voted_option, resource.resource)
        assertContentEquals(listOf(expectedAnswer), resource.args)
    }

    private fun proposal() =
        Proposal(
            id = 1,
            title = "Proposal",
            description = "Description",
            options =
                listOf(
                    VoteOption(id = 1, label = "Yes"),
                    VoteOption(id = 2, label = "No")
                )
        )
}
