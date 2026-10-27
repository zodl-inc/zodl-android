package co.electriccoin.zcash.ui.common.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the app is waiting for the user to pass app access authentication. The authentication state lives in
 * an Activity-scoped view model, so this is the bridge for application-scoped code that must not put UI in front
 * of the lock screen. Locked until the Activity says otherwise: without an Activity there is no UI to show anyway.
 */
interface AppAccessLockProvider {
    val isLocked: StateFlow<Boolean>

    fun setLocked(locked: Boolean)
}

class AppAccessLockProviderImpl : AppAccessLockProvider {
    private val state = MutableStateFlow(true)

    override val isLocked: StateFlow<Boolean> = state

    override fun setLocked(locked: Boolean) {
        state.value = locked
    }
}
