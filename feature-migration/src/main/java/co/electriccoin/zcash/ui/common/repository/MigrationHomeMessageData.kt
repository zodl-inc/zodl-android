package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.migration.MigrationAttentionKind

/**
 * The migration home-banner payload (formerly ui-lib's `HomeMessageData.Migration`) — the concrete
 * [MigrationHomeMessage] the feature module emits through `MigrationHomeMessageSource`.
 *
 * Carries only DERIVED live data (crossings-only counts from the engine's transfer states) — the
 * app persists nothing about the plan (see `spec/2026-07-30-plan-cache-elimination-proposal.md`).
 * [isRunActive] is true when the engine holds a committed, not-yet-finished run (drives the
 * Progress-vs-Setup routing the old `plan != null` check used to).
 */
data class MigrationHomeMessageData(
    val isRunActive: Boolean,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isComplete: Boolean = false,
    // Spec §6.4 "Transfer Ready to Send": true when the next pending transfer's scheduled time
    // has arrived, background execution is unavailable, and the SDK doesn't yet count it as
    // overdue — a narrower, earlier window than the general missed-transfer/overdue state.
    val isReadyToSend: Boolean = false,
    // Non-null exactly when the SDK's MigrationState is RequiresAttention (spec §6.2/§6.3) —
    // see MigrationAttentionKind's doc for why the two causes must never collapse into one
    // generic message again. attentionRangeText is only meaningful for TRANSFER_EXPIRED.
    val attentionKind: MigrationAttentionKind? = null,
    val attentionRangeText: String? = null,
    // MOB-1750: true when this Complete banner comes from the RESIDUE branch (a small leftover
    // Orchard balance not tied to an unseen in-app migration celebration) rather than the one-time
    // post-migration celebration — drives both the home banner's "X ZEC left in Orchard" copy and
    // MigrationCompleteScreen's reduced summary card (no Transfers/Duration rows).
    val isResidueOnly: Boolean = false,
    // The live Orchard balance at decision time. Only meaningful when [isResidueOnly] is true — the
    // home banner needs the amount for its title; the celebration branch's copy is static.
    val residualBalanceZatoshi: Long = 0L,
) : MigrationHomeMessage()
