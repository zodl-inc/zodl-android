package co.electriccoin.zcash.ui.screen.migration.battery

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.IsBackgroundExecutionAvailableProvider
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class MigrationBatteryVM(
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val isBackgroundExecutionAvailableProvider: IsBackgroundExecutionAvailableProvider,
) : ViewModel() {
    private val lce = mutableLce<Unit>()

    init {
        // MOB-1665: decided once, here — not via the screen's old `remember { }` check, which
        // re-evaluated (and re-fired its LaunchedEffect) on every FRESH composition of this
        // screen, including the one Navigation-Compose creates when a Back press pops back onto
        // this entry. This ViewModel instance survives being hidden while Notification/Review sit
        // on top of it and is only recreated if this entry is actually popped off the back stack,
        // so deciding here makes the auto-skip genuinely one-shot for the life of this back-stack
        // entry: it can no longer re-fire just because the exemption became true elsewhere in the
        // flow (e.g. granted via onAllow() on the very first visit) and the user then pressed Back
        // to look at this screen again — confirmed live: that exact sequence bounced through
        // Battery -> Notification -> a brand-new Review 3-4 times before landing anywhere stable.
        if (isBackgroundExecutionAvailableProvider.isAvailable()) {
            onAutoSkip()
        }
    }

    val state: StateFlow<LceState<MigrationBatteryState>> =
        flowOf(
            MigrationBatteryState(
                onAllow = ::onAllow,
                onSkip = ::onSkip,
                onAutoSkip = ::onAutoSkip,
                onBack = ::onBack,
            )
        ).withLce(lce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onAllow() = navigationRouter.forward(MigrationNotificationArgs)

    // Declining only makes background delivery less reliable (Doze may defer it more) — it does
    // not disable it, so this doesn't change what happens downstream at all.
    private fun onSkip() = navigationRouter.forward(MigrationNotificationArgs)

    // Used when this screen skips itself without ever being shown (permission already granted) —
    // replace instead of forward so it doesn't linger in the back stack and bounce the user
    // straight back here when they press back from a later screen.
    private fun onAutoSkip() = navigationRouter.replace(MigrationNotificationArgs)

    private fun onBack() = navigationRouter.back()
}
