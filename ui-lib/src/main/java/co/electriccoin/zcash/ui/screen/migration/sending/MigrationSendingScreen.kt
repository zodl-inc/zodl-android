package co.electriccoin.zcash.ui.screen.migration.sending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import org.koin.androidx.compose.koinViewModel

@Composable
fun MigrationSendingScreen() {
    val vm = koinViewModel<MigrationSendingVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.send() }
    LceRenderer(state) { MigrationSendingView() }
}

// Figma node 2618:6858 ("Sending") — reuses the same "sending" Lottie composition the standard
// (non-migration) TransactionProgressView shows while a regular transfer is submitting. The
// frame has no bottom CTA (empty CTA slot) since there's nothing actionable yet while sending —
// this mirrors TransactionProgressVM.createSendingState(), which likewise leaves all buttons null.
@Composable
fun MigrationSendingView() {
    BlankBgScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SendingAnimation()
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Sending...",
                style = ZashiTypography.header5,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your ZEC are being sent to\nIronwood...",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SendingAnimation() {
    val lottieRes = R.raw.send_confirmation_sending_v1 orDark R.raw.send_confirmation_sending_dark_v1
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRes))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(
        composition = composition,
        progress = { progress },
        maintainOriginalImageBounds = true,
        modifier = Modifier.size(200.dp),
    )
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme { MigrationSendingView() }
