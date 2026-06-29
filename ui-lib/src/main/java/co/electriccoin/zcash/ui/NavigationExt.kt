package co.electriccoin.zcash.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog

inline fun <reified T : Any> NavGraphBuilder.dialogComposable(
    noinline content: @Composable (NavBackStackEntry) -> Unit
) {
    this.dialog<T>(
        typeMap = emptyMap(),
        deepLinks = emptyList(),
        dialogProperties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        content = content
    )
}

object NavigationTargets {
    const val EXPORT_PRIVATE_DATA = "export_private_data"
    const val NOT_ENOUGH_SPACE = "not_enough_space"
    const val QR_CODE = "qr_code"
    const val WHATS_NEW = "whats_new"
    const val CRASH_REPORTING_OPT_IN = "crash_reporting_opt_in"
}

object NavigationArgs {
    const val ADDRESS_TYPE = "addressType"
}
