package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.UntrimmedTextSm
import co.electriccoin.zcash.ui.screen.connectledger.common.UntrimmedTextXs
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * The Figma "Advanced options" card of step 2: a header that expands and collapses the card, then
 * the explanation and the account index field with its label and hint. The body opens and closes
 * the way the rows of the transaction details do, growing down from the header. The header
 * announces whether the card is open through the expand and collapse accessibility actions.
 */
@Composable
internal fun LedgerAdvancedOptions(
    state: LedgerAdvancedOptionsState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZashiColors.Surfaces.bgPrimary)
                .border(1.dp, ZashiColors.Surfaces.strokeSecondary, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = state.onToggle)
                    .semantics {
                        if (state.isExpanded) {
                            collapse {
                                state.onToggle()
                                true
                            }
                        } else {
                            expand {
                                state.onToggle()
                                true
                            }
                        }
                    }.padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag(LedgerDeviceScanTag.ADVANCED_OPTIONS_HEADER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = state.title.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                modifier = Modifier.size(20.dp),
                painter =
                    painterResource(
                        if (state.isExpanded) DesignR.drawable.ic_chevron_up else DesignR.drawable.ic_chevron_down
                    ),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
            )
        }
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = state.message.getValue(),
                    style = UntrimmedTextXs,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(16.dp)
                Text(
                    text = state.accountIndexLabel.getValue(),
                    style = UntrimmedTextSm,
                    color = ZashiColors.Inputs.Default.label,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(6.dp)
                ZashiTextField(
                    state = state.accountIndex,
                    modifier = Modifier.fillMaxWidth(),
                    innerModifier =
                        ZashiTextFieldDefaults.innerModifier
                            .errorRing(state.accountIndex.isError, ZashiColors.Utility.ErrorRed.utilityError200)
                            .testTag(LedgerDeviceScanTag.ACCOUNT_INDEX_FIELD),
                    textStyle = ZashiTypography.textSm,
                    singleLine = true,
                    shape = RoundedCornerShape(FIELD_RADIUS.dp),
                    colors =
                        ZashiTextFieldDefaults.defaultColors(
                            errorBorderColor = ZashiColors.Inputs.ErrorFilled.stroke,
                        ),
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                )
                Spacer(6.dp)
                Text(
                    text = state.hint.getValue(),
                    style = UntrimmedTextXs,
                    color =
                        if (state.accountIndex.isError) {
                            ZashiColors.Inputs.ErrorDefault.hint
                        } else {
                            ZashiColors.Inputs.Default.hint
                        },
                )
            }
        }
    }
}

/**
 * Figma's error state rings the field with a 2 dp "Focus Rings/Inner Error" shadow outside its
 * stroke. It is drawn behind the field and outside its bounds, so it takes no layout space and the
 * field does not move when the error comes and goes.
 */
private fun Modifier.errorRing(
    isError: Boolean,
    color: Color,
) = if (isError) {
    drawBehind {
        val width = ERROR_RING_WIDTH.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-width / 2, -width / 2),
            size = Size(size.width + width, size.height + width),
            cornerRadius = CornerRadius(FIELD_RADIUS.dp.toPx() + width / 2),
            style = Stroke(width),
        )
    }
} else {
    this
}

private const val FIELD_RADIUS = 10

private const val ERROR_RING_WIDTH = 2

@PreviewScreens
@Composable
private fun CollapsedPreview() =
    ZcashTheme {
        LedgerAdvancedOptions(state = LedgerAdvancedOptionsState.preview)
    }

@PreviewScreens
@Composable
private fun ExpandedPreview() =
    ZcashTheme {
        LedgerAdvancedOptions(state = LedgerAdvancedOptionsState.previewExpanded)
    }

@PreviewScreens
@Composable
private fun ErrorPreview() =
    ZcashTheme {
        LedgerAdvancedOptions(state = LedgerAdvancedOptionsState.previewError)
    }
