@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.contact

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.util.tryRequestFocus
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun AddZashiABContactScreen(args: AddZashiABContactArgs) {
    val viewModel = koinViewModel<AddZashiABContactVM> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack?.invoke() }
    var hasBeenAutofocused by rememberSaveable { mutableStateOf(false) }
    state?.let {
        ABContactView(
            state = it,
            onSideEffect = { nameFocusRequester, addressFocusRequester ->
                if (!hasBeenAutofocused) {
                    if (args.address == null) {
                        addressFocusRequester.tryRequestFocus() ?: true
                    } else {
                        nameFocusRequester.tryRequestFocus() ?: true
                    }
                    hasBeenAutofocused = true
                }
            }
        )
    }
}

@Serializable
data class AddZashiABContactArgs(
    val address: String?,
    /** A label to start the contact's name with, such as the ZNS name the address was resolved from. */
    val name: String? = null,
)
