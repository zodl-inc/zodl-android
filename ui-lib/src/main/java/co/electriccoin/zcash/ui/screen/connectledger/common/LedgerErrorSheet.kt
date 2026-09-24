package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.ZashiInScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerErrorSheet(state: LedgerErrorSheetState?) {
    ZashiInScreenModalBottomSheet(state = state) { sheetState ->
        LedgerErrorContent(
            state = sheetState,
            modifier = Modifier.testTag(LedgerDeviceScanTag.ERROR_SHEET),
        )
    }
}

/**
 * A locally controlled Ledger error sheet; only the icon, the copy and the buttons differ between
 * the cases in the Figma "Error States and Edge Cases" section.
 */
data class LedgerErrorSheetState(
    @get:DrawableRes
    override val icon: Int,
    override val title: StringResource,
    override val message: StringResource,
    override val primary: ButtonState,
    override val secondary: ButtonState?,
    override val onBack: () -> Unit,
) : LedgerErrorContentState,
    ModalBottomSheetState {
    companion object {
        val preview =
            LedgerErrorSheetState(
                icon = R.drawable.ic_ledger_alert_circle,
                title = stringRes("No Devices Found"),
                message =
                    stringRes(
                        "We couldn't find any Ledger devices nearby. Make sure your Ledger " +
                            "hardware is unlocked and Bluetooth is turned on."
                    ),
                primary = ButtonState(stringRes("Try again")),
                secondary = null,
                onBack = {},
            )

        val previewTwoButtons =
            LedgerErrorSheetState(
                icon = R.drawable.ic_ledger_alert_circle,
                title = stringRes("Account Already Added"),
                message = stringRes("This account is already connected to Zodl."),
                primary = ButtonState(stringRes("Go to Account")),
                secondary = ButtonState(stringRes("Cancel"), style = ButtonStyle.SECONDARY),
                onBack = {},
            )

        val previewBluetoothOff =
            LedgerErrorSheetState(
                icon = R.drawable.ic_ledger_bluetooth_off,
                title = stringRes("Bluetooth Is Off"),
                message = stringRes("Turn on Bluetooth in Settings to connect your Ledger."),
                primary = ButtonState(stringRes("Try again")),
                secondary = null,
                onBack = {},
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerErrorSheet(state = LedgerErrorSheetState.preview)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun TwoButtonsPreview() =
    ZcashTheme {
        LedgerErrorSheet(state = LedgerErrorSheetState.previewTwoButtons)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun BluetoothOffPreview() =
    ZcashTheme {
        LedgerErrorSheet(state = LedgerErrorSheetState.previewBluetoothOff)
    }
