@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceSkeletons
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDeviceScanView(state: LedgerDeviceScanState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                navigationAction = {
                    when (state.navigation) {
                        LedgerDeviceScanNavigation.BACK -> ZashiTopAppBarBackNavigation(state.onBack)
                        LedgerDeviceScanNavigation.CLOSE -> ZashiTopAppBarCloseNavigation(state.onBack)
                    }
                },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
            ) {
                Image(
                    modifier = Modifier.height(40.dp),
                    painter = painterResource(R.drawable.ic_ledger_wordmark),
                    contentDescription = null,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.height(32.dp))
                if (state.devices.isEmpty()) {
                    if (state.showDeviceSkeletons) {
                        DeviceSkeletons(
                            isShimmering = state.isScanning && state.inlineIssue == null,
                            inlineIssue = state.inlineIssue,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        state.devices.forEachIndexed { index, device ->
                            if (index != 0) {
                                Spacer(Modifier.height(12.dp))
                            }
                            LedgerDeviceRow(
                                state = device,
                                testTag = LedgerDeviceScanTag.DEVICE_ROW_PREFIX + index,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.weight(1f))
                ZashiButton(
                    state = state.primaryButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(LedgerDeviceScanTag.PRIMARY_BTN),
                )
            }

            LedgerErrorSheet(state.errorSheet)
        }
    }
}

/**
 * The shared placeholder rows with the issue's indicator anchored to their lower part.
 */
@Composable
private fun DeviceSkeletons(isShimmering: Boolean, inlineIssue: LedgerInlineIssueState?) {
    LedgerDeviceSkeletons(isShimmering = isShimmering) {
        inlineIssue?.let {
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

private const val INDICATOR_BOTTOM_OFFSET = 24

@PreviewScreens
@Composable
private fun ScanningPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewSearching)
    }

@PreviewScreens
@Composable
private fun SelectPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewSelect)
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
