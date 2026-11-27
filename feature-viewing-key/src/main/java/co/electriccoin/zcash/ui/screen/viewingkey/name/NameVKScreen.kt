package co.electriccoin.zcash.ui.screen.viewingkey.name

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun NameVKScreen() {
    val vm = koinViewModel<NameVKVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        NameVKView(it)
    }
}

/** The screen that names the account being imported. */
@Serializable
internal data object NameVKArgs
