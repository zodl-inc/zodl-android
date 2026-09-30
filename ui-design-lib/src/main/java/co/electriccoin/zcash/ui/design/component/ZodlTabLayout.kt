package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * A generic switcher of equally wide cells with an animated selection indicator, the same visual
 * as the slippage picker's preset row.
 */
@Composable
fun ZodlTabLayout(
    items: List<SegmentedControlItem>,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = items.indexOfFirst { it.isSelected }
    Surface(
        color = ZashiColors.Switcher.surfacePrimary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        modifier = modifier
    ) {
        RowWithSameWidthItems(
            modifier = Modifier.padding(2.dp),
            indicator = { height, width -> Indicator(height, width, selectedIndex) }
        ) {
            items.forEach { item ->
                Cell(
                    text = item.text.getValue(),
                    isSelected = item.isSelected,
                    onClick = {
                        if (!item.isSelected) {
                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                        }
                        item.onClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun RowWithSameWidthItems(
    indicator: @Composable (Dp, Dp) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val itemCount = subcompose("find_count", content).count()
        val maxWidth = constraints.maxWidth
        val itemWidth = maxWidth / itemCount
        val maxHeight =
            subcompose("measure_height", content)
                .map { measurable ->
                    measurable.measure(
                        constraints.copy(
                            minWidth = itemWidth,
                            maxWidth = itemWidth
                        )
                    )
                }.maxOf { it.height }
        val placeables =
            subcompose("create_placeables", content).map { measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = itemWidth,
                        maxWidth = itemWidth,
                        minHeight = maxHeight,
                        maxHeight = maxHeight
                    )
                )
            }
        val indicatorPlaceable =
            subcompose("indicator") {
                indicator(
                    maxHeight.toDp(),
                    itemWidth.toDp()
                )
            }[0].measure(
                constraints.copy(
                    minWidth = itemWidth,
                    maxWidth = itemWidth,
                    minHeight = maxHeight,
                    maxHeight = maxHeight
                )
            )

        layout(width = constraints.maxWidth, height = maxHeight) {
            indicatorPlaceable.placeRelative(x = 0, y = 0, zIndex = .1f)
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = index * itemWidth, y = 0, zIndex = 1f)
            }
        }
    }
}

@Composable
private fun Cell(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color by animateColorAsState(
            if (isSelected) ZashiColors.Switcher.selectedText else ZashiColors.Switcher.defaultText
        )

        Text(
            text = text,
            style = ZashiTypography.textMd,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun Indicator(
    height: Dp,
    width: Dp,
    selectionIndex: Int,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
    ) {
        val finalOffset by animateDpAsState(
            targetValue = width * selectionIndex,
            animationSpec = tween(durationMillis = INDICATOR_ANIMATION_MILLIS, easing = FastOutSlowInEasing)
        )

        Box(
            modifier =
                Modifier
                    .height(height)
                    .width(width)
                    .offset(x = finalOffset)
                    .background(
                        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusLg),
                        color = ZashiColors.Switcher.selectedBg,
                    ).border(
                        border = BorderStroke(1.dp, ZashiColors.Switcher.selectedStroke),
                        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusLg)
                    )
        )
    }
}

private const val INDICATOR_ANIMATION_MILLIS = 350

data class SegmentedControlItem(
    val text: StringResource,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        var selected by remember { mutableIntStateOf(0) }
        BlankSurface {
            ZodlTabLayout(
                items =
                    listOf(
                        SegmentedControlItem(
                            text = stringRes("QR Code"),
                            isSelected = selected == 0,
                            onClick = { selected = 0 }
                        ),
                        SegmentedControlItem(
                            text = stringRes("Key String"),
                            isSelected = selected == 1,
                            onClick = { selected = 1 }
                        ),
                    ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
