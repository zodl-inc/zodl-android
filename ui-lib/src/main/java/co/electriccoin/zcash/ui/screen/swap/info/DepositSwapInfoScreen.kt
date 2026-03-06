package co.electriccoin.zcash.ui.screen.swap.info

import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun DepositSwapInfoScreen() {
    val navigationRouter = koinInject<NavigationRouter>()
    DepositSwapInfoView(
        onBack = { navigationRouter.back() }
    )
}

@Serializable
data object DepositSwapInfoArgs
