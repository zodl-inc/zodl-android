package co.electriccoin.zcash.ui.screen.connectledger.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiInScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
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
import co.electriccoin.zcash.ui.design.util.stringRes
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
                navigationAction = { ZashiTopAppBarCloseNavigation(state.onBack) },
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
                    modifier = Modifier.height(32.dp),
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
                    DeviceSkeletons(isShimmering = state.isScanning)
                } else {
                    state.devices.forEachIndexed { index, device ->
                        if (index != 0) {
                            Spacer(Modifier.height(12.dp))
                        }
                        DeviceRow(state = device, index = index)
                    }
                }
                Spacer(Modifier.height(24.dp))
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

@Composable
private fun DeviceSkeletons(isShimmering: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (isShimmering) Modifier.shimmer(rememberZashiShimmer()) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZashiColors.Surfaces.bgSecondary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(ZashiColors.Surfaces.bgTertiary, CircleShape)
                )
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier =
                        Modifier
                            .height(12.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ZashiColors.Surfaces.bgTertiary)
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(state: LedgerDeviceItemState, index: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZashiColors.Surfaces.bgPrimary)
                .then(
                    if (state.isSelected) {
                        Modifier.selectedBorder()
                    } else {
                        Modifier.unselectedBorder()
                    }
                ).clickable(enabled = state.isEnabled, onClick = state.onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(LedgerDeviceScanTag.DEVICE_ROW_PREFIX + index),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(40.dp),
            painter = painterResource(R.drawable.ic_ledger_device),
            contentDescription = null,
        )
        Spacer(Modifier.width(16.dp))
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

@Composable
private fun Modifier.selectedBorder() =
    this.then(
        Modifier.border(
            BorderStroke(2.dp, ZashiColors.Text.textPrimary),
            RoundedCornerShape(12.dp)
        )
    )

@Composable
private fun Modifier.unselectedBorder() =
    this.then(
        Modifier.border(
            BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
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
                        ZashiColors.Text.textPrimary
                    } else {
                        Color.Transparent
                    }
                ).border(
                    BorderStroke(
                        1.dp,
                        if (isSelected) {
                            ZashiColors.Text.textPrimary
                        } else {
                            ZashiColors.Surfaces.strokeSecondary
                        }
                    ),
                    CircleShape
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                modifier = Modifier.size(12.dp),
                painter = painterResource(R.drawable.ic_ledger_check),
                contentDescription = null,
                tint = ZashiColors.Surfaces.bgPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerErrorSheet(state: LedgerErrorSheetState?) {
    ZashiInScreenModalBottomSheet(state = state) { sheetState ->
        Column(
            modifier =
                Modifier
                    .weight(1f, false)
                    .padding(horizontal = 24.dp)
                    .testTag(LedgerDeviceScanTag.ERROR_SHEET),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(ZashiColors.Surfaces.bgSecondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_ledger_alert_circle),
                    contentDescription = null,
                    tint = ZashiColors.Text.textPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = sheetState.title.getValue(),
                style = ZashiTypography.textXl,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = sheetState.message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            ZashiButton(
                state = sheetState.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            sheetState.secondary?.let { secondary ->
                Spacer(Modifier.height(8.dp))
                ZashiButton(
                    state = secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val SKELETON_ROWS = 3

@PreviewScreens
@Composable
private fun ScanningPreview() =
    ZcashTheme {
        LedgerDeviceScanView(
            state =
                LedgerDeviceScanState(
                    title = stringRes("Searching for Devices…"),
                    subtitle = stringRes("Make sure your Ledger is unlocked and Bluetooth is enabled."),
                    isScanning = true,
                    devices = emptyList(),
                    primaryButton = ButtonState(stringRes("Searching"), isEnabled = false, isLoading = true),
                    errorSheet = null,
                    onPermissionsGranted = {},
                    onPermissionsDenied = {},
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun SelectPreview() =
    ZcashTheme {
        LedgerDeviceScanView(
            state =
                LedgerDeviceScanState(
                    title = stringRes("Select Your Device"),
                    subtitle = stringRes("Select the Ledger device you'd like to connect."),
                    isScanning = false,
                    devices =
                        listOf(
                            LedgerDeviceItemState(
                                name = stringRes("Ledger Device 1"),
                                isSelected = true,
                                isEnabled = true,
                                onClick = {},
                            ),
                            LedgerDeviceItemState(
                                name = stringRes("Ledger Device 2"),
                                isSelected = false,
                                isEnabled = true,
                                onClick = {},
                            ),
                        ),
                    primaryButton = ButtonState(stringRes("Connect")),
                    errorSheet = null,
                    onPermissionsGranted = {},
                    onPermissionsDenied = {},
                    onBack = {},
                )
        )
    }
