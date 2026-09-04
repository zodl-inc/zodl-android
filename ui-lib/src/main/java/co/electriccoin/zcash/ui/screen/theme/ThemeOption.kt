package co.electriccoin.zcash.ui.screen.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ZashiRadioIndicator
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

private val CARD_SHAPE = RoundedCornerShape(16.dp)

private val RADIO_SIZE = 20.dp

/** Figma's `_Checkbox base` centers the checked dot in the circle at an exact 8/20 diameter ratio. */
private val RADIO_DOT_SIZE = 8.dp

private val UNCHECKED_BORDER_WIDTH = 1.dp

private val CHECKED_BORDER_WIDTH = 2.dp

private val UNCHECKED_RADIO_STROKE_WIDTH = 1.dp

/**
 * The System/Light/Dark and Classic Dark/Pure Black option card shared by [co.electriccoin.zcash.ui.screen.theme.settings.ThemeSettingsView]
 * and [co.electriccoin.zcash.ui.screen.theme.darklook.ThemeDarkLookView]. Distinct from
 * [co.electriccoin.zcash.ui.screen.exchangerate.settings.Option] - same title/subtitle/haptics shape, but its
 * own card and radio-indicator styling per the Figma theme spec, so the exchange-rate screens keep their
 * existing look untouched.
 */
@Composable
internal fun ThemeOption(
    isChecked: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val clickAction =
        remember(isChecked, onClick) {
            if (isChecked) {
                onClick
            } else {
                {
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                    onClick()
                }
            }
        }

    Row(
        modifier =
            modifier
                .clip(CARD_SHAPE)
                .background(ZashiColors.Surfaces.bgPrimary)
                .border(
                    width = if (isChecked) CHECKED_BORDER_WIDTH else UNCHECKED_BORDER_WIDTH,
                    color = if (isChecked) ZashiColors.Text.textPrimary else ZashiColors.Surfaces.strokeSecondary,
                    shape = CARD_SHAPE
                ).selectable(
                    selected = isChecked,
                    onClick = clickAction,
                    role = Role.RadioButton,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeRadioIndicator(isChecked = isChecked)
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@Composable
private fun ThemeRadioIndicator(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
) {
    ZashiRadioIndicator(
        isChecked = isChecked,
        checkedContent = {
            Box(
                modifier =
                    modifier
                        .size(RADIO_SIZE)
                        .clip(CircleShape)
                        .background(ZashiColors.Text.textPrimary),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(RADIO_DOT_SIZE)
                            .clip(CircleShape)
                            .background(ZashiColors.Surfaces.bgPrimary)
                )
            }
        },
        uncheckedContent = {
            Box(
                modifier =
                    modifier
                        .size(RADIO_SIZE)
                        .clip(CircleShape)
                        .border(UNCHECKED_RADIO_STROKE_WIDTH, ZashiColors.Checkboxes.boxOffStroke, CircleShape)
            )
        }
    )
}
