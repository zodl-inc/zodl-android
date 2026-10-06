package co.electriccoin.zcash.ui.screen.connectledger.scan

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * The Figma "Advanced options" card of step 2: a header that expands and collapses the card, then
 * the explanation and the account index field with its label and hint.
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
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
        if (state.isExpanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = state.message.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(16.dp)
                Text(
                    text = state.accountIndexLabel.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Inputs.Default.label,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(6.dp)
                ZashiTextField(
                    state = state.accountIndex,
                    modifier = Modifier.fillMaxWidth(),
                    innerModifier =
                        ZashiTextFieldDefaults.innerModifier
                            .testTag(LedgerDeviceScanTag.ACCOUNT_INDEX_FIELD),
                    singleLine = true,
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
                    style = ZashiTypography.textXs,
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
