package co.electriccoin.zcash.ui.screen.connectkeystone

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.scankeystone.ScanKeystoneSignInRequest
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun ConnectKeystoneScreen() {
    val navigationRouter = koinInject<NavigationRouter>()

    BackHandler {
        navigationRouter.back()
    }

    ConnectKeystoneView(
        state =
            ConnectKeystoneState(
                onViewKeystoneTutorialClicked = {
                    // do nothing
                },
                onBackClick = {
                    navigationRouter.back()
                },
                onContinueClick = {
                    navigationRouter.forward(ScanKeystoneSignInRequest)
                },
            )
    )
}

@Serializable
object ConnectKeystoneArgs
