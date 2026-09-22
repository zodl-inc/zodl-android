package co.electriccoin.zcash.ui.screen.connectledger.connected

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun LedgerConnectedScreen() {
    val navigationRouter = koinInject<NavigationRouter>()
    BackHandler { }
    LedgerConnectedView(
        state =
            LedgerConnectedState(
                onClose = { navigationRouter.backToRoot() }
            )
    )
}

@Serializable
data object LedgerConnectedArgs
