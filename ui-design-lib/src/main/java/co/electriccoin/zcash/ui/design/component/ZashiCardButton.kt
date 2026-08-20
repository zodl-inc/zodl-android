package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.PressMorphDefaults
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.pressMorph
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ZashiCardButton(
    state: ButtonState,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier =
            modifier
                .pressMorph(interactionSource, PressMorphDefaults.PRESSED_SCALE_SUBTLE)
                .clickable(
                    enabled = state.isEnabled,
                    interactionSource = interactionSource,
                    indication = ripple()
                ) {
                    if (state.hapticFeedbackType != null) {
                        runCatching { haptic.performHapticFeedback(state.hapticFeedbackType) }
                    }
                    state.onClick()
                },
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.icon != null) {
                Image(
                    painter = painterResource(state.icon),
                    contentDescription = null,
                )
            }

            Text(
                text = state.text.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                modifier = Modifier.weight(1f)
            )

            if (state.trailingIcon != null) {
                Image(
                    painter = painterResource(state.trailingIcon),
                    contentDescription = null,
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZashiCardButton(
                state =
                    ButtonState(
                        text = stringRes("Switch server"),
                        icon = android.R.drawable.ic_menu_info_details,
                        trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                        onClick = {}
                    )
            )
        }
    }
