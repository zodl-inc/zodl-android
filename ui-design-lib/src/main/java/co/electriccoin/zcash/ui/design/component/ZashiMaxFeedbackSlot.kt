package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * The one-line disclaimer above the main button (Figma annotation 9162:3138): a neutral hint (info icon, tertiary
 * text) or the Max failure (alert icon, error text). Nothing is drawn when [feedback] is null.
 *
 * The slot owns its spacing to the button: it adds [ZashiMaxFeedbackSlotDefaults.SpacingToButton] below the row, so a
 * caller places the slot directly above the primary button and passes no bottom spacing of its own.
 */
@Composable
fun ZashiMaxFeedbackSlot(
    feedback: MaxFeedback?,
    modifier: Modifier = Modifier,
) {
    when (feedback) {
        is MaxFeedback.Hint -> {
            FeedbackRow(
                icon = R.drawable.ic_info,
                text = feedback.text,
                color = ZashiColors.Text.textTertiary,
                modifier = modifier.testTag(ZashiMaxFeedbackSlotTags.HINT)
            )
        }

        is MaxFeedback.Error -> {
            FeedbackRow(
                icon = R.drawable.ic_alert_circle_outline,
                text = feedback.text,
                color = ZashiColors.Text.textError,
                modifier = modifier.testTag(ZashiMaxFeedbackSlotTags.ERROR)
            )
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun FeedbackRow(
    icon: Int,
    text: StringResource,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = ZashiMaxFeedbackSlotDefaults.SpacingToButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color)
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.weight(1f, false),
            text = text.getValue(),
            style = ZashiTypography.textXs,
            color = color
        )
    }
}

/** What the Max chip's owner says to the user above the main button: a neutral [Hint] or an [Error]. */
@Immutable
sealed interface MaxFeedback {
    val text: StringResource

    /** Neutral hint: Max leaves room for fees and slippage and follows each new quote. */
    data object Hint : MaxFeedback {
        override val text: StringResource = stringRes(R.string.general_maxFeeSlippageHint)
    }

    /** Max failed; the user enters the amount instead. [variant] picks "send" or "swap" in the copy. */
    data class Error(
        val variant: MaxVariant
    ) : MaxFeedback {
        override val text: StringResource =
            when (variant) {
                MaxVariant.SEND -> stringRes(R.string.general_maxUnavailableSend)
                MaxVariant.SWAP -> stringRes(R.string.general_maxUnavailableSwap)
            }
    }

    companion object {
        /**
         * The error wins over the hint: it answers the user's last tap and clears itself on the next edit, while
         * the hint describes the Max fill and clears with it.
         */
        fun of(
            hint: Hint?,
            error: Error?
        ): MaxFeedback? = error ?: hint
    }
}

/** What the user wants to do with the amount, which only changes the copy of [MaxFeedback.Error]. */
enum class MaxVariant {
    SEND,
    SWAP
}

object ZashiMaxFeedbackSlotDefaults {
    /** Space between the one-line row and the primary button below it (Figma 9162:3138). */
    val SpacingToButton: Dp = 24.dp
}

object ZashiMaxFeedbackSlotTags {
    const val ERROR = "zashi_max_feedback_error"
    const val HINT = "zashi_max_feedback_hint"
}

private class MaxFeedbackPreviewProvider : PreviewParameterProvider<MaxFeedback> {
    override val values =
        sequenceOf(
            MaxFeedback.Hint,
            MaxFeedback.Error(MaxVariant.SEND),
            MaxFeedback.Error(MaxVariant.SWAP),
        )
}

@PreviewScreens
@Composable
private fun ZashiMaxFeedbackSlotPreview(
    @PreviewParameter(MaxFeedbackPreviewProvider::class) feedback: MaxFeedback
) = ZcashTheme {
    BlankSurface {
        ZashiMaxFeedbackSlot(
            feedback = feedback,
            modifier = Modifier.padding(horizontal = ZcashTheme.dimens.spacingDefault)
        )
    }
}
