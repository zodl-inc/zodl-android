package co.electriccoin.zcash.ui.screen.migration.progress

import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationDetails
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationProgressState(
    val title: StringResource,
    val subtitle: StringResource,
    // Feeds the "Split Balance N" rows shown above the transfer timeline — one row per
    // note-split (preparation) transaction, rendered in broadcast order.
    val totalAmount: StringResource,
    val totalFiatAmount: StringResource? = null,
    // Only populated for a single (or zero) preparation — rendered as-is, one row per item. For
    // more than one preparation this is left empty and [preparationsSummary]/[preparationDetails]
    // drive a single collapsed "Split Balance" row + "Show details" sheet instead (Figma "PR App
    // Designs Q3'26" node 5207:16023, 2026-08-03) — mirrors MigrationReviewScreen's identical
    // collapse threshold, so both screens render a multi-step note-split the same way.
    val preparations: List<MigrationProgressPreparationState> = emptyList(),
    val preparationsSummary: MigrationProgressPreparationSummary? = null,
    val preparationDetails: MigrationPreparationDetails? = null,
    // Live Orchard → Ironwood balance-tracker card (Figma "PR App Designs Q3'26" node 3480:7638).
    // Null only until both live balances have resolved at least once.
    val balanceTracker: MigrationProgressBalanceTracker? = null,
    val transfers: List<MigrationProgressTransferState>,
    val isComplete: Boolean,
    val onBack: () -> Unit,
    val onDone: (() -> Unit)? = null,
)

/** The live Orchard (source) → Ironwood (destination) balance split shown above the timeline. */
data class MigrationProgressBalanceTracker(
    val orchardAmount: StringResource,
    val orchardFiatAmount: StringResource?,
    val ironwoodAmount: StringResource,
    val ironwoodFiatAmount: StringResource?,
)

/**
 * The collapsed "Split Balance" row shown instead of individual [MigrationProgressPreparationState]
 * rows once there's more than one preparation. [statusLabel]/[isReadyNow] mirror the single active
 * (first not-yet-sent) preparation's own row state; [isSent] is true only once every preparation
 * has broadcast, painting the row DONE.
 */
data class MigrationProgressPreparationSummary(
    val statusLabel: StringResource,
    val isReadyNow: Boolean,
    val isSent: Boolean,
)

/**
 * One note-split (preparation) transaction row in the Migration Progress timeline.
 *
 * [number] is 1-based display order (broadcast/schedule order). [statusLabel] is the row's only
 * label (2026-08-03 finalization: the DEBUG-only raw engine status suffix is gone) — a soft,
 * non-deadline-implying per-row time hint ("~5 min" / "in ~5 min" for the last row overall),
 * "Ready now", "Done", or a blocked-row phrase ("Awaiting signature" / "Waiting for previous
 * split"); never styled as a countdown-to-deadline, never "Overdue". [isReadyNow] is true only for
 * the "Ready now" state, which Figma renders in the primary text color instead of the muted gray
 * every other subtitle uses.
 */
data class MigrationProgressPreparationState(
    val number: Int,
    val statusLabel: StringResource,
    val isReadyNow: Boolean,
    val isSent: Boolean,
)

data class MigrationProgressTransferState(
    val index: Int,
    val amount: StringResource,
    // The row's only label — see [MigrationProgressPreparationState.statusLabel] doc.
    val statusLabel: StringResource,
    // True only for the "Ready now" state — primary text color instead of muted gray.
    val isReadyNow: Boolean,
    // Attention paint (orange) — genuine cannot-heal states only (expired / unprovable anchor),
    // never a merely-late-but-healthy transfer.
    val isAttention: Boolean,
    val isSent: Boolean,
    val fiatAmount: StringResource? = null,
)
