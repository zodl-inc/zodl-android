package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

private const val FOOTER_BLUR_RAMP_FRACTION = .15f

private const val FOOTER_BLUR_RAMP_INTENSITY = .85f

private const val FOOTER_BLUR_PLATEAU_FRACTION = .5f

private val FooterBlurIntensityStops =
    listOf(
        0f to 0f,
        FOOTER_BLUR_RAMP_FRACTION to FOOTER_BLUR_RAMP_INTENSITY,
        FOOTER_BLUR_PLATEAU_FRACTION to 1f,
        1f to 1f
    )

/**
 * The footer's blur curve mirrored top-down: full blur from the top edge through the middle of the
 * header, then a steep ramp down to nothing at the bottom edge. Pass it to [zashiFrostedHeader] on
 * screens whose header holds content that has to stay readable over the list scrolling underneath.
 */
val HeaderBlurIntensityStops =
    listOf(
        0f to 1f,
        FOOTER_BLUR_PLATEAU_FRACTION to 1f,
        1f - FOOTER_BLUR_RAMP_FRACTION to FOOTER_BLUR_RAMP_INTENSITY,
        1f to 0f
    )

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
 * unblurred, so the header instead becomes an opaque bar painted in [fallbackColor], deliberately
 * ignoring [frostColor] — a transparent fallback would show the content unblurred on those
 * devices. Surfaces whose resting background is not bgPrimary (a bottom sheet, for instance) pass
 * their own background as [fallbackColor] so the bar disappears into them.
 *
 * By default the blur strength follows a plain two-point gradient, full at the top edge and gone at
 * the bottom one. Passing [intensityStops] switches it to a multi-stop curve instead, mapping a
 * fraction of the header's height (0f = top edge, 1f = bottom edge) to a blur intensity at that
 * line and interpolating linearly between stops; [HeaderBlurIntensityStops] holds the header
 * mirror of the footer's curve, which keeps a strong blur under the header's own content and only
 * ramps away just above its bottom edge. As in [zashiFrostedFooter], only the blur follows the
 * stops: the tint becomes a plain top-ramp gradient and the noise grain is dropped, so the frost
 * does not repaint the header's resting colors.
 */
@Composable
fun Modifier.zashiFrostedHeader(
    hazeState: HazeState,
    frostColor: Color = ZashiColors.Surfaces.bgPrimary,
    fallbackColor: Color = ZashiColors.Surfaces.bgPrimary,
    intensityStops: List<Pair<Float, Float>>? = null
): Modifier {
    if (intensityStops == null) {
        return zashiFrost(
            hazeState = hazeState,
            frostColor = frostColor,
            fallbackColor = fallbackColor,
            progressive =
                HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
        )
    }
    val tintColor = frostColor.copy(alpha = frostColor.alpha * FROST_TINT_ALPHA)
    return zashiFrost(
        hazeState = hazeState,
        frostColor = frostColor,
        fallbackColor = fallbackColor,
        progressive =
            HazeProgressive.Brush(
                Brush.verticalGradient(
                    colorStops =
                        intensityStops
                            .map { (fraction, intensity) -> fraction to Color.White.copy(alpha = intensity) }
                            .toTypedArray()
                )
            ),
        tint =
            HazeTint(
                Brush.verticalGradient(
                    0f to tintColor,
                    1f to tintColor.copy(alpha = 0f)
                )
            ),
        noiseFactor = 0f
    )
}

/**
 * Renders a frosted footer: the mirror image of [zashiFrostedHeader], fading in from the top edge of
 * the footer to full frost at its bottom edge, so the content scrolling underneath blurs away as it
 * approaches the bottom of the screen.
 *
 * [frostColor] and [fallbackColor] behave exactly as in [zashiFrostedHeader], including the opaque
 * fallback bar where blur is unsupported.
 *
 * Unlike the header, the footer's blur strength follows a multi-stop curve rather than a plain
 * two-point gradient: [intensityStops] maps a fraction of the footer's height (0f = top edge,
 * 1f = bottom edge) to a blur intensity at that line, interpolating linearly between stops. The
 * default ramps steeply to a strong blur just below the top edge, so text sitting at the top of a
 * footer stays readable over the content scrolling underneath.
 *
 * Only the blur follows the stops. The tint keeps a plain bottom-ramp gradient and the noise
 * grain is dropped entirely, so the frost does not repaint the footer's resting colors.
 */
@Composable
fun Modifier.zashiFrostedFooter(
    hazeState: HazeState,
    frostColor: Color = ZashiColors.Surfaces.bgPrimary,
    fallbackColor: Color = ZashiColors.Surfaces.bgPrimary,
    intensityStops: List<Pair<Float, Float>> = FooterBlurIntensityStops
): Modifier {
    val tintColor = frostColor.copy(alpha = frostColor.alpha * FROST_TINT_ALPHA)
    return zashiFrost(
        hazeState = hazeState,
        frostColor = frostColor,
        fallbackColor = fallbackColor,
        progressive =
            HazeProgressive.Brush(
                Brush.verticalGradient(
                    colorStops =
                        intensityStops
                            .map { (fraction, intensity) -> fraction to Color.White.copy(alpha = intensity) }
                            .toTypedArray()
                )
            ),
        tint =
            HazeTint(
                Brush.verticalGradient(
                    0f to tintColor.copy(alpha = 0f),
                    1f to tintColor
                )
            ),
        noiseFactor = 0f
    )
}

@Composable
private fun Modifier.zashiFrost(
    hazeState: HazeState,
    frostColor: Color,
    fallbackColor: Color,
    progressive: HazeProgressive,
    tint: HazeTint? = null,
    noiseFactor: Float = HazeDefaults.noiseFactor
): Modifier =
    if (hazeState.blurEnabled) {
        hazeEffect(
            state = hazeState,
            style =
                HazeStyle(
                    backgroundColor = frostColor,
                    tint = tint ?: HazeTint(frostColor.copy(alpha = frostColor.alpha * FROST_TINT_ALPHA)),
                    blurRadius = FROST_BLUR_RADIUS,
                    noiseFactor = noiseFactor
                )
        ) {
            this.progressive = progressive
        }
    } else {
        background(fallbackColor)
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
