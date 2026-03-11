@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.contact

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.error.DialogView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun UpdateGenericABContactScreen(args: UpdateGenericABContactArgs) {
    val viewModel = koinViewModel<UpdateGenericABContactVM> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack?.invoke() }
    state?.let { ABContactView(state = it) }
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    dialogState?.let {
        DialogView(state = it)
    }
}

@Serializable
data class UpdateGenericABContactArgs(
    val address: String,
    val chain: String?
)
