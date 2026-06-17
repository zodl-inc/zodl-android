@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package co.electriccoin.zcash.ui.screen.addressbook

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.compose.ZashiTooltipBox
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiTextButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddressBookPopup(
    tooltipState: TooltipState,
    state: AddressBookState,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit
) {
    ZashiTooltipBox(
        modifier = modifier,
        state = tooltipState,
        tooltip = { Tooltip(state = state, onDismissRequest = { tooltipState.dismiss() }) },
        anchor = anchor
    )
}

@Composable
private fun Tooltip(
    state: AddressBookState,
    onDismissRequest: () -> Unit
) {
    Surface(
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(8.dp),
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Material3 TooltipBox renders in a separate Compose Popup
            // window — re-enable testTagsAsResourceId inline so testTags
            // surface as resource-ids inside the popup.
            TextButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            testTagsAsResourceId = true
                            testTag = AddressBookTag.MANUAL_ENTRY
                        },
                state = state.manualButton,
                res = R.drawable.ic_add_contact_manual,
                onDismissRequest = onDismissRequest
            )
            TextButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            testTagsAsResourceId = true
                            testTag = AddressBookTag.SCAN_ENTRY
                        },
                state = state.scanButton,
                res = R.drawable.ic_add_contact_qr,
                onDismissRequest = onDismissRequest
            )
        }
    }
}

@Composable
private fun TextButton(
    state: ButtonState,
    @DrawableRes res: Int,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZashiTextButton(
        modifier = modifier,
        onClick = {
            onDismissRequest()
            state.onClick()
        }
    ) {
        Spacer(modifier = Modifier.width(4.dp))
        Image(
            painter = painterResource(res),
            contentDescription = state.text.getValue(),
            colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = state.text.getValue(),
            style = ZashiTypography.textMd,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary
        )
    }
}

@PreviewScreens
@Composable
private fun PopupContentPreview() =
    ZcashTheme {
        Tooltip(
            state =
                AddressBookState(
                    onBack = {},
                    isLoading = false,
                    items = emptyList(),
                    scanButton =
                        ButtonState(
                            text = stringRes("Scan QR code"),
                        ),
                    manualButton =
                        ButtonState(
                            text = stringRes("Manual entry"),
                        ),
                    title = stringRes("Address book"),
                    info = null
                ),
            onDismissRequest = {}
        )
    }
