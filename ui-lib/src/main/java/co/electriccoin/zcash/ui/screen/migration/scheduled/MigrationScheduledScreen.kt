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
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import org.koin.androidx.compose.koinViewModel

data class MigrationScheduledState(
    val totalAmount: StringResource,
    val transfersProgress: StringResource,
    val duration: StringResource,
    val onDone: () -> Unit,
)

@Composable
fun MigrationScheduledScreen() {
    val vm = koinViewModel<MigrationScheduledVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { MigrationScheduledView(it) }
}

@Composable
fun MigrationScheduledView(state: MigrationScheduledState) {
    GradientBgScaffold(
        startColor = ZashiColors.Utility.SuccessGreen.utilitySuccess100,
        endColor = ZashiColors.Surfaces.bgPrimary,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_fist_punch),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Migration Scheduled",
                    style = ZashiTypography.header5,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your ZEC will be migrated to the Ironwood pool based on the schedule you approved.",
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ZashiColors.Surfaces.bgSecondary)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryRow(label = "Total to transfer", value = state.totalAmount.getValue())
                    SummaryRow(label = "Pool", value = "Orchard → Ironwood")
                    SummaryRow(label = "Transfers", value = state.transfersProgress.getValue())
                    SummaryRow(label = "Duration", value = state.duration.getValue())
                }
            }
            ZashiButton(
                state = ButtonState(text = stringRes("Done"), onClick = state.onDone),
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
private fun Preview() = ZcashTheme {
    MigrationScheduledView(
        state = MigrationScheduledState(
            totalAmount = stringRes("12.45800 ZEC"),
            transfersProgress = stringRes("0 of 5"),
            duration = stringRes("~24 hours"),
            onDone = {},
        )
    )
}
