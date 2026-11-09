package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * The one slot above the main button that tells the user why Max is not working (Figma annotation 9044:812): the
 * failure [banner] or the unavailability [hint]. When both apply the banner wins (see [MaxFeedback.of]).
 *
 * Nothing is drawn when both are null, so [modifier] (typically the spacing to the button) only applies to content.
 */
@Composable
fun ZashiMaxFeedbackSlot(
    hint: ZashiMaxHintRowState?,
    banner: ZashiInlineBannerState?,
    modifier: Modifier = Modifier,
) {
    when (val feedback = MaxFeedback.of(hint = hint, banner = banner)) {
        is MaxFeedback.Banner -> {
            ZashiInlineBanner(
                state = feedback.state,
                modifier = modifier.testTag(ZashiMaxFeedbackSlotTags.BANNER)
            )
        }

        is MaxFeedback.Hint -> {
            ZashiMaxHintRow(
                state = feedback.state,
                modifier = modifier.testTag(ZashiMaxFeedbackSlotTags.HINT)
            )
        }

        null -> {
            Unit
        }
    }
}

/** What [ZashiMaxFeedbackSlot] shows. */
@Immutable
sealed interface MaxFeedback {
    data class Banner(
        val state: ZashiInlineBannerState
    ) : MaxFeedback

    data class Hint(
        val state: ZashiMaxHintRowState
    ) : MaxFeedback

    companion object {
        /**
         * The banner wins over the hint: it answers the user's last tap and clears itself on the next edit, while
         * the hint describes the balance and comes back on its own.
         */
        fun of(
            hint: ZashiMaxHintRowState?,
            banner: ZashiInlineBannerState?
        ): MaxFeedback? = banner?.let(::Banner) ?: hint?.let(::Hint)
    }
}

/** The generic "couldn't calculate the max amount" banner (Figma 9043:2986); the reason is only logged. */
fun maxFailedBannerState() =
    ZashiInlineBannerState(
        title = stringRes(R.string.general_maxFailedTitle),
        subtitle = stringRes(R.string.general_maxFailedSubtitle)
    )

/** "Max uses shielded ZEC only." with a Shield action (Figma 9043:926); [onShield] opens the shielding flow. */
fun maxShieldedOnlyHintState(onShield: () -> Unit) =
    ZashiMaxHintRowState(
        text = stringRes(R.string.general_maxHintShieldedOnly),
        button = ChipButtonState(text = stringRes(R.string.general_maxHintShield), onClick = onShield)
    )

/** "Max will be available once your funds confirm." (Figma 9043:1056). */
fun maxFundsConfirmingHintState() = ZashiMaxHintRowState(text = stringRes(R.string.general_maxHintConfirming))

object ZashiMaxFeedbackSlotTags {
    const val BANNER = "zashi_max_feedback_banner"
    const val HINT = "zashi_max_feedback_hint"
}

private typealias MaxFeedbackPreviewInput = Pair<ZashiMaxHintRowState?, ZashiInlineBannerState?>

private class MaxFeedbackPreviewProvider : PreviewParameterProvider<MaxFeedbackPreviewInput> {
    override val values =
        sequenceOf(
            maxShieldedOnlyHintState(onShield = {}) to null,
            maxFundsConfirmingHintState() to null,
            maxShieldedOnlyHintState(onShield = {}) to maxFailedBannerState(),
        )
}

@PreviewScreens
@Composable
private fun ZashiMaxFeedbackSlotPreview(
    @PreviewParameter(MaxFeedbackPreviewProvider::class) feedback: MaxFeedbackPreviewInput
) = ZcashTheme {
    BlankSurface {
        ZashiMaxFeedbackSlot(
            hint = feedback.first,
            banner = feedback.second,
            modifier = Modifier.padding(ZcashTheme.dimens.spacingDefault)
        )
    }
}
