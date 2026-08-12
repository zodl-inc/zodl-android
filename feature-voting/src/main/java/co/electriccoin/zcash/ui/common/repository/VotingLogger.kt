package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.util.loggable

/**
 * Single-tag debug logger for CHP voting timing/diagnostics. Watch with `adb logcat -s VOTING`.
 * Mirrors `feature-migration`'s MigrationLogger: one plain tag, no unrelated noise, no-op in
 * release builds (see `loggable`'s `BuildConfig.DEBUG` gate).
 */
val votingLog = co.electriccoin.zcash.ui.util.loggable("VOTING")
