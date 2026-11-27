package co.electriccoin.zcash.ui.design.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Scales the whole composable down with a spring while [interactionSource] reports a press, and springs it back on
 * release.
 *
 * Place this at the front of the component-internal modifier chain, i.e. immediately after the caller's `modifier`,
 * so that the entire visual - background, border, ripple and content - scales as a single layer.
 *
 * @param pressedScale the scale the composable shrinks to while pressed; use
 * [PressMorphDefaults.PRESSED_SCALE_SUBTLE] for full-width rows and cards.
 */
@Composable
fun Modifier.pressMorph(
    interactionSource: InteractionSource,
    pressedScale: Float = PressMorphDefaults.PRESSED_SCALE
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else PressMorphDefaults.UNPRESSED_SCALE,
        animationSpec = if (isPressed) PressMorphDefaults.pressSpring else PressMorphDefaults.releaseSpring,
        label = "pressMorphScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

object PressMorphDefaults {
    const val UNPRESSED_SCALE = 1f

    const val PRESSED_SCALE = 0.97f

    const val PRESSED_SCALE_SUBTLE = 0.985f

    val pressSpring: AnimationSpec<Float> =
        spring(
            stiffness = Spring.StiffnessHigh,
            dampingRatio = Spring.DampingRatioMediumBouncy
        )

    val releaseSpring: AnimationSpec<Float> =
        spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        )
}
