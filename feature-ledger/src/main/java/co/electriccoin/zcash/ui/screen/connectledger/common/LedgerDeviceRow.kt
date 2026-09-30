package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * A selectable Ledger row, shared by the connect flow and the sign sheet. [testTag] is supplied by
 * the caller and must be positional: a device identifier never goes into the semantics tree. It is
 * a radio button, so its list is expected to be a `selectableGroup`; the selected state is announced
 * from its semantics, which leaves the check icon decorative.
 */
@Composable
internal fun LedgerDeviceRow(
    state: LedgerDeviceItemState,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectionShadow(state.isSelected)
                .clip(RoundedCornerShape(12.dp))
                .background(ZashiColors.Surfaces.bgPrimary)
                .selectionBorder(state.isSelected)
                .selectable(
                    selected = state.isSelected,
                    enabled = state.isEnabled,
                    role = Role.RadioButton,
                    onClick = state.onClick,
                ).padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(40.dp),
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_item_ledger),
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = state.name.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(16.dp))
        SelectionIndicator(isSelected = state.isSelected)
    }
}

/**
 * Figma lifts the selected row with its Shadow SM.
 */
private fun Modifier.selectionShadow(isSelected: Boolean) =
    if (isSelected) {
        this.shadow(SELECTED_ELEVATION.dp, RoundedCornerShape(12.dp))
    } else {
        this
    }

private const val SELECTED_ELEVATION = 2

/**
 * The selected row is drawn with a heavier, darker stroke; selection is carried by the border and
 * the filled selector together, as in the Figma frames.
 */
@Composable
private fun Modifier.selectionBorder(isSelected: Boolean) =
    this.then(
        Modifier.border(
            if (isSelected) {
                BorderStroke(2.dp, ZashiColors.Text.textPrimary)
            } else {
                BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary)
            },
            RoundedCornerShape(12.dp)
        )
    )

@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        ZashiColors.Checkboxes.boxOnBg
                    } else {
                        Color.Transparent
                    }
                ).border(
                    BorderStroke(
                        1.dp,
                        if (isSelected) {
                            ZashiColors.Checkboxes.boxOnBg
                        } else {
                            ZashiColors.Checkboxes.boxOffStroke
                        }
                    ),
                    CircleShape
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                modifier = Modifier.size(14.dp),
                painter = painterResource(R.drawable.ic_ledger_check),
                contentDescription = null,
                tint = ZashiColors.Checkboxes.boxOnFg,
            )
        }
    }
}

/**
 * A device row. It deliberately carries no identifier: the only one a scan has is the device's
 * Bluetooth address, a stable hardware identifier that must not reach the semantics tree. The view
 * keys and tags rows by position; the selection itself is tracked inside the view model.
 */
data class LedgerDeviceItemState(
    val name: StringResource,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val onClick: () -> Unit,
) {
    companion object {
        val preview =
            LedgerDeviceItemState(
                name = stringRes("Ledger Device 2"),
                isSelected = false,
                isEnabled = true,
                onClick = {},
            )

        val previewSelected = preview.copy(name = stringRes("Ledger Device 1"), isSelected = true)
    }
}
