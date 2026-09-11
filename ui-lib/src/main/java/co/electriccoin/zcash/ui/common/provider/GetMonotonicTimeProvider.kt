package co.electriccoin.zcash.ui.common.provider

import android.os.SystemClock

/**
 * Provides a monotonic timestamp, i.e. milliseconds elapsed since boot (including deep sleep),
 * via [SystemClock.elapsedRealtime]. Unlike [System.currentTimeMillis], this clock cannot be
 * changed by the user, which makes it the only safe source of truth for security-relevant
 * elapsed-time checks such as the app-lock re-authentication timeout.
 */
class GetMonotonicTimeProvider {
    operator fun invoke() = SystemClock.elapsedRealtime()
}
