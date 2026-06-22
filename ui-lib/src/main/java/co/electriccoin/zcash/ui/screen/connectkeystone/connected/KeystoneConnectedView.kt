package co.electriccoin.zcash.ui.screen.connectkeystone.connected

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
fun KeystoneConnectedView(state: KeystoneConnectedState) {
    BlankBgScaffold { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                0f to ZashiColors.Utility.SuccessGreen.utilitySuccess100,
                                GRADIENT_OFFSET to ZashiColors.Surfaces.bgPrimary,
                            )
                    )
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldPadding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Image(
                    modifier = Modifier.size(120.dp),
                    painter = painterResource(R.drawable.ic_face_star),
                    contentDescription = null,
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.keystone_addHWWallet_connected),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.keystone_addHWWallet_connectedDesc),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.keystone_connected_button),
                    onClick = state.onClose,
                )
            }
        }
    }
}

private const val GRADIENT_OFFSET = 0.4f

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        KeystoneConnectedView(
            state = KeystoneConnectedState(onClose = {})
        )
    }
