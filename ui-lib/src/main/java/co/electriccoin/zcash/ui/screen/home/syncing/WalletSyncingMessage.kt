package co.electriccoin.zcash.ui.screen.home.syncing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiCircularProgressIndicatorByPercent
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Suppress("ModifierNaming")
@Composable
fun WalletSyncingMessage(
    contentPadding: PaddingValues,
    state: WalletSyncingMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            ZashiCircularProgressIndicatorByPercent(
                modifier = Modifier.size(20.dp),
                progressPercent = state.progress,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.home_message_syncing_title, state.progress),
            )
        },
        subtitle = {
            Text(
                text = stringResource(R.string.smartBanner_content_sync_info),
            )
        },
        end = null
    )
}

class WalletSyncingMessageState(
    val progress: Float,
    val onClick: () -> Unit
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        val progress by animateFloatAsState(50f, label = "progress", animationSpec = tween(10000))

        BlankSurface {
            WalletSyncingMessage(
                contentPadding = PaddingValues(16.dp),
                state =
                    WalletSyncingMessageState(
                        progress = progress,
                        onClick = {}
                    )
            )
        }
    }
