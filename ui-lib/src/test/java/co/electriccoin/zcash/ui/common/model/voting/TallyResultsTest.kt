package co.electriccoin.zcash.ui.common.model.voting

import kotlin.test.Test
import kotlin.test.assertEquals

class TallyResultsTest {
    @Test
    fun chainTallyResultsUseProvidedBallotDivisor() {
        val results =
            ChainTallyResultsResponse(
                results =
                    listOf(
                        ChainTallyResultEntry(proposalId = 1, voteDecision = 0, totalValue = 2),
                        ChainTallyResultEntry(proposalId = 1, voteDecision = 1, totalValue = 3),
                        ChainTallyResultEntry(proposalId = 2, voteDecision = 0, totalValue = 4)
                    )
            ).toTallyResults(
                roundId = "round",
                ballotDivisorZatoshi = 125L
            )

        assertEquals("round", results.roundId)
        assertEquals(
            listOf(
                ProposalTally(
                    proposalId = 1,
                    options =
                        listOf(
                            OptionTally(optionId = 0, weight = 250),
                            OptionTally(optionId = 1, weight = 375)
                        )
                ),
                ProposalTally(
                    proposalId = 2,
                    options =
                        listOf(
                            OptionTally(optionId = 0, weight = 500)
                        )
                )
            ),
            results.proposals
        )
    }
}
