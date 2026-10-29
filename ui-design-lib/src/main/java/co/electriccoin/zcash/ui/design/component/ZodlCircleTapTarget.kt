package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.util.pressMorph

/** A 48 dp circular tap target centring [content], with the design system's press-morph and ripple. */
@Composable
internal fun ZodlCircleTapTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .pressMorph(interactionSource)
                .size(TAP_TARGET_SIZE)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = TAP_TARGET_SIZE / 2),
                    enabled = isEnabled,
                    role = Role.Button,
                    onClickLabel = onClickLabel,
                    onClick = onClick
                ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

internal val TAP_TARGET_SIZE = 48.dp
