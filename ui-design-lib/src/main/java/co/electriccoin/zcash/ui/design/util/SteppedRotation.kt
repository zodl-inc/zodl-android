package co.electriccoin.zcash.ui.design.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Rotates the element in discrete jumps (e.g. a mechanical/ticking loading-icon look) rather than
 * a smooth continuous spin — the angle snaps by [stepDegrees] every [stepDurationMs].
 */
fun Modifier.steppedRotation(stepDegrees: Float = 45f, stepDurationMs: Long = 100L): Modifier =
    composed {
        var angle by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(stepDegrees, stepDurationMs) {
            while (true) {
                delay(stepDurationMs)
                angle = (angle + stepDegrees) % 360f
            }
        }
        graphicsLayer { rotationZ = angle }
    }
