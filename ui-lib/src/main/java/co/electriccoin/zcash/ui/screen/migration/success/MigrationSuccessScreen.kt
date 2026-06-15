package co.electriccoin.zcash.ui.screen.migration.success

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import org.koin.androidx.compose.koinViewModel

data class MigrationSuccessState(
    val dustAmount: co.electriccoin.zcash.ui.design.util.StringResource? = null,
    val onViewTransaction: (() -> Unit)?,
    val onClose: () -> Unit,
)

@Composable
fun MigrationSuccessScreen() {
    val vm = koinViewModel<MigrationSuccessVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { MigrationSuccessView(it) }
}

@Composable
fun MigrationSuccessView(state: MigrationSuccessState) {
    BlankBgScaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (state.dustAmount != null) "Migration Complete" else "Sent!",
                    style = ZashiTypography.textXl,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.dustAmount != null)
                        "Your ZEC is now in the Ironwood pool."
                    else
                        "Your ZEC were successfully\nsent to Ironwood.",
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    textAlign = TextAlign.Center,
                )
                state.dustAmount?.let { dust ->
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Dust balance remaining",
                                style = ZashiTypography.textXs,
                                fontWeight = FontWeight.SemiBold,
                                color = ZashiColors.Text.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${dust.getValue()} stayed in Orchard — the amount is below the transfer threshold.",
                                style = ZashiTypography.textXs,
                                color = ZashiColors.Text.textTertiary,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                state.onViewTransaction?.let { onView ->
                    ZashiButton(
                        state = ButtonState(text = stringRes("View Transaction"), onClick = onView),
                        modifier = Modifier.fillMaxWidth(),
                        defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ZashiButton(
                    state = ButtonState(text = stringRes("Close"), onClick = state.onClose),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme {
    MigrationSuccessView(
        state = MigrationSuccessState(
            onViewTransaction = {},
            onClose = {},
        )
    )
}
