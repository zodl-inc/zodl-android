@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.chooseserver

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ChooseServerScreen() {
    val vm = koinViewModel<ChooseServerVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack() }
    ChooseServerView(state = state)
}

@Serializable
data object ChooseServerArgs
