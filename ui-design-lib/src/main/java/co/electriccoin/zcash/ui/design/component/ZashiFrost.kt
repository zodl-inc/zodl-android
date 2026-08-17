package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private const val FROST_TINT_ALPHA = .55f

private val FROST_BLUR_RADIUS = 20.dp

/**
 * Creates the state which connects a frosted header to the content scrolling underneath it. Pass the
 * same instance to [zashiFrostedHeader] and [zashiFrostSource].
 */
@Composable
fun rememberZashiFrostState(): HazeState = rememberHazeState()

/**
 * Renders a frosted header: the content behind it, blurred and tinted with
 * [ZashiColors.Surfaces.bgPrimary], fading out towards the bottom edge of the header.
 *
 * Blur is only available on devices where Haze can run it (see [HazeState.blurEnabled], which
 * defaults to `HazeDefaults.blurEnabled()`). Everywhere else Haze would degrade to a translucent
 * scrim, which combined with the vertical fade would let list rows show through the header
 * unblurred, so the header instead becomes an opaque [ZashiColors.Surfaces.bgPrimary] bar.
 */
@Composable
fun Modifier.zashiFrostedHeader(hazeState: HazeState): Modifier {
    val frostColor = ZashiColors.Surfaces.bgPrimary
    return if (hazeState.blurEnabled) {
        hazeEffect(
            state = hazeState,
            style =
                HazeStyle(
                    backgroundColor = frostColor,
                    tint = HazeTint(frostColor.copy(alpha = FROST_TINT_ALPHA)),
                    blurRadius = FROST_BLUR_RADIUS,
                    noiseFactor = HazeDefaults.noiseFactor
                )
        ) {
            progressive =
                HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
        }
    } else {
        background(frostColor)
    }
}

/**
 * Marks this content as the source blurred by a [zashiFrostedHeader] using the same [hazeState].
 * Applied only when blur is enabled, because recording the content into a graphics layer every frame
 * costs the same whether or not any header consumes it.
 */
@Composable
fun Modifier.zashiFrostSource(hazeState: HazeState): Modifier =
    if (hazeState.blurEnabled) {
        hazeSource(hazeState)
    } else {
        this
    }
