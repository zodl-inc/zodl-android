package co.electriccoin.zcash.ui.common.model.voting

enum class VoteOptionDisplayColor {
    SUPPORT,
    OPPOSE,
    ABSTAIN,
    PURPLE,
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

fun VoteOption.displayColor(
    position: Int,
    total: Int,
): VoteOptionDisplayColor = voteOptionDisplayColor(label = label, position = position, total = total)

fun Proposal.voteBadgeInfo(choiceId: Int): VoteOptionDisplayInfo {
    val matchedIndex = options.indexOfFirst { option -> option.id == choiceId }
    if (matchedIndex >= 0) {
        val matched = options[matchedIndex]
        return VoteOptionDisplayInfo(
            label = matched.label,
            color = matched.displayColor(position = matchedIndex, total = options.size)
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
            color = option.displayColor(position = matchedIndex, total = options.size)
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

private fun voteOptionDisplayColor(
    label: String,
    position: Int,
    total: Int,
): VoteOptionDisplayColor {
    if (label.contains("abstain", ignoreCase = true)) {
        return VoteOptionDisplayColor.ABSTAIN
    }

    if (total == 2) {
        return if (position == 0) {
            VoteOptionDisplayColor.SUPPORT
        } else {
            VoteOptionDisplayColor.OPPOSE
        }
    }

    val palette =
        listOf(
            VoteOptionDisplayColor.SUPPORT,
            VoteOptionDisplayColor.OPPOSE,
            VoteOptionDisplayColor.PURPLE,
            VoteOptionDisplayColor.WARNING,
            VoteOptionDisplayColor.INDIGO,
            VoteOptionDisplayColor.BRAND,
            VoteOptionDisplayColor.GRAY,
            VoteOptionDisplayColor.INDIGO_DARK,
        )

    return palette[position.mod(palette.size)]
}
