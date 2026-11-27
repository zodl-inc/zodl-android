package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

/**
 * The Figma "Waiting Indicator": a spinner turning once a second over a centred title that wraps
 * rather than being cut, with an optional subtitle. Without [showSpinner] only the text is drawn,
 * which the sign sheet's device picker uses as its heading. The title is a polite live region, so a
 * screen reader reads out a status that replaces the one shown.
 */
@Composable
internal fun LedgerWaitingIndicator(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showSpinner: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSpinner) {
            Spinner()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                modifier =
                    Modifier
                        .widthIn(max = WAITING_TEXT_MAX_WIDTH.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                text = title,
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    modifier = Modifier.widthIn(max = WAITING_TEXT_MAX_WIDTH.dp),
                    text = subtitle,
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition(label = "LedgerWaitingSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SPINNER_TURN_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "LedgerWaitingSpinnerRotation",
    )
    Image(
        modifier =
            Modifier
                .size(24.dp)
                .rotate(rotation),
        painter = painterResource(R.drawable.ic_ledger_loading),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary),
    )
}

private const val FULL_TURN = 360f

private const val SPINNER_TURN_MILLIS = 1000

private const val WAITING_TEXT_MAX_WIDTH = 280
