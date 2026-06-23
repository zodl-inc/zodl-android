package co.electriccoin.zcash.ui.screen.swap.slippage

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextField
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextFieldDefaults
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.SuffixVisualTransformation
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import java.math.BigDecimal

@Suppress("CyclomaticComplexMethod")
@Composable
fun SlippagePicker(
    state: SlippagePickerState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    var textFieldInnerState by remember { mutableStateOf(createTextFieldInnerState(state.amount)) }
    val textFieldInteractionSource = remember { MutableInteractionSource() }
    val isTextFieldFocused by textFieldInteractionSource.collectIsFocusedAsState()
    LaunchedEffect(isTextFieldFocused) {
        if (isTextFieldFocused &&
            state.amount in
            listOf(BigDecimal("0.5"), BigDecimal(1), BigDecimal(2))
        ) {
            runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        }
    }
    val selection by remember(state.amount, isTextFieldFocused) {
        mutableStateOf(
            if (isTextFieldFocused) {
                Selection.ByTextField(textFieldInnerState.amount)
            } else {
                when (state.amount) {
                    BigDecimal("0.5") -> Selection.ByButton1
                    BigDecimal(1) -> Selection.ByButton2
                    BigDecimal(2) -> Selection.ByButton3
                    else -> Selection.ByTextField(state.amount)
                }
            }
        )
    }

    LaunchedEffect(isTextFieldFocused) {
        if (isTextFieldFocused) {
            state.onAmountChange(textFieldInnerState.amount)
        }
    }

    val textFieldState by remember {
        derivedStateOf {
            NumberTextFieldState(
                innerState = textFieldInnerState,
                onValueChange = {
                    textFieldInnerState = it
                    state.onAmountChange(it.amount)
                }
            )
        }
    }

    Surface(
        color = ZashiColors.Switcher.surfacePrimary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        modifier = modifier
    ) {
        RowWithSameWidthItems(
            modifier = Modifier.padding(2.dp),
            indicator = { height, width ->
                val index =
                    when {
                        selection is Selection.ByButton1 && !isTextFieldFocused -> 0
                        selection is Selection.ByButton2 && !isTextFieldFocused -> 1
                        selection is Selection.ByButton3 && !isTextFieldFocused -> 2
                        else -> 3
                    }
                Indicator(height, width, index)
            }
        ) {
            Button(
                text = (stringResByNumber(BigDecimal("0.5"), minDecimals = 0) + stringRes("%")).getValue(),
                isSelected = selection is Selection.ByButton1 && !isTextFieldFocused,
                onClick = {
                    if (selection !is Selection.ByButton1 || isTextFieldFocused) {
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                    }

                    focusManager.clearFocus(true)
                    textFieldInnerState = createTextFieldInnerState(null)
                    state.onAmountChange(BigDecimal("0.5"))
                }
            )
            Button(
                text = (stringResByNumber(1, minDecimals = 0) + stringRes("%")).getValue(),
                isSelected = selection is Selection.ByButton2 && !isTextFieldFocused,
                onClick = {
                    if (selection !is Selection.ByButton2 || isTextFieldFocused) {
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                    }
                    focusManager.clearFocus(true)
                    textFieldInnerState = createTextFieldInnerState(null)
                    state.onAmountChange(BigDecimal(1))
                }
            )
            Button(
                text = (stringResByNumber(2, minDecimals = 0) + stringRes("%")).getValue(),
                isSelected = selection is Selection.ByButton3 && !isTextFieldFocused,
                onClick = {
                    if (selection !is Selection.ByButton3 || isTextFieldFocused) {
                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                    }
                    focusManager.clearFocus(true)
                    textFieldInnerState = createTextFieldInnerState(null)
                    state.onAmountChange(BigDecimal(2))
                }
            )
            ZashiNumberTextField(
                modifier = Modifier.height(40.dp),
                state = textFieldState,
                interactionSource = textFieldInteractionSource,
                textStyle = ZashiNumberTextFieldDefaults.textStyle.copy(textAlign = TextAlign.Center),
                colors =
                    ZashiTextFieldDefaults.defaultColors(
                        borderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        placeholderColor = ZashiColors.Switcher.defaultText,
                        textColor = ZashiColors.Switcher.selectedText,
                        errorTextColor = ZashiColors.Inputs.ErrorDefault.stroke,
                        errorContainerColor = Color.Transparent,
                    ),
                placeholder =
                    if (!isTextFieldFocused) {
                        {
                            ZashiAutoSizeText(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp),
                                text = stringResource(R.string.swapAndPay_custom),
                                style = ZashiTypography.textMd,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = ZashiColors.Switcher.defaultText,
                                contentAlignment = Alignment.Center,
                                maxLines = 1
                            )
                        }
                    } else {
                        null
                    },
                visualTransformation =
                    if (isTextFieldFocused || selection is Selection.ByTextField) {
                        SuffixVisualTransformation("%")
                    } else {
                        VisualTransformation.None
                    },
                contentPadding = PaddingValues(top = 16.dp),
            )
        }
    }
}

private fun createTextFieldInnerState(amount: BigDecimal?): NumberTextFieldInnerState {
    val text =
        when (amount) {
            null -> stringRes("")
            BigDecimal("0.5") -> stringRes("")
            BigDecimal(1) -> stringRes("")
            BigDecimal(2) -> stringRes("")
            else -> stringResByNumber(amount, minDecimals = 0)
        }

    val newAmount =
        when (amount) {
            null -> null
            BigDecimal("0.5") -> null
            BigDecimal(1) -> null
            BigDecimal(2) -> null
            else -> amount
        }

    return NumberTextFieldInnerState(
        innerTextFieldState =
            InnerTextFieldState(
                value = text,
                selection = TextSelection.Start
            ),
        amount = newAmount,
        lastValidAmount = newAmount
    )
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
            subcompose("create_pleaceables", content).map { measurable ->
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
private fun Button(
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
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
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

private sealed interface Selection {
    data object ByButton1 : Selection

    data object ByButton2 : Selection

    data object ByButton3 : Selection

    data class ByTextField(
        val bigDecimal: BigDecimal?
    ) : Selection
}

data class SlippagePickerState(
    val amount: BigDecimal? = BigDecimal("0.5"),
    val onAmountChange: (BigDecimal?) -> Unit
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        var amount: BigDecimal? by remember { mutableStateOf(BigDecimal("0.5")) }
        BlankSurface {
            SlippagePicker(
                state =
                    SlippagePickerState(
                        amount = amount,
                        onAmountChange = { amount = it }
                    ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
