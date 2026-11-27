package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.voting.BuildConfig

/**
 * Single-tag debug logger for CHP voting timing/diagnostics. Watch with `adb logcat -s VOTING`.
 *
 * Implemented locally (not via `co.electriccoin.zcash.ui.util.loggable`, which is forbidden
 * outside ui-lib by the repo's detekt config - module boundary, feature-voting doesn't depend
 * on ui-lib internals for this) but behaviorally identical: mirrors `feature-migration`'s
 * MigrationLogger, one plain tag, no-op in release builds.
 */
interface VotingLogger {
    operator fun invoke(message: String, exception: Throwable? = null)
}

val votingLog =
    object : VotingLogger {
        override fun invoke(message: String, exception: Throwable?) {
            if (BuildConfig.DEBUG) {
                if (exception != null) {
                    android.util.Log.e("VOTING", message, exception)
                } else {
                    android.util.Log.d("VOTING", message)
                }
            }
        }
    }
