package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Creates the state which connects a frosted bar to the content scrolling underneath it. Pass the
 * same instance to [zashiFrostedHeader] / [zashiFrostedFooter] and [zashiFrostSource].
 */
@Composable
fun rememberZashiFrostState(): HazeState = rememberHazeState()

/**
 * Renders a frosted header: the content behind it, blurred and tinted with [frostColor], fading out
 * towards the bottom edge of the header.
 *
 * Pass [Color.Transparent] as [frostColor] on screens which paint their own background behind the
 * frost source (a gradient scaffold, for instance): the tint is scaled by the frost color's own
 * alpha, so a transparent one degenerates to pure blur and at rest the header paints nothing,
 * letting that background show through untouched.
 *
 * Blur is only available on devices where Haze can run it (see [HazeState.blurEnabled], which
 * defaults to `HazeDefaults.blurEnabled()`). Everywhere else Haze would degrade to a translucent
 * scrim, which combined with the vertical fade would let list rows show through the header
 * unblurred, so the header instead becomes an opaque bar. That fallback bar is always
 * [ZashiColors.Surfaces.bgPrimary] and deliberately ignores [frostColor] — a transparent fallback
 * would show the content unblurred on those devices.
 */
@Composable
fun Modifier.zashiFrostedHeader(
    hazeState: HazeState,
    frostColor: Color = ZashiColors.Surfaces.bgPrimary
): Modifier =
    zashiFrost(
        hazeState = hazeState,
        frostColor = frostColor,
        startIntensity = 1f,
        endIntensity = 0f
    )

/**
 * Renders a frosted footer: the mirror image of [zashiFrostedHeader], fading in from the top edge of
 * the footer to full frost at its bottom edge, so the content scrolling underneath blurs away as it
 * approaches the bottom of the screen.
 *
 * [frostColor] behaves exactly as in [zashiFrostedHeader], including the opaque
 * [ZashiColors.Surfaces.bgPrimary] fallback bar where blur is unsupported.
 */
@Composable
fun Modifier.zashiFrostedFooter(
    hazeState: HazeState,
    frostColor: Color = ZashiColors.Surfaces.bgPrimary
): Modifier =
    zashiFrost(
        hazeState = hazeState,
        frostColor = frostColor,
        startIntensity = 0f,
        endIntensity = 1f
    )

@Composable
private fun Modifier.zashiFrost(
    hazeState: HazeState,
    frostColor: Color,
    startIntensity: Float,
    endIntensity: Float
): Modifier =
    if (hazeState.blurEnabled) {
        hazeEffect(
            state = hazeState,
            style =
                HazeStyle(
                    backgroundColor = frostColor,
                    tint = HazeTint(frostColor.copy(alpha = frostColor.alpha * FROST_TINT_ALPHA)),
                    blurRadius = FROST_BLUR_RADIUS,
                    noiseFactor = HazeDefaults.noiseFactor
                )
        ) {
            progressive =
                HazeProgressive.verticalGradient(
                    startIntensity = startIntensity,
                    endIntensity = endIntensity
                )
        }
    } else {
        background(ZashiColors.Surfaces.bgPrimary)
    }

/**
 * Marks this content as the source blurred by a [zashiFrostedHeader] or [zashiFrostedFooter] using
 * the same [hazeState]. Applied only when blur is enabled, because recording the content into a
 * graphics layer every frame costs the same whether or not any bar consumes it.
 */
@Composable
fun Modifier.zashiFrostSource(hazeState: HazeState): Modifier =
    if (hazeState.blurEnabled) {
        hazeSource(hazeState)
    } else {
        this
    }
