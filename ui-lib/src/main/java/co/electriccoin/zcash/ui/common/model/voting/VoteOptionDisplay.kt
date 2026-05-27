package co.electriccoin.zcash.ui.common.model.voting

enum class VoteOptionDisplayColor {
    SUPPORT,
    OPPOSE,
    ABSTAIN,
    BLUE,
    WARNING,
    INDIGO,
    BRAND,
    GRAY,
    INDIGO_DARK,
}

data class VoteOptionDisplayInfo(
    val label: String,
    val color: VoteOptionDisplayColor,
)

fun VoteOption.displayColor(): VoteOptionDisplayColor = voteOptionDisplayColor(label = label)

fun Proposal.voteBadgeInfo(choiceId: Int): VoteOptionDisplayInfo {
    val matchedIndex = options.indexOfFirst { option -> option.id == choiceId }
    if (matchedIndex >= 0) {
        val matched = options[matchedIndex]
        return VoteOptionDisplayInfo(
            label = matched.label,
            color = matched.displayColor()
        )
    }

    val isSyntheticAbstain = options.none(VoteOption::isAbstainOption) && choiceId == abstainOptionId()
    if (isSyntheticAbstain) {
        return VoteOptionDisplayInfo(
            label = "Abstain",
            color = VoteOptionDisplayColor.ABSTAIN
        )
    }

    return VoteOptionDisplayInfo(
        label = "Voted",
        color = VoteOptionDisplayColor.GRAY
    )
}

fun Proposal.tallyDisplayInfo(
    decision: Int,
    fallbackLabel: String,
): VoteOptionDisplayInfo {
    val matchedIndex = options.indexOfFirst { it.id == decision }
    if (matchedIndex >= 0) {
        val option = options[matchedIndex]
        return VoteOptionDisplayInfo(
            label = option.label,
            color = option.displayColor()
        )
    }

    if (fallbackLabel.contains("abstain", ignoreCase = true)) {
        return VoteOptionDisplayInfo(
            label = fallbackLabel,
            color = VoteOptionDisplayColor.ABSTAIN
        )
    }

    return VoteOptionDisplayInfo(
        label = fallbackLabel,
        color = VoteOptionDisplayColor.GRAY
    )
}

private fun voteOptionDisplayColor(label: String): VoteOptionDisplayColor =
    when {
        label.contains("abstain", ignoreCase = true) -> VoteOptionDisplayColor.ABSTAIN

        label.equals("yes", ignoreCase = true) ||
            label.equals(
                "support",
                ignoreCase = true
            )
        -> VoteOptionDisplayColor.SUPPORT

        label.equals("no", ignoreCase = true) ||
            label.equals(
                "oppose",
                ignoreCase = true
            )
        -> VoteOptionDisplayColor.OPPOSE

        else -> VoteOptionDisplayColor.BLUE
    }
