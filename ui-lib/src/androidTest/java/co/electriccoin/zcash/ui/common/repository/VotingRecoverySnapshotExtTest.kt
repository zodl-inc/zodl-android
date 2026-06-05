package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import org.junit.Assert.assertEquals
import org.junit.Test

class VotingRecoverySnapshotExtTest {
    @Test
    fun effectiveChoices_mergesSubmittedSelectionsWithPersistedDraftAbstains() {
        val firstProposal = proposal(id = 1)
        val secondProposal = proposal(id = 2)
        val recovery =
            VotingRecoverySnapshot(
                accountUuid = "account-1",
                roundId = "round-1",
                submittedAtEpochSeconds = 1L,
                draftChoices =
                    mapOf(
                        firstProposal.id to 0,
                        secondProposal.id to secondProposal.abstainChoiceId()
                    ),
                proposalSelections =
                    mapOf(
                        firstProposal.id to VotingProposalSelection(choiceId = 0, numOptions = 2)
                    )
            )

        val choices = recovery.effectiveChoices(listOf(firstProposal, secondProposal))

        assertEquals(
            mapOf(
                firstProposal.id to 0,
                secondProposal.id to secondProposal.abstainChoiceId()
            ),
            choices
        )
    }

    @Test
    fun effectiveChoices_omitsSkippedProposalsAfterSubmission() {
        val proposal = proposal(id = 7)
        val recovery =
            VotingRecoverySnapshot(
                accountUuid = "account-1",
                roundId = "round-2",
                submittedAtEpochSeconds = 1L
            )

        val choices = recovery.effectiveChoices(listOf(proposal))

        assertEquals(emptyMap<Int, Int>(), choices)
    }

    @Test
    fun effectiveChoices_prefersInMemoryDraftChoices() {
        val proposal = proposal(id = 9)
        val recovery =
            VotingRecoverySnapshot(
                accountUuid = "account-1",
                roundId = "round-3",
                submittedAtEpochSeconds = 1L,
                draftChoices = mapOf(proposal.id to proposal.abstainChoiceId()),
                proposalSelections =
                    mapOf(
                        proposal.id to VotingProposalSelection(choiceId = 0, numOptions = 2)
                    )
            )

        val choices =
            recovery.effectiveChoices(
                proposals = listOf(proposal),
                inMemoryDraftChoices = mapOf(proposal.id to 1)
            )

        assertEquals(mapOf(proposal.id to 1), choices)
    }

    private fun proposal(id: Int) =
        Proposal(
            id = id,
            title = "Proposal $id",
            description = "",
            options =
                listOf(
                    VoteOption(id = 0, label = "Support"),
                    VoteOption(id = 1, label = "Oppose")
                )
        )

    private fun Proposal.abstainChoiceId(): Int = (options.maxOf { it.id }) + 1
}
