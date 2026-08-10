package co.electriccoin.zcash.ui.common.model.voting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChainDtoTest {
    @Test
    fun chainRoundSourcesProposalIdentityFromChainResponse() {
        val round =
            makeRound(
                proposals =
                    listOf(
                        makeProposal(
                            id = 2,
                            options =
                                listOf(
                                    ChainVoteOptionDto(label = "No", index = 1),
                                    ChainVoteOptionDto(label = "Yes", index = 0)
                                )
                        ),
                        makeProposal(
                            id = 3,
                            options =
                                listOf(
                                    ChainVoteOptionDto(label = "Abstain", index = 0),
                                    ChainVoteOptionDto(label = "Approve", index = 1)
                                )
                        )
                    )
            ).toVotingRound()

        assertEquals(listOf(2, 3), round.proposals.map(Proposal::id))
        assertEquals(
            listOf(1, 0),
            round.proposals
                .first()
                .options
                .map(VoteOption::id)
        )
        assertEquals(
            listOf(0, 1),
            round.proposals
                .last()
                .options
                .map(VoteOption::id)
        )
    }

    @Test
    fun chainSessionSourcesProposalIdentityFromChainResponse() {
        val session =
            makeRound(
                proposals = listOf(makeProposal(id = 2))
            ).toVotingSession()

        assertEquals(listOf(2), session.proposals.map(Proposal::id))
    }

    @Test
    fun chainRoundRejectsEmptyProposalList() {
        assertFailsWith<VotingConfigException> {
            makeRound(proposals = emptyList()).toVotingRound()
        }
    }

    @Test
    fun chainRoundRejectsTooManyProposals() {
        assertFailsWith<VotingConfigException> {
            makeRound(
                proposals = (1..16).map { index -> makeProposal(id = index) }
            ).toVotingRound()
        }
    }

    @Test
    fun chainRoundRejectsProposalIdsOutsideBounds() {
        listOf(0, 16).forEach { proposalId ->
            assertFailsWith<VotingConfigException> {
                makeRound(
                    proposals = listOf(makeProposal(id = proposalId))
                ).toVotingRound()
            }
        }
    }

    @Test
    fun chainRoundRejectsDuplicateProposalIds() {
        assertFailsWith<VotingConfigException> {
            makeRound(
                proposals =
                    listOf(
                        makeProposal(id = 1),
                        makeProposal(id = 1)
                    )
            ).toVotingRound()
        }
    }

    @Test
    fun chainRoundRejectsNonContiguousOptionIndices() {
        assertFailsWith<VotingConfigException> {
            makeRound(
                proposals =
                    listOf(
                        makeProposal(
                            options =
                                listOf(
                                    ChainVoteOptionDto(label = "No", index = 0),
                                    ChainVoteOptionDto(label = "Yes", index = 2)
                                )
                        )
                    )
            ).toVotingRound()
        }
    }

    @Test
    fun chainRoundRejectsInvalidOptionCounts() {
        listOf(
            listOf(ChainVoteOptionDto(label = "Only", index = 0)),
            (0..8).map { index -> ChainVoteOptionDto(label = "Option $index", index = index) }
        ).forEach { options ->
            assertFailsWith<VotingConfigException> {
                makeRound(
                    proposals = listOf(makeProposal(options = options))
                ).toVotingRound()
            }
        }
    }

    @Test
    fun chainRoundRejectsMissingOptionIndicesWhenTheyCreateDuplicates() {
        assertFailsWith<VotingConfigException> {
            makeRound(
                proposals =
                    listOf(
                        makeProposal(
                            options =
                                listOf(
                                    ChainVoteOptionDto(label = "No"),
                                    ChainVoteOptionDto(label = "Yes")
                                )
                        )
                    )
            ).toVotingRound()
        }
    }

    private fun makeRound(
        proposals: List<ChainProposalDto>
    ): ChainRoundDto =
        ChainRoundDto(
            voteRoundId = "00".repeat(32),
            title = "Round",
            snapshotHeight = 100,
            voteEndTime = 200,
            proposals = proposals
        )

    private fun makeProposal(
        id: Int = 1,
        options: List<ChainVoteOptionDto> =
            listOf(
                ChainVoteOptionDto(label = "No", index = 0),
                ChainVoteOptionDto(label = "Yes", index = 1)
            )
    ): ChainProposalDto =
        ChainProposalDto(
            id = id,
            title = "Proposal $id",
            options = options
        )
}
