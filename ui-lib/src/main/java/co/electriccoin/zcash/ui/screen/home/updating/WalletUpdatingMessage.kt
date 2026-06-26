package co.electriccoin.zcash.ui.screen.home.updating

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Suppress("ModifierNaming")
@Composable
fun WalletUpdatingMessage(
    contentPadding: PaddingValues,
    state: WalletUpdatingMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            LottieProgress(
                modifier =
                    Modifier
                        .size(20.dp),
                size = 20.dp,
                loadingRes = co.electriccoin.zcash.ui.design.R.raw.lottie_loading_white
            )
        },
        title = {
            Text(
                stringResource(R.string.home_message_wallet_updating_title),
            )
        },
        subtitle = {
            Text(
                text = stringResource(R.string.smartBanner_content_updatingBalance_info),
            )
        },
        end = null
    )
}

class WalletUpdatingMessageState(
    val onClick: () -> Unit
) : HomeMessageState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            WalletUpdatingMessage(
                contentPadding = PaddingValues(16.dp),
                state =
                    WalletUpdatingMessageState(
                        onClick = {}
                    )
            )
        }
    }
