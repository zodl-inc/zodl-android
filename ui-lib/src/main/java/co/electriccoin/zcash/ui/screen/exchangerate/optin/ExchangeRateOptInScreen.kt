package co.electriccoin.zcash.ui.screen.exchangerate.optin

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExchangeRateOptInScreen() {
    val vm = koinViewModel<ExchangeRateOptInVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state?.onBack() }
    state?.let { ExchangeRateOptInView(state = it) }
}

@Serializable
object ExchangeRateOptInArgs
