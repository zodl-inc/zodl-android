package co.electriccoin.zcash.ui.screen.swap.info

import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.ExternalUrl
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun SwapInfoScreen() {
    val navigationRouter = koinInject<NavigationRouter>()
    val state =
        SwapInfoState(
            onBack = { navigationRouter.back() },
            onLearnMoreClick = { navigationRouter.replace(ExternalUrl(LEARN_MORE_URL)) }
        )
    SwapInfoView(state)
}

@Serializable
data object SwapInfoArgs

private const val LEARN_MORE_URL = "https://support.zodl.com/article/26-swapping-into-zec"
