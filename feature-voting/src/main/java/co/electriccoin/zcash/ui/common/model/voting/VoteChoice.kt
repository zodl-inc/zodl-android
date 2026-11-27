package co.electriccoin.zcash.ui.common.model.voting

sealed class VoteChoice {
    data class Option(
        val proposalId: Int,
        val optionIndex: Int
    ) : VoteChoice()
}
