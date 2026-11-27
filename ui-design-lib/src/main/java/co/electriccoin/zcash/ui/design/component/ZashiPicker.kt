package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.PressMorphDefaults
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.pressMorph
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ZashiPicker(
    state: PickerState,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (state.isEnabled) ZashiColors.Dropdowns.Default.bg else ZashiColors.Dropdowns.Disabled.bg
    )
    val borderColor = if (state.isEnabled) Color.Unspecified else ZashiColors.Inputs.Disabled.stroke

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.pressMorph(interactionSource, PressMorphDefaults.PRESSED_SCALE_SUBTLE),
        onClick = { if (state.isEnabled) state.onClick() },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusLg),
        color = bgColor,
        border = if (borderColor.isUnspecified) null else BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.bigIcon is ImageResource.ByDrawable) {
                Box {
                    Image(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(state.bigIcon.resource),
                        contentDescription = null,
                    )
                    if (state.smallIcon is ImageResource.ByDrawable) {
                        Image(
                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(3.dp, 3.dp),
                            painter = painterResource(state.smallIcon.resource),
                            contentDescription = null,
                        )
                    }
                }
                Spacer(8.dp)
            }

            if (state.text != null) {
                val textColor by animateColorAsState(
                    if (state.isEnabled) {
                        ZashiColors.Dropdowns.Filled.textMain
                    } else {
                        ZashiColors.Dropdowns.Disabled.textMain
                    }
                )

                Text(
                    text = state.text.getValue(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            } else {
                val textColor by animateColorAsState(
                    if (state.isEnabled) {
                        ZashiColors.Dropdowns.Default.text
                    } else {
                        ZashiColors.Dropdowns.Disabled.textMain
                    }
                )

                Text(
                    text = state.placeholder.getValue(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }

            Spacer(1f)

            val tintColor = if (state.isEnabled) Color.Unspecified else ZashiColors.Dropdowns.Disabled.icon

            Image(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                colorFilter = if (tintColor.isSpecified) ColorFilter.tint(tintColor) else null
            )
        }
    }
}

data class PickerState(
    val bigIcon: ImageResource?,
    val smallIcon: ImageResource?,
    val text: StringResource?,
    val placeholder: StringResource,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZashiPicker(
                state =
                    PickerState(
                        bigIcon = imageRes(R.drawable.ic_item_keystone),
                        smallIcon = imageRes(R.drawable.ic_item_keystone),
                        text = stringRes("Text"),
                        placeholder = stringRes("Placeholder"),
                        onClick = {}
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun PlaceholderPreview() =
    ZcashTheme {
        BlankSurface {
            ZashiPicker(
                state =
                    PickerState(
                        bigIcon = null,
                        smallIcon = null,
                        text = null,
                        placeholder = stringRes("Placeholder..."),
                        onClick = {}
                    )
            )
        }
    }
