package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.MigrationTransferStates
import kotlin.time.Instant

/**
 * A DERIVED, never-persisted view of the engine's live migration state — the replacement for the
 * deleted `MigrationPlan` display cache (see
 * `spec/2026-07-30-plan-cache-elimination-proposal.md`). Every field is computed fresh from
 * [cash.z.ecc.android.sdk.OrchardMigrationSdk.getMigrationTransferStates] (which reads the
 * engine's persisted state) plus the measured block rate; there is deliberately NO app copy left
 * to diverge from the engine (the live-observed reschedule divergence class is unrepresentable).
 *
 * Display conventions preserved from the old plan cache:
 * - [transfers] are CROSSINGS ONLY, sorted by scheduled height (id tiebreak) — "Transfer N" is
 *   the 1-based position in this list, permanently different from the engine's tx id (ZIP 318
 *   shuffles the two orderings apart).
 * - [preparations] are the note-split plumbing rows, same sort.
 * - Counts ([completedCount]/[totalCount]/[isComplete]) are crossings-only and count SENT
 *   (broadcast) as done, matching the banner/notification copy users already know.
 */
data class LiveMigrationSnapshot(
    val transfers: List<LiveMigrationTransfer>,
    val preparations: List<LiveMigrationPreparation>,
    /** The scanned tip the snapshot was computed at. */
    val tipHeight: Long,
) {
    val nextPending: LiveMigrationTransfer? get() = transfers.firstOrNull { !it.isSent }
    val completedCount: Int get() = transfers.count { it.isSent }
    val totalCount: Int get() = transfers.size
    val isComplete: Boolean get() = transfers.isNotEmpty() && transfers.all { it.isSent }
}

/** One crossing transfer, live from the engine. [index] is the 0-based display position. */
data class LiveMigrationTransfer(
    val id: Long,
    val index: Int,
    val amountZatoshi: Long,
    val scheduledHeight: Long,
    /** Wall-clock estimate of [scheduledHeight] at the measured block rate. */
    val scheduledAt: Instant,
    val isSent: Boolean,
    val isProved: Boolean,
    /** The engine's actionable step when this transaction is ready (null when blocked or done). */
    val action: MigrationTransferAction?,
    /** Why the engine says this transaction is waiting (null when ready or done). */
    val blocker: MigrationTransferBlocker?,
    /** Wall-clock estimate of the ZIP 203 expiry; null = never expires. */
    val expiryAt: Instant?,
    val minedHeight: Long?,
    /** Wall-clock estimate of [minedHeight]; null until mined. Defaults for existing test call sites. */
    val minedAt: Instant? = null,
)

/** One preparation (note-split) transaction, live from the engine. */
data class LiveMigrationPreparation(
    val id: Long,
    val layer: Int,
    val index: Int,
    val scheduledHeight: Long,
    val scheduledAt: Instant,
    val isSent: Boolean,
    val isProved: Boolean,
    val action: MigrationTransferAction?,
    val blocker: MigrationTransferBlocker?,
    val dependsOn: List<Long>,
)

/**
 * The single states→snapshot projection. [nowEpochSeconds]/[estimatedTip]/[secondsPerBlock]
 * define the height→wall-clock line; heights already past the tip project to "now or earlier"
 * (negative deltas allowed — "scheduled 2h ago" is meaningful display data).
 */
fun MigrationTransferStates.toSnapshot(
    estimatedTip: Long,
    secondsPerBlock: Long,
    nowEpochSeconds: Long,
    nu7ActivationHeight: Long? = null,
): LiveMigrationSnapshot {
    fun at(height: Long): Instant =
        Instant.fromEpochSeconds(
            nowEpochSeconds +
                estimatedSecondsBetweenHeights(estimatedTip, height, secondsPerBlock, nu7ActivationHeight)
        )
    val sorted = transfers.sortedWith(compareBy({ it.scheduledHeight }, { it.id }))
    return LiveMigrationSnapshot(
        transfers =
            sorted
                .filter { it.isTransfer }
                .mapIndexed { i, t ->
                    LiveMigrationTransfer(
                        id = t.id,
                        index = i,
                        amountZatoshi = t.amountZatoshi ?: 0L,
                        scheduledHeight = t.scheduledHeight,
                        scheduledAt = at(t.scheduledHeight),
                        isSent = t.isSent,
                        isProved = t.isProved,
                        action = t.action?.toAppAction(),
                        blocker = t.blocker?.toAppBlocker(),
                        expiryAt = t.expiryHeight?.let { at(it) },
                        minedHeight = t.minedHeight,
                        minedAt = t.minedHeight?.let { at(it) },
                    )
                },
        preparations =
            sorted
                .filter { !it.isTransfer }
                .map { p ->
                    LiveMigrationPreparation(
                        id = p.id,
                        layer = p.prepLayer ?: 0,
                        index = p.prepIndex ?: 0,
                        scheduledHeight = p.scheduledHeight,
                        scheduledAt = at(p.scheduledHeight),
                        isSent = p.isSent,
                        isProved = p.isProved,
                        action = p.action?.toAppAction(),
                        blocker = p.blocker?.toAppBlocker(),
                        dependsOn = p.dependsOn,
                    )
                },
        tipHeight = tipHeight,
    )
}
