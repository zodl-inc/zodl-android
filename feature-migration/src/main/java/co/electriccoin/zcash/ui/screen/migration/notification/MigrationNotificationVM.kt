package co.electriccoin.zcash.ui.screen.migration.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class MigrationNotificationVM(
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val context: Context,
) : ViewModel() {
    private val lce = mutableLce<Unit>()

    init {
        // MOB-1665: decided once, here — not via the screen's old `remember { }` check, which
        // re-evaluated (and re-fired its LaunchedEffect) on every FRESH composition of this
        // screen, including the one Navigation-Compose creates when a Back press pops back onto
        // this entry. This ViewModel instance survives being hidden while Review sits on top of
        // it and is only recreated if this entry is actually popped off the back stack, so
        // deciding here makes the auto-skip genuinely one-shot for the life of this back-stack
        // entry: it can no longer re-fire just because the permission became true elsewhere in
        // the flow (e.g. granted via onAllow() on the very first visit) and the user then pressed
        // Back to look at this screen again — see MigrationBatteryVM's init for the same fix and
        // the confirmed-live symptom (Battery -> Notification -> a brand-new Review, 3-4 times).
        val isAlreadyGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        if (isAlreadyGranted) {
            onAutoSkip()
        }
    }

    val state: StateFlow<LceState<MigrationNotificationState>> =
        flowOf(
            MigrationNotificationState(
                onAllow = ::onAllow,
                onSkip = ::onSkip,
                onAutoSkip = ::onAutoSkip,
                onBack = ::onBack,
            )
        ).withLce(lce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onAllow() = navigationRouter.forward(reviewArgs())

    private fun onSkip() = navigationRouter.forward(reviewArgs())

    // Used when this screen skips itself without ever being shown (permission already granted) —
    // replace instead of forward so it doesn't linger in the back stack and bounce the user
    // straight back here when they press back from a later screen.
    private fun onAutoSkip() = navigationRouter.replace(reviewArgs())

    // The Tor privacy check already happened earlier in the flow (How This Works, ahead of
    // Battery/Notification).
    private fun reviewArgs() = MigrationReviewArgs(mode = MigrationMode.AUTOMATIC)

    private fun onBack() = navigationRouter.back()
}
