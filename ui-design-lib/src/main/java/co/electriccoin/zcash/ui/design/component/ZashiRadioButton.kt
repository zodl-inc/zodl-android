package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Suppress("LongParameterList", "LongMethod")
@Composable
fun ZashiRadioButton(
    state: RadioButtonState,
    modifier: Modifier = Modifier,
    isRippleEnabled: Boolean = true,
    checkedContent: @Composable () -> Unit = { RadioButtonCheckedContent(state) },
    uncheckedContent: @Composable () -> Unit = { RadioButtonUncheckedContent(state) },
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    testTag: String? = null,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    enabled = state.isEnabled,
                    indication = if (isRippleEnabled) ripple() else null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick =
                        if (state.hapticFeedbackType != null) {
                            {
                                runCatching { haptic.performHapticFeedback(state.hapticFeedbackType) }
                                state.onClick()
                            }
                        } else {
                            state.onClick
                        },
                    role = Role.Button,
                ).padding(horizontal = 20.dp)
                .then(
                    if (testTag != null) {
                        Modifier.testTag(testTag)
                    } else {
                        Modifier
                    }
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButtonIndicator(
                state = state,
                checkedContent = checkedContent,
                uncheckedContent = uncheckedContent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = state.text.getValue(),
                    style = ZashiTypography.textSm,
                    color =
                        if (state.isEnabled) {
                            ZashiColors.Text.textPrimary
                        } else {
                            ZashiColors.Text.textDisabled
                        },
                    modifier =
                        Modifier.padding(
                            top = 14.dp,
                            bottom = if (state.subtitle == null) 14.dp else 0.dp,
                            start = 0.dp,
                            end = ZcashTheme.dimens.spacingDefault
                        )
                )

                if (state.subtitle != null) {
                    Text(
                        text = state.subtitle.getValue(),
                        style = ZashiTypography.textSm,
                        color =
                            if (state.isEnabled) {
                                ZashiColors.Text.textTertiary
                            } else {
                                ZashiColors.Text.textDisabled
                            },
                        modifier =
                            Modifier.padding(
                                bottom = 6.dp,
                                start = 0.dp,
                                end = ZcashTheme.dimens.spacingDefault
                            )
                    )
                }
            }
        }
        if (trailingContent != null) {
            Row {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

@Composable
fun RadioButtonUncheckedContent(state: RadioButtonState) {
    Image(
        painter = painterResource(id = R.drawable.ic_radio_button_unchecked),
        contentDescription = state.text.getValue(),
    )
}

@Composable
fun RadioButtonCheckedContent(state: RadioButtonState) {
    Image(
        painter = painterResource(id = R.drawable.ic_radio_button_checked),
        contentDescription = state.text.getValue(),
    )
}

@Composable
private fun RadioButtonIndicator(
    state: RadioButtonState,
    checkedContent: @Composable () -> Unit,
    uncheckedContent: @Composable () -> Unit
) {
    Box {
        uncheckedContent()
        AnimatedVisibility(
            visible = state.isChecked,
            enter = scaleIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = scaleOut(spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy))
        ) {
            checkedContent()
        }
    }
}

data class RadioButtonState(
    val text: StringResource,
    val isChecked: Boolean,
    val isEnabled: Boolean = true,
    val hapticFeedbackType: HapticFeedbackType? =
        if (!isEnabled) {
            null
        } else if (isChecked) {
            HapticFeedbackType.ToggleOff
        } else {
            HapticFeedbackType.ToggleOn
        },
    val subtitle: StringResource? = null,
    val onClick: () -> Unit,
)

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun RadioButtonPreview() =
    ZcashTheme {
        BlankBgColumn {
            var isChecked by remember { mutableStateOf(false) }

            ZashiRadioButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    RadioButtonState(
                        text = stringRes("test"),
                        isChecked = isChecked,
                        onClick = { isChecked = !isChecked },
                    ),
                trailingContent = {
                    Text(text = "Trailing text")
                }
            )
            ZashiRadioButton(
                state =
                    RadioButtonState(
                        text = stringRes("test"),
                        isChecked = true,
                        onClick = {},
                    ),
            )
        }
    }
