package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceSkeletons
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

/**
 * Step 2 of the Ledger connect flow: the Figma "Searching for Devices..." frame with its placeholder
 * rows, then "Select Your Ledger" once a device is listed, with the "Advanced options" card under
 * the list.
 */
@Composable
fun LedgerDeviceScanView(state: LedgerDeviceScanState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBack) },
        step = 2,
        title = state.title.getValue(),
        description = state.subtitle.getValue(),
        bottomButton = {
            ZashiButton(
                state = state.primaryButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerDeviceScanTag.PRIMARY_BTN),
            )
        },
    ) {
        Spacer(LIST_EXTRA_TOP_GAP.dp)
        if (state.devices.isEmpty()) {
            if (state.showDeviceSkeletons) {
                LedgerDeviceSkeletons(
                    isShimmering = state.isScanning && state.inlineIssue == null,
                    fadeStart = SKELETON_FADE_START,
                    opaqueAt = 1f,
                ) {
                    state.inlineIssue?.let {
                        LedgerInlineIssue(
                            state = it,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = INDICATOR_BOTTOM_OFFSET.dp),
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                state.devices.forEachIndexed { index, device ->
                    if (index != 0) {
                        Spacer(12.dp)
                    }
                    LedgerDeviceRow(
                        state = device,
                        testTag = LedgerDeviceScanTag.DEVICE_ROW_PREFIX + index,
                    )
                }
            }
        }
        state.advancedOptions?.let {
            Spacer(12.dp)
            LedgerAdvancedOptions(state = it)
        }
    }

    LedgerErrorSheet(state.errorSheet)
}

/**
 * Figma starts the list 32 dp below the description, 8 dp further than the shared layout's gap.
 */
private const val LIST_EXTRA_TOP_GAP = 8

private const val INDICATOR_BOTTOM_OFFSET = 24

/**
 * Figma's step 2 fade covers the placeholder list from y 52 of its 216 dp down, clear at the top
 * and opaque at the bottom, so the first row stays fully visible.
 */
private const val SKELETON_FADE_START = 52f / 216f

@PreviewScreens
@Composable
private fun ScanningPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewSearching)
    }

@PreviewScreens
@Composable
private fun SelectNonePreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewSelectNone)
    }

@PreviewScreens
@Composable
private fun SelectPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewSelect)
    }

@PreviewScreens
@Composable
private fun ConnectingPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewConnecting)
    }

@PreviewScreens
@Composable
private fun BluetoothOffPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewBluetoothOff)
    }

@PreviewScreens
@Composable
private fun AccessRequiredPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewAccessRequired)
    }

@PreviewScreens
@Composable
private fun NoDevicesPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewNoDevices)
    }

@PreviewScreens
@Composable
private fun SomethingWentWrongPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewSomethingWentWrong)
    }

@PreviewScreens
@Composable
private fun UnlockPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewUnlock)
    }
