package co.electriccoin.zcash.ui.common.model.migration

/**
 * Formats a migration plan's total span so it reflects whatever interval the current
 * [cash.z.ecc.android.sdk.OrchardMigrationSdk] implementation actually schedules transfers at —
 * minutes for the compressed debug cadence, hours for the real one — instead of a hardcoded
 * "~24 hours" that only matched production timing.
 */
fun formatMigrationDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(60L)
    return if (seconds < 3600) {
        "~${seconds / 60} min"
    } else {
        "~${seconds / 3600} hours"
    }
}
