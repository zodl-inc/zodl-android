package co.electriccoin.zcash.ui.screen.editaccount

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun EditAccountScreen(args: EditAccountArgs) {
    val vm = koinViewModel<EditAccountVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack?.invoke() }
    state?.let { EditAccountView(it) }
}
