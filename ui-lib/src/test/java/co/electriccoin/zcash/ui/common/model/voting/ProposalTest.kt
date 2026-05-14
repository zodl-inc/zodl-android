package co.electriccoin.zcash.ui.common.model.voting

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProposalTest {
    @Test
    fun generatedAbstainChoiceIsSyntheticWhenProposalHasNoOnWireAbstain() {
        val proposal =
            proposal(
                options =
                    listOf(
                        VoteOption(id = 0, label = "Support"),
                        VoteOption(id = 1, label = "Oppose")
                    )
            )

        assertTrue(proposal.isSyntheticAbstainChoice(choiceId = 2))
    }

    @Test
    fun onWireAbstainChoiceIsNotSynthetic() {
        val proposal =
            proposal(
                options =
                    listOf(
                        VoteOption(id = 0, label = "Support"),
                        VoteOption(id = 1, label = "Abstain")
                    )
            )

        assertFalse(proposal.isSyntheticAbstainChoice(choiceId = 1))
    }

    @Test
    fun unknownChoiceIsNotSynthetic() {
        val proposal =
            proposal(
                options =
                    listOf(
                        VoteOption(id = 0, label = "Support"),
                        VoteOption(id = 1, label = "Oppose")
                    )
            )

        assertFalse(proposal.isSyntheticAbstainChoice(choiceId = 99))
    }

    private fun proposal(
        options: List<VoteOption>
    ) = Proposal(
        id = 1,
        title = "Proposal",
        description = "",
        options = options
    )
}
