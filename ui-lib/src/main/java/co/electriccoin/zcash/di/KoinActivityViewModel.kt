package co.electriccoin.zcash.di

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.currentKoinScope
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.viewmodel.defaultExtras

@Suppress("LongParameterList")
@Composable
inline fun <reified T : ViewModel> koinActivityViewModel(
    qualifier: Qualifier? = null,
    viewModelStoreOwner: ViewModelStoreOwner = LocalContext.componentActivity(),
    key: String? = null,
    extras: CreationExtras = defaultExtras(LocalContext.componentActivity()),
    scope: Scope = currentKoinScope(),
    noinline parameters: ParametersDefinition? = null,
) = koinViewModel<T>(
    qualifier = qualifier,
    viewModelStoreOwner = viewModelStoreOwner,
    key = key,
    extras = extras,
    scope = scope,
    parameters = parameters,
)

@Composable
fun ProvidableCompositionLocal<Context>.componentActivity(): ComponentActivity = this.current.findComponentActivity()

/**
 * Walks the whole [ContextWrapper] chain instead of a single level, because the ambient context can carry
 * more than one wrapper: [co.electriccoin.zcash.ui.design.component.Override] wraps it for automated tests
 * and the app theme wraps it again whenever the chosen appearance diverges from the ambient configuration.
 * A single-level unwrap threw for every screen composed below such a stack.
 */
private fun Context.findComponentActivity(): ComponentActivity {
    var context: Context = this
    while (context !is ComponentActivity) {
        context =
            (context as? ContextWrapper)?.baseContext
                ?: throw ClassCastException("Context is not a ComponentActivity and does not wrap one")
    }
    return context
}
