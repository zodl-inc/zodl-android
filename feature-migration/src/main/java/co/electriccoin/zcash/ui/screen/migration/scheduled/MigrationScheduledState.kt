package co.electriccoin.zcash.ui.screen.migration.scheduled

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.compose.DisableScreenTimeout
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTextOrShimmer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import co.electriccoin.zcash.ui.screen.migration.component.MigrationFailureBottomSheet
import org.koin.androidx.compose.koinViewModel
import co.electriccoin.zcash.ui.design.R as DesignR

data class MigrationScheduledState(
    val totalAmount: StringResource,
    val transfersProgress: StringResource,
    val duration: StringResource,
    val backgroundHint: StringResource? = null,
    val onDone: () -> Unit,
)

@Composable
fun MigrationScheduledScreen() {
    val vm = koinViewModel<MigrationScheduledVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val isFinalizing by vm.isFinalizing.collectAsStateWithLifecycle()
    val failureSheet by vm.failureSheet.collectAsStateWithLifecycle()
    if (isFinalizing) {
        MigrationSchedulingView()
    } else {
        LceRenderer(
            state = state,
            loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
        ) { MigrationScheduledView(it) }
    }
    MigrationFailureBottomSheet(failureSheet)
}

// Figma node 5058:10456 — shown while a Keystone-signed batch is still being stored and
// finalized (Tor submit, schedule commit). Same layout as [MigrationScheduledView]'s summary
// card, but every value is a shimmer placeholder since the schedule isn't committed yet.
// Internal (not private): referenced by MigrationFlowPreviews.kt's aggregated design-review view.
@Composable
internal fun MigrationSchedulingView() {
    DisableScreenTimeout()
    GradientBgScaffold(
        startColor = ZashiColors.Utility.SuccessGreen.utilitySuccess100,
        endColor = ZashiColors.Surfaces.bgPrimary,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_fist_punch),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringRes(DesignR.string.migrationScheduled_schedulingTitle).getValue(),
                style = ZashiTypography.header5,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(DesignR.string.migrationScheduled_schedulingSubtitle).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZashiColors.Surfaces.bgSecondary)
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonSummaryRow(
                    label = stringRes(DesignR.string.migrationScheduled_totalToTransferLabel).getValue()
                )
                SkeletonSummaryRow(label = stringRes(DesignR.string.migrationScheduled_poolLabel).getValue())
                SkeletonSummaryRow(label = stringRes(DesignR.string.migrationScheduled_transfersLabel).getValue())
                SkeletonSummaryRow(label = stringRes(DesignR.string.migrationScheduled_durationLabel).getValue())
            }
            Spacer(Modifier.weight(1f))
            ZashiButton(
                state =
                    ButtonState(
                        text = stringRes(DesignR.string.migrationScheduled_schedulingTitle),
                        isEnabled = false,
                        isLoading = true,
                        onClick = {},
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SkeletonSummaryRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            modifier = Modifier.weight(1f),
        )
        ZashiTextOrShimmer(
            text = null as String?,
            shimmerWidth = 64.dp,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun MigrationScheduledView(state: MigrationScheduledState) {
    GradientBgScaffold(
        startColor = ZashiColors.Utility.SuccessGreen.utilitySuccess100,
        endColor = ZashiColors.Surfaces.bgPrimary,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_fist_punch),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringRes(DesignR.string.migrationScheduled_doneTitle).getValue(),
                style = ZashiTypography.header5,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(DesignR.string.migrationScheduled_doneSubtitle).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZashiColors.Surfaces.bgSecondary)
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryRow(
                    label = stringRes(DesignR.string.migrationScheduled_totalToTransferLabel).getValue(),
                    value = state.totalAmount.getValue(),
                )
                SummaryRow(
                    label = stringRes(DesignR.string.migrationScheduled_poolLabel).getValue(),
                    value = stringRes(DesignR.string.migrationScheduled_poolValue).getValue(),
                )
                SummaryRow(
                    label = stringRes(DesignR.string.migrationScheduled_transfersLabel).getValue(),
                    value = state.transfersProgress.getValue(),
                )
                SummaryRow(
                    label = stringRes(DesignR.string.migrationScheduled_durationLabel).getValue(),
                    value = state.duration.getValue(),
                )
            }
            state.backgroundHint?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            ZashiButton(
                state = ButtonState(text = stringRes(DesignR.string.migration_status_done), onClick = state.onDone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary,
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        MigrationScheduledView(
            state =
                MigrationScheduledState(
                    totalAmount = stringRes("12.45800 ZEC"),
                    transfersProgress = stringRes("0 of 5"),
                    duration = stringRes("~24 hours"),
                    backgroundHint =
                        stringRes(
                            "Transfers run when you open the app — enable background activity in Settings " +
                                "for automatic sending."
                        ),
                    onDone = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewScheduling() =
    ZcashTheme {
        MigrationSchedulingView()
    }
