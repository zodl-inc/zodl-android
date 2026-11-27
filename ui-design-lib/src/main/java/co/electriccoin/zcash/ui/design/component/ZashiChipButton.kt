package co.electriccoin.zcash.ui.design.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.pressMorph
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ZashiChipButton(
    state: ChipButtonState,
    modifier: Modifier = Modifier,
    useTint: Boolean = true,
    shape: RoundedCornerShape = ZashiChipButtonDefaults.shape,
    border: BorderStroke? = ZashiChipButtonDefaults.border,
    color: Color = ZashiChipButtonDefaults.color,
    contentPadding: PaddingValues = ZashiChipButtonDefaults.contentPadding,
    textStyle: TextStyle = ZashiChipButtonDefaults.textStyle,
    endIconSpacer: Dp = ZashiChipButtonDefaults.endIconSpacer,
) {
    val normalizedColor by animateColorAsState(color)
    val normalizedTextColor by animateColorAsState(textStyle.color)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.pressMorph(interactionSource),
        shape = shape,
        border = border,
        color = normalizedColor,
    ) {
        Row(
            modifier =
                Modifier.clickable(
                    onClick = state.onClick,
                    enabled = state.isEnabled,
                    interactionSource = interactionSource,
                    indication = ripple()
                ) then
                    Modifier.padding
                        (contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.startIcon != null) {
                Image(
                    painterResource(state.startIcon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ZashiColors.Btns.Tertiary.btnTertiaryFg)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = state.text.getValue(),
                style = textStyle,
                color = normalizedTextColor
            )
            if (state.endIcon != null) {
                Spacer(Modifier.width(endIconSpacer))
                Image(
                    painterResource(state.endIcon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ZashiColors.Btns.Tertiary.btnTertiaryFg).takeIf { useTint }
                )
            }
        }
    }
}

data class ChipButtonState(
    val text: StringResource,
    @param:DrawableRes val startIcon: Int? = null,
    @param:DrawableRes val endIcon: Int? = null,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit,
)

object ZashiChipButtonDefaults {
    val shape: RoundedCornerShape
        get() = RoundedCornerShape(10.dp)
    val border: BorderStroke?
        get() = null
    val color: Color
        @Composable get() = ZashiColors.Btns.Tertiary.btnTertiaryBg
    val contentPadding: PaddingValues
        get() = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val textStyle: TextStyle
        @Composable get() =
            ZashiTypography.textSm.copy(
                color = ZashiColors.Btns.Tertiary.btnTertiaryFg,
                fontWeight = FontWeight.SemiBold
            )
    val endIconSpacer: Dp
        get() = 4.dp
}

@PreviewScreens
@Composable
private fun ZashiChipButtonPreview() =
    ZcashTheme {
        ZashiChipButton(
            state =
                ChipButtonState(
                    startIcon = R.drawable.ic_radio_button_checked,
                    text = stringRes("Test"),
                    onClick = {}
                )
        )
    }

@PreviewScreens
@Composable
private fun ZashiChipButtonEndIconPreview() =
    ZcashTheme {
        ZashiChipButton(
            state =
                ChipButtonState(
                    endIcon = R.drawable.ic_close,
                    text = stringRes("End Icon Chip"),
                    onClick = {}
                )
        )
    }
