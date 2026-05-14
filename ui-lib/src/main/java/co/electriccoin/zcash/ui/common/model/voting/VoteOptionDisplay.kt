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
    val isSyntheticAbstain = options.none(VoteOption::isAbstainOption) && choiceId == abstainOptionId()
    return when {
        matchedIndex >= 0 -> {
            val matched = options[matchedIndex]
            VoteOptionDisplayInfo(
                label = matched.label,
                color = matched.displayColor(position = matchedIndex, total = options.size)
            )
        }

        isSyntheticAbstain -> {
            VoteOptionDisplayInfo(
                label = "Abstain",
                color = VoteOptionDisplayColor.ABSTAIN
            )
        }

        else -> {
            VoteOptionDisplayInfo(
                label = "Voted",
                color = VoteOptionDisplayColor.GRAY
            )
        }
    }
}

fun Proposal.tallyDisplayInfo(
    decision: Int,
    fallbackLabel: String,
): VoteOptionDisplayInfo {
    val matchedIndex = options.indexOfFirst { it.id == decision }
    return when {
        matchedIndex >= 0 -> {
            val option = options[matchedIndex]
            VoteOptionDisplayInfo(
                label = option.label,
                color = option.displayColor(position = matchedIndex, total = options.size)
            )
        }

        fallbackLabel.contains("abstain", ignoreCase = true) -> {
            VoteOptionDisplayInfo(
                label = fallbackLabel,
                color = VoteOptionDisplayColor.ABSTAIN
            )
        }

        else -> {
            VoteOptionDisplayInfo(
                label = fallbackLabel,
                color = VoteOptionDisplayColor.GRAY
            )
        }
    }
}

private fun voteOptionDisplayColor(
    label: String,
    position: Int,
    total: Int,
): VoteOptionDisplayColor {
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

    return when {
        label.contains("abstain", ignoreCase = true) -> VoteOptionDisplayColor.ABSTAIN
        total == BINARY_OPTION_COUNT && position == 0 -> VoteOptionDisplayColor.SUPPORT
        total == BINARY_OPTION_COUNT -> VoteOptionDisplayColor.OPPOSE
        else -> palette[position.mod(palette.size)]
    }
}

private const val BINARY_OPTION_COUNT = 2
