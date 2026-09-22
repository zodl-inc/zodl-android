@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.chooseserver

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun ChooseServerScreen(args: ChooseServerArgs) {
    val vm =
        koinViewModel<ChooseServerVM> {
            parametersOf(args.preselectFastestClearnet, args.autoStartProbing)
        }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack() }
    ChooseServerView(state = state)
}

@Serializable
data class ChooseServerArgs(
    val preselectFastestClearnet: Boolean = false,
    val autoStartProbing: Boolean = true
)
