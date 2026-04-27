package co.electriccoin.zcash.ui.common.viewmodel

/**
 * A deferred side-effect action emitted by a ViewModel and executed by the UI.
 *
 * The VM owns the lifecycle: it sets [action] on a `MutableStateFlow<Action?>` and clears
 * it to `null` inside the lambda before executing the side-effect. The screen collects via
 * `collectAsStateWithLifecycle()` and calls `action?.eval()` directly — no separate clear
 * call needed on the UI side.
 *
 * Because `collectAsStateWithLifecycle()` only delivers values while the composable is in
 * the composition (screen on top), the action will not fire if another screen is pushed
 * on top before the action is consumed.
 *
 * Usage in VM:
 * ```kotlin
 * val action = MutableStateFlow<Action?>(null)
 *
 * // emit — lambda clears the action before executing the side-effect:
 * action.value = Action {
 *     action.value = null
 *     navigationRouter.forward(SomeArgs)
 * }
 * ```
 *
 * Usage in Screen:
 * ```kotlin
 * val action by vm.action.collectAsStateWithLifecycle()
 * action?.execute()
 * ```
 */
data class Action(
    val execute: () -> Unit
)
