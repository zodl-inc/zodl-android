package co.electriccoin.zcash.ui.screen.keepopen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.KeepOpenView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KeepOpenScreen(args: KeepOpenArgs) {
    val viewModel = koinViewModel<KeepOpenVM> { parametersOf(args.flow) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    KeepOpenView(state)
}

@Serializable
data class KeepOpenArgs(
    val flow: KeepOpenFlow
)

enum class KeepOpenFlow {
    RESTORE,
    RESYNC,
    KEYSTONE,
    LEDGER,
}
