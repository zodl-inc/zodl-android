package co.electriccoin.zcash.ui.design.component.listitem.checkbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiCheckboxIndicator
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDefaults
import co.electriccoin.zcash.ui.design.component.listitem.clickableModifier
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.PressMorphDefaults
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.pressMorph
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle

@Composable
fun ZashiExpandedCheckboxListItem(
    state: ZashiExpandedCheckboxListItemState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    ExpandedBaseListItem(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        leading = {
            ZashiListItemDefaults.LeadingItem(
                modifier = it,
                icon = imageRes(state.icon),
                badge = null,
                contentDescription = state.title.getValue()
            )
        },
        content = {
            Column(
                modifier = it
            ) {
                Row {
                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.SemiBold,
                        color = ZashiColors.Text.textPrimary
                    )
                }
                Text(
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary
                )
            }
        },
        trailing = {
            ZashiCheckboxIndicator(state.isSelected)
        },
        below = {
            state.info?.let {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = it.title.getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it.subtitle.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
            }
        },
        border =
            BorderStroke(
                1.dp,
                if (state.isSelected) {
                    ZashiColors.Surfaces.bgAlt
                } else {
                    ZashiColors.Surfaces.strokeSecondary
                }
            ),
        onClick =
            if (state.hapticFeedbackType == null) {
                state.onClick
            } else {
                {
                    runCatching { haptic.performHapticFeedback(state.hapticFeedbackType) }
                    state.onClick()
                }
            },
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun ExpandedBaseListItem(
    shape: Shape,
    contentPadding: PaddingValues,
    onClick: (() -> Unit)?,
    leading: @Composable (Modifier) -> Unit,
    trailing: @Composable (Modifier) -> Unit,
    below: @Composable ColumnScope.(Modifier) -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.pressMorph(interactionSource, PressMorphDefaults.PRESSED_SCALE_SUBTLE),
        shape = shape,
        color = Color.Transparent,
        border = border,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                clickableModifier(interactionSource, onClick)
                    .padding(contentPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading(Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                content(Modifier.weight(1f))
                Spacer(modifier = Modifier.width(16.dp))
                trailing(Modifier)
            }
            below(Modifier)
        }
    }
}

data class ZashiExpandedCheckboxListItemState(
    val title: StringResource,
    val subtitle: StyledStringResource,
    val icon: Int,
    val isSelected: Boolean,
    val hapticFeedbackType: HapticFeedbackType? =
        if (isSelected) {
            HapticFeedbackType.ToggleOff
        } else {
            HapticFeedbackType.ToggleOn
        },
    val info: ZashiExpandedCheckboxRowState?,
    val onClick: () -> Unit
) : CheckboxListItemState

data class ZashiExpandedCheckboxRowState(
    val title: StringResource,
    val subtitle: StringResource,
)

@Composable
@PreviewScreens
private fun ExpandedPreviewChecked() =
    ZcashTheme {
        BlankSurface {
            ZashiExpandedCheckboxListItem(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ZashiExpandedCheckboxListItemState(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle").withStyle(),
                        icon = R.drawable.ic_radio_button_checked,
                        isSelected = true,
                        info =
                            ZashiExpandedCheckboxRowState(
                                title = stringRes("title"),
                                subtitle = stringRes("subtitle")
                            ),
                        onClick = {}
                    )
            )
        }
    }

@Composable
@PreviewScreens
private fun PreviewWithNoInfo() =
    ZcashTheme {
        BlankSurface {
            ZashiExpandedCheckboxListItem(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ZashiExpandedCheckboxListItemState(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle").withStyle(),
                        icon = R.drawable.ic_radio_button_checked,
                        isSelected = true,
                        info = null,
                        onClick = {}
                    )
            )
        }
    }

@Composable
@PreviewScreens
private fun ExpandedPreviewUnchecked() =
    ZcashTheme {
        BlankSurface {
            ZashiExpandedCheckboxListItem(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ZashiExpandedCheckboxListItemState(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle").withStyle(),
                        icon = R.drawable.ic_radio_button_checked,
                        isSelected = false,
                        info =
                            ZashiExpandedCheckboxRowState(
                                title = stringRes("title"),
                                subtitle = stringRes("subtitle")
                            ),
                        onClick = {}
                    )
            )
        }
    }
