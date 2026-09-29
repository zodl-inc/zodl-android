@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState
import com.valentinilk.shimmer.shimmer

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
 * The Figma placeholder list: three rows under a gradient that turns fully opaque halfway down,
 * with the issue's indicator anchored to its lower part. The gradient spans only the rows; Figma's
 * extends into the 20 dp inset beside them, where it lies over the same background and cannot show.
 */
@Composable
private fun DeviceSkeletons(isShimmering: Boolean, inlineIssue: LedgerInlineIssueState?) {
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(SKELETON_ROWS) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (isShimmering) Modifier.shimmer(rememberZashiShimmer()) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ZashiColors.Surfaces.bgPrimary)
                            .border(
                                BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
                                RoundedCornerShape(12.dp)
                            ).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(SkeletonFill, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonBar(width = SKELETON_TITLE_WIDTH, height = 16, radius = 4)
                        SkeletonBar(width = SKELETON_SUBTITLE_WIDTH, height = 12, radius = 3)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(SkeletonFill, CircleShape)
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to ZashiColors.Surfaces.bgPrimary.copy(alpha = 0f),
                            GRADIENT_OPAQUE_AT to ZashiColors.Surfaces.bgPrimary,
                            1f to ZashiColors.Surfaces.bgPrimary,
                        )
                    )
        )
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

/**
 * Figma fills the placeholders in a gray lighter than bgTertiary: Gray 50 by day, which this token
 * is exactly, and a dark gray at night that this token's Shark 900 matches most closely.
 */
private val SkeletonFill: Color
    @Composable get() = ZashiColors.Utility.Gray.utilityGray50

@Composable
private fun SkeletonBar(width: Int, height: Int, radius: Int) {
    Box(
        modifier =
            Modifier
                .height(height.dp)
                .width(width.dp)
                .clip(RoundedCornerShape(radius.dp))
                .background(SkeletonFill)
    )
}

private const val SKELETON_ROWS = 3

private const val SKELETON_TITLE_WIDTH = 100

private const val SKELETON_SUBTITLE_WIDTH = 68

private const val GRADIENT_OPAQUE_AT = 0.5f

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
private fun PairingPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewPairing)
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
