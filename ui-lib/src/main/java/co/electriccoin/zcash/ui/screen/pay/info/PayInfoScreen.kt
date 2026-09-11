package co.electriccoin.zcash.ui.screen.pay.info

import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.ExternalUrl
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun PayInfoScreen() {
    val navigationRouter = koinInject<NavigationRouter>()
    val state =
        PayInfoState(
            onBack = { navigationRouter.back() },
            onLearnMoreClick = { navigationRouter.replace(ExternalUrl(LEARN_MORE_URL)) }
        )
    PayInfoView(state)
}

@Serializable
data object PayInfoArgs

private const val LEARN_MORE_URL = "https://support.zodl.com/article/24-using-crosspay-to-spend-zec"
