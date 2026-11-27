package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingBallotIntent
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import kotlin.test.Test
import kotlin.test.assertEquals

class VotingBallotIntentBuilderTest {
    @Test
    fun `an answered proposal produces an intent carrying that choice`() {
        val proposals = listOf(proposal(id = 1))
        val choices = mapOf(1 to 0)

        val intents = buildBallotIntents(proposals, choices)

        assertEquals(listOf(VotingBallotIntent(proposalId = 1, choice = 0)), intents)
    }

    @Test
    fun `a proposal absent from choices produces a skipped (null-choice) intent, not an omission`() {
        val proposals = listOf(proposal(id = 1), proposal(id = 2))
        // Only proposal 1 was answered -- proposal 2 was left blank by the user, a valid
        // partial-ballot case the round driver must see as an explicit Skipped decision, not as
        // "no intent recorded at all" (the latter is what triggers a blocking NeedsBallot).
        val choices = mapOf(1 to 2)

        val intents = buildBallotIntents(proposals, choices)

        assertEquals(
            listOf(
                VotingBallotIntent(proposalId = 1, choice = 2),
                VotingBallotIntent(proposalId = 2, choice = null)
            ),
            intents
        )
    }

    @Test
    fun `every proposal in the roster gets exactly one intent, regardless of answer order`() {
        val proposals = listOf(proposal(id = 1), proposal(id = 2), proposal(id = 3))
        val choices = mapOf(3 to 1, 1 to 0)

        val intents = buildBallotIntents(proposals, choices)

        assertEquals(3, intents.size)
        assertEquals(setOf(1, 2, 3), intents.map { it.proposalId }.toSet())
        assertEquals(0, intents.first { it.proposalId == 1 }.choice)
        assertEquals(null, intents.first { it.proposalId == 2 }.choice)
        assertEquals(1, intents.first { it.proposalId == 3 }.choice)
    }

    @Test
    fun `an entirely unanswered roster produces all-skipped intents`() {
        val proposals = listOf(proposal(id = 1), proposal(id = 2))

        val intents = buildBallotIntents(proposals, emptyMap())

        assertEquals(
            listOf(
                VotingBallotIntent(proposalId = 1, choice = null),
                VotingBallotIntent(proposalId = 2, choice = null)
            ),
            intents
        )
    }

    @Test
    fun `a choices entry for a proposal id outside the roster is ignored`() {
        val proposals = listOf(proposal(id = 1))
        // Defensive case: choices should never legitimately contain an id the round doesn't
        // have, but the builder must not fabricate an intent for it either way -- the roster,
        // not the choices map, is what determines which proposals get an intent at all.
        val choices = mapOf(1 to 0, 999 to 5)

        val intents = buildBallotIntents(proposals, choices)

        assertEquals(listOf(VotingBallotIntent(proposalId = 1, choice = 0)), intents)
    }

    private fun proposal(id: Int) =
        Proposal(
            id = id,
            title = "Proposal $id",
            description = "",
            options = listOf(VoteOption(id = 0, label = "Yes"), VoteOption(id = 1, label = "No"))
        )
}
