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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
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
                            ).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(ZashiColors.Surfaces.bgTertiary, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBar(widthFraction = SKELETON_TITLE_WIDTH)
                        SkeletonBar(widthFraction = SKELETON_SUBTITLE_WIDTH)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(ZashiColors.Surfaces.bgTertiary, CircleShape)
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
                            0f to Color.Transparent,
                            1f to ZashiColors.Surfaces.bgPrimary,
                        )
                    )
        )
        inlineIssue?.let {
            LedgerInlineIssue(
                state = it,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float) {
    Box(
        modifier =
            Modifier
                .height(10.dp)
                .width(SKELETON_BAR_BASE_WIDTH.dp * widthFraction)
                .clip(RoundedCornerShape(5.dp))
                .background(ZashiColors.Surfaces.bgTertiary)
    )
}

@Composable
private fun DeviceRow(state: LedgerDeviceItemState, index: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZashiColors.Surfaces.bgPrimary)
                .selectionBorder(state.isSelected)
                .clickable(enabled = state.isEnabled, onClick = state.onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(LedgerDeviceScanTag.DEVICE_ROW_PREFIX + index),
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

private const val SKELETON_ROWS = 3

private const val SKELETON_BAR_BASE_WIDTH = 160

private const val SKELETON_TITLE_WIDTH = 1f

private const val SKELETON_SUBTITLE_WIDTH = 0.6f

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
private fun ErrorPreview() =
    ZcashTheme {
        LedgerDeviceScanView(state = LedgerDeviceScanState.previewError)
    }
