package co.electriccoin.zcash.migration

/**
 * The single logger for all app-side migration code. Plain `MIGRATION` logcat tag with no
 * column padding — watch with `adb logcat -s MIGRATION` (SDK-Rust migration lines keep their
 * `MIGRATION_DIAG` marker under `cash.z.rust.logs`).
 *
 * Implemented locally (not via `co.electriccoin.zcash.ui.util.loggable`, which is forbidden
 * outside ui-lib by the repo's detekt config — module boundary, feature-migration doesn't
 * depend on ui-lib internals for this) but behaviorally identical: a no-op in release builds.
 */
interface MigrationLogger {
    operator fun invoke(message: String, exception: Throwable? = null)
}

val migrationLog =
    object : MigrationLogger {
        override fun invoke(message: String, exception: Throwable?) {
            if (BuildConfig.DEBUG) {
                if (exception != null) {
                    android.util.Log.e("MIGRATION", message, exception)
                } else {
                    android.util.Log.d("MIGRATION", message)
                }
            }
        }
    }
