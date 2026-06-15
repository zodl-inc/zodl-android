package co.electriccoin.zcash.ui.screen.migration.sending

import androidx.compose.foundation.layout.Box
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import org.koin.androidx.compose.koinViewModel

@Composable
fun MigrationSendingScreen() {
    val vm = koinViewModel<MigrationSendingVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.send() }
    LceRenderer(state) { MigrationSendingView() }
}

@Composable
fun MigrationSendingView() {
    BlankBgScaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ZashiColors.Btns.Brand.btnBrandFg)
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Sending...",
                    style = ZashiTypography.textXl,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your ZEC are being sent to\nIronwood...",
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme { MigrationSendingView() }
