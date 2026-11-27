package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.AttentionReason
import kotlin.time.Instant

/**
 * UI-facing classification of [AttentionReason], mirroring the SDK's own two distinct causes for a
 * [cash.z.ecc.android.sdk.MigrationState.RequiresAttention] state — see design spec §6.2 (Migration
 * Plan Update) vs §6.3 (Transfer(s) Expired). Screens/the home banner must key their copy off this,
 * never collapse both causes into one generic "invalid" message again.
 *
 * [AttentionReason.SyncRequiredBeforeNext] is intentionally out of scope here — nothing in the app
 * surfaces that reason today (see [cash.z.ecc.android.sdk.OrchardMigrationSdk.isSyncBlocked]'s own
 * sync/broadcast decoupling, which already covers the real case that reason would otherwise be
 * for) — callers that only care about the two attention screens should filter on the reason type
 * before ever calling [toUiKind].
 */
enum class MigrationAttentionKind { PLAN_UPDATE, TRANSFER_EXPIRED }

fun AttentionReason.toUiKind(): MigrationAttentionKind =
    when (this) {
        is AttentionReason.InvalidTransfer -> MigrationAttentionKind.PLAN_UPDATE
        AttentionReason.TransferExpired -> MigrationAttentionKind.TRANSFER_EXPIRED
        AttentionReason.SyncRequiredBeforeNext -> MigrationAttentionKind.TRANSFER_EXPIRED
    }

/**
 * The specific 1-based "Transfer N" indices this [AttentionReason] concerns, read from the live
 * [LiveMigrationSnapshot] (engine-derived — the display index and the stable engine id live on the
 * same row, so no id↔index correlation against a cache is needed anymore).
 *
 * For [AttentionReason.InvalidTransfer], this is exactly the one transfer the SDK named — empty if
 * the snapshot has no transfer with that id.
 *
 * For [AttentionReason.TransferExpired], the SDK doesn't name a specific transfer, so this derives
 * the affected set as every still-unsent transfer whose ZIP 203 expiry has already passed [now] —
 * i.e. the set [cash.z.ecc.android.sdk.OrchardMigrationSdk.restartCurrentMigrationStep] will
 * actually discard, not merely "everything after the last completed one."
 */
fun AttentionReason.affectedTransferIndices(snapshot: LiveMigrationSnapshot, now: Instant): List<Int> =
    when (this) {
        is AttentionReason.InvalidTransfer -> {
            snapshot.transfers.filter { it.id == transferId }.map { it.index }
        }

        AttentionReason.TransferExpired -> {
            snapshot.transfers
                .filter { !it.isSent && it.expiryAt != null && it.expiryAt <= now }
                .map { it.index }
        }

        AttentionReason.SyncRequiredBeforeNext -> {
            emptyList()
        }
    }

/**
 * Renders 1-based transfer indices (as produced by [affectedTransferIndices]) into the spec's
 * "Transfer 3–5" / "Transfer 3" style — a contiguous range when there's more than one, a single
 * number otherwise. `null` when [this] is empty (caller decides the fallback copy).
 */
fun List<Int>.toMigrationRangeText(): String? {
    if (isEmpty()) return null
    val sorted = sorted()
    val first = sorted.first() + 1
    val last = sorted.last() + 1
    return if (first == last) "$first" else "$first–$last"
}
