package co.electriccoin.zcash.ui.common.provider

import android.content.Context

/**
 * DEBUG ONLY: a synchronous, directly-readable override forcing
 * [IsBackgroundExecutionAvailableProvider.isAvailable] to report unavailable regardless of the
 * device's real battery-optimization/background-restriction state — a QA testing aid for spec
 * §6.4 "Transfer Ready to Send", whose no-background-execution condition is otherwise only
 * reachable by actually revoking the app's battery-optimization exemption from system Settings.
 *
 * Kept synchronous (raw [android.content.SharedPreferences], not the suspend [StorageProvider]
 * abstraction) because [IsBackgroundExecutionAvailableProvider.isAvailable] itself is synchronous
 * — it's called from a `@Composable` ([co.electriccoin.zcash.ui.screen.migration.battery
 * .MigrationBatteryScreen]) as well as suspend contexts, and only the former can't tolerate a
 * suspend read. Backed by regular (non-encrypted) app storage, wiped on uninstall.
 */
object DebugForceBackgroundExecutionUnavailable {
    private const val PREFS_NAME = "debug_migration_prefs"
    private const val KEY = "force_background_execution_unavailable"

    fun isForced(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, forced: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, forced)
            .apply()
    }
}
