package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationState
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationTransfer
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_DUST_THRESHOLD_ZATOSHI
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_RESIDUAL_MIN_ZATOSHI
import co.electriccoin.zcash.ui.common.model.migration.MigrationAttentionKind
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferBlocker
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Characterization tests for two unverified home-banner gaps:
 *
 * 1. [AttentionReason.SyncRequiredBeforeNext] — not covered in [GetHomeMessageUseCaseMigrationTest].
 *    [migrationMessageFor] does produce a banner for this reason (the RequiresAttention branch
 *    fires), but [AttentionReason.SyncRequiredBeforeNext.toUiKind()] returns [MigrationAttentionKind.TRANSFER_EXPIRED]
 *    — the same value as [AttentionReason.TransferExpired] — even though the two have distinct
 *    semantics. The [MigrationAttention.kt] doc explicitly marks SyncRequiredBeforeNext "out of scope"
 *    for [toUiKind] and warns callers to filter on the reason type before calling it; this pinning
 *    test documents the current (probably wrong) mapping so any future intentional change is visible.
 *
 * 2. In-flight IMMEDIATE staleness — while a send-max IMMEDIATE sweep is in flight (broadcast but
 *    not yet confirmed) the banner still reads "Migrate required" because [migrationMessageFor] sees
 *    the pre-confirmation orchardBalance (still ≥ MIGRATION_RESIDUAL_MIN_ZATOSHI) combined with
 *    no-run, and fires the REQUIRED branch. The reactive [getOrchardBalance.observe()] fix
 *    (committed in the observeMigrationMessage() comment) only removes the *one-shot* problem;
 *    it does not remove the gap that exists between broadcast and first confirmation, during which
 *    balance is non-zero and the IMMEDIATE is invisible to [migrationMessageFor] (it bypasses the
 *    migration engine entirely — no MigrationState row, no plan row). These tests pin the current
 *    behavior (stale "Migrate required" banner during in-flight IMMEDIATE) as a documented known bug.
 */
class MigrationBannerStateTest {
    // ─── helpers shared with GetHomeMessageUseCaseMigrationTest ──────────────

    private fun snapshot() =
        LiveMigrationSnapshot(
            transfers =
                listOf(
                    LiveMigrationTransfer(
                        id = 10,
                        index = 0,
                        amountZatoshi = 500_000_000L,
                        scheduledHeight = 1_000L,
                        scheduledAt = kotlin.time.Instant.fromEpochSeconds(0),
                        isSent = true,
                        isProved = true,
                        action = null,
                        blocker = null,
                        expiryAt = null,
                        minedHeight = 1_005L,
                    ),
                    LiveMigrationTransfer(
                        id = 11,
                        index = 1,
                        amountZatoshi = 500_000_000L,
                        scheduledHeight = 2_000L,
                        scheduledAt = kotlin.time.Instant.fromEpochSeconds(4_000_000_000L),
                        isSent = false,
                        isProved = true,
                        action = null,
                        blocker = null,
                        expiryAt = null,
                        minedHeight = null,
                    ),
                ),
            preparations = emptyList(),
            tipHeight = 1_000L,
        )

    // ─── §1 SyncRequiredBeforeNext ────────────────────────────────────────────

    /**
     * FIXED (bug 1): [AttentionReason.SyncRequiredBeforeNext] must NOT surface an attention banner.
     *
     * [AttentionReason.SyncRequiredBeforeNext] means the previous transfer produced change back to
     * Orchard and the wallet must sync before the next transfer can spend it. This is a sync-wait
     * condition, NOT a transfer-expiry event. [toUiKind()] collapses it onto
     * [MigrationAttentionKind.TRANSFER_EXPIRED], which would have shown incorrect "Transfer expired"
     * copy. [migrationMessageFor] now explicitly excludes SyncRequiredBeforeNext from the
     * RequiresAttention branch (see [MigrationAttention.kt]'s doc marking it out of scope for
     * toUiKind, and CheckMigrationRecoveryUseCase which likewise does not route on this reason).
     *
     * With the fix it falls through: not InProgress, not Complete, and run active so the
     * residue/required branches (both gated on no-run) don't fire — the result is null (no
     * banner). Sync-required is transient: the engine keeps syncing and the ordinary in-progress /
     * no-message branches take over on the next read.
     */
    @Test
    fun syncRequiredBeforeNextWithPlanShowsNoAttentionBanner() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.SyncRequiredBeforeNext),
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 100_000L,
            )

        // SyncRequiredBeforeNext is filtered — no TRANSFER_EXPIRED (or any) attention banner is shown.
        assertNull(
            result,
            "SyncRequiredBeforeNext must not raise an attention banner — it is a transient sync-wait, not an expiry",
        )
    }

    /**
     * Defensive path: [AttentionReason.SyncRequiredBeforeNext] with a null plan bypasses the
     * RequiresAttention branch (which gates on `run active`) and falls through to the ordinary
     * orchardBalance decision. With a migratable balance this produces "Migrate required" — not an
     * attention banner.
     */
    @Test
    fun syncRequiredBeforeNextWithNullPlanAndMigratableBalanceFallsThroughToRequired() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.SyncRequiredBeforeNext),
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 200_000L,
            )
        // Falls through to the "orchardBalance >= min && no-run → Migrate required" branch.
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }

    /**
     * SyncRequiredBeforeNext with a null plan and a sub-migratable residual balance — falls
     * through to the RESIDUE branch rather than the RequiresAttention branch.
     */
    @Test
    fun syncRequiredBeforeNextWithNullPlanAndResidualBalanceFallsThroughToCompleted() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.SyncRequiredBeforeNext),
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L, // in the (dust, min) gap — residue
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = 500_000L,
            ),
            result,
        )
    }

    /**
     * SyncRequiredBeforeNext with a null plan and zero balance — nothing to migrate or report.
     */
    @Test
    fun syncRequiredBeforeNextWithNullPlanAndZeroBalanceShowsNothing() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.SyncRequiredBeforeNext),
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 0L,
            )
        assertNull(result)
    }

    // ─── §2 In-flight IMMEDIATE staleness ─────────────────────────────────────

    /**
     * CHARACTERIZATION (known bug — stale "Migrate required" banner during in-flight IMMEDIATE).
     *
     * An IMMEDIATE migration is a plain send-max sweep using [OrchardMigrationSdk.proposeImmediateMigration]
     * + the existing SubmitProposalUseCase pipeline. It bypasses the migration engine entirely:
     *   - No MigrationState row is written → sdkState stays at whatever it was before (null / NotStarted).
     *   - No MigrationPlan is persisted → plan stays null.
     *
     * While the sweep is in-flight (broadcast but not yet confirmed):
     *   - orchardBalanceZatoshi is still the PRE-broadcast value (≥ MIGRATION_RESIDUAL_MIN_ZATOSHI).
     *   - sdkState == null (or NotStarted), no-run.
     *   - → [migrationMessageFor] fires the "orchardBalance >= min && no-run" branch: "Migrate required".
     *
     * The reactive [getOrchardBalance.observe()] fix (see observeMigrationMessage()) eliminated the
     * one-shot stale problem where the balance was read once and never updated. But it does NOT
     * eliminate this gap: for the entire duration between broadcast and first confirmation, the balance
     * is non-zero (unconfirmed funds are still counted as part of orchardBalance until the tx mines),
     * so the banner keeps firing. This is the "0.000 + Migrate required while IMMEDIATE in flight" bug
     * described in project_migration_banner_stale_immediate.md.
     *
     * This test PINS the current (buggy) behavior. A fix must make [migrationMessageFor]
     * (or its callers) aware that an IMMEDIATE is in flight — e.g. via a dedicated flag or
     * by routing IMMEDIATE through the plan/MigrationState machinery — and return null in that window.
     */
    @Test
    fun inFlightImmediateWithNonZeroBalanceShowsRequiredBanner_CHARACTERIZATION_BUG() {
        // Simulates: IMMEDIATE broadcast, not yet confirmed; balance still shows the full amount.
        // sdkState is null (IMMEDIATE never writes to the migration engine).
        // plan is null (IMMEDIATE never persists an app-side plan).
        val inFlightOrchardBalance = MIGRATION_RESIDUAL_MIN_ZATOSHI + 5_000_000L // 0.06 ZEC, still reported

        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = inFlightOrchardBalance,
            )

        // BUG: Banner fires "Migrate required" even though the sweep is already broadcast.
        // Expected (once fixed): null — no banner should show while IMMEDIATE is in flight.
        assertEquals(
            MigrationHomeMessageData(isRunActive = false),
            result,
            "BUG: 'Migrate required' banner fires for in-flight IMMEDIATE. " +
                "Expected null (no banner) — fix must track in-flight IMMEDIATE state.",
        )
    }

    /**
     * CHARACTERIZATION: IMMEDIATE in-flight with exactly the migratable minimum balance — same bug.
     * Pins the boundary condition.
     */
    @Test
    fun inFlightImmediateAtExactMinimumBalanceShowsRequiredBanner_CHARACTERIZATION_BUG() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI,
            )
        // BUG — same as above, boundary case.
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }

    /**
     * After the IMMEDIATE confirms, the Orchard balance drops to zero (all funds swept to Ironwood).
     * At that point [migrationMessageFor] correctly returns null — no banner.
     * This test documents the correct, post-confirmation behavior for contrast with the in-flight bug tests above.
     */
    @Test
    fun immediateConfirmedZeroBalanceShowsNothing() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 0L,
            )
        assertNull(result, "After IMMEDIATE confirms and balance is 0, no banner should show")
    }

    /**
     * After the IMMEDIATE confirms, a sub-dust residual leftover (e.g. due to fee) is treated as
     * "migration completed" — the residue flow, not "Migrate required". This is the correct end-state.
     */
    @Test
    fun immediateConfirmedWithDustResidualShowsCompleted() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI, // exactly at threshold — shows nothing
            )
        // At or below the dust threshold: no banner (it is negligible dust).
        assertNull(result)
    }

    /**
     * After IMMEDIATE confirms but leaves a residue in the (dust, min) gap — shows completed/residue
     * banner, not "Migrate required". Correct end-state — contrast with the in-flight tests above.
     */
    @Test
    fun immediateConfirmedWithResidualInGapShowsCompleted() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L, // in (dust, min) gap — residue, un-migratable
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = 500_000L,
            ),
            result,
        )
    }

    // ─── §3 Cross-branch: SyncRequiredBeforeNext during InProgress ────────────

    /**
     * Verifies that when the SDK reports InProgress while SyncRequiredBeforeNext is present as an
     * attention reason on a *previous* read (i.e. the state already advanced back to InProgress),
     * the banner shows the ordinary InProgress message — not an attention banner.
     * Guards against accidental attention-kind leakage from a prior state into the InProgress branch.
     */
    @Test
    fun inProgressStateOverridesSyncRequiredBeforeNextAttentionKindIfPresent() {
        val progress = MigrationProgress(completedTransfers = 1, totalTransfers = 3, nextTransferReadyAtHeight = null)
        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(progress),
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 1_000_000L,
                // attentionKind/attentionRangeText are only populated by the caller when sdkState is
                // RequiresAttention — passing them here verifies the InProgress branch ignores them.
                attentionKind = MigrationAttentionKind.TRANSFER_EXPIRED,
                attentionRangeText = "1",
            )
        // Must be a plain InProgress banner with no attention fields.
        assertEquals(MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 2), result)
        assertNull(result?.attentionKind)
        assertNull(result?.attentionRangeText)
    }

    // ─── §4 SIGNATURE-blocked committed schedule routes through "Migration required" ──

    /**
     * Found live 2026-08-02: a committed-but-never-signed Keystone schedule (every transaction
     * stuck in [MigrationTransferBlocker.SIGNATURE] — the one blocker that can never self-resolve,
     * unlike SCHEDULE/ANCHOR_BOUNDARY/DEPENDENCIES which clear as blocks mine) was classified as
     * plain InProgress, routing to the read-only Progress screen with no way to re-trigger signing.
     * [migrationMessageFor] must instead report isRunActive = false, exactly like a fresh
     * "Migrate now" case, so the click-routing (`onMigrationMessageClick`) sends the user through
     * the ordinary Setup/Review/Keystone-sign flow — which the engine's own `commit_or_reuse`
     * safely re-enters (it returns the SAME already-committed PCZTs rather than re-committing).
     */
    @Test
    fun inProgressWithSignatureBlockedTransferShowsMigrationRequired() {
        val progress = MigrationProgress(completedTransfers = 0, totalTransfers = 2, nextTransferReadyAtHeight = null)
        val signatureBlockedSnapshot =
            snapshot().copy(
                transfers =
                    snapshot().transfers.map {
                        if (!it.isSent) it.copy(blocker = MigrationTransferBlocker.SIGNATURE) else it
                    },
            )
        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(progress),
                snapshot = signatureBlockedSnapshot,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 1_000_000L,
            )
        // Still the InProgress branch (isRunActive flips false, but completedCount/totalCount are
        // still populated from the snapshot) — NOT the bare "Migrate now" no-run branches further
        // down, which never fire because `when` returns on the first match.
        assertEquals(MigrationHomeMessageData(isRunActive = false, completedCount = 1, totalCount = 2), result)
    }

    /**
     * Same signature-blocked condition must also suppress the "ready to send" branch — a
     * signature-blocked transfer is not ready to send, it is blocked, regardless of how the other
     * ready-to-send preconditions (background execution unavailable, no overdue transfers, due
     * height reached) evaluate.
     */
    @Test
    fun readyToSendPreconditionsDoNotFireWhenSignatureBlocked() {
        val progress = MigrationProgress(completedTransfers = 0, totalTransfers = 2, nextTransferReadyAtHeight = null)
        val signatureBlockedSnapshot =
            snapshot().copy(
                transfers =
                    snapshot().transfers.map {
                        if (!it.isSent) {
                            it.copy(
                                blocker = MigrationTransferBlocker.SIGNATURE,
                                scheduledAt = kotlin.time.Instant.fromEpochSeconds(0), // already due
                            )
                        } else {
                            it
                        }
                    },
            )
        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(progress),
                snapshot = signatureBlockedSnapshot,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 1_000_000L,
                isBackgroundExecutionAvailable = false,
                hasOverdueTransfers = false,
            )
        // Full equality also asserts isReadyToSend == false (the data class default) — the
        // ready-to-send branch must not have fired despite its other preconditions being met.
        assertEquals(MigrationHomeMessageData(isRunActive = false, completedCount = 1, totalCount = 2), result)
    }

    // ─── §5 Per-note dust gate (Kris Nuttycombe's formula) ────────────────────

    /**
     * A raw Orchard balance above [MIGRATION_DUST_THRESHOLD_ZATOSHI] can still be true dust if it's
     * made up of notes too small individually to be worth spending net of MARGINAL_FEE — e.g. many
     * sub-MARGINAL_FEE notes, or few notes just barely above MARGINAL_FEE. In that case the SDK's
     * migratableOrchardTotal() (per-note, net of MARGINAL_FEE) reports at or below the threshold
     * even though the raw balance does not. [migrationMessageFor] must gate the residue banner on
     * the per-note total, not the raw balance — otherwise it prompts to migrate/lock a balance that
     * costs more to move than it's worth.
     */
    @Test
    fun rawBalanceAboveDustButPerNoteTotalAtDustShowsNothing() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 50_000L, // raw: well above dust
                migratableOrchardTotalZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI, // per-note: at the threshold
            )
        // No banner — the per-note total is not strictly above the threshold, even though the raw
        // balance is. Falls through both the residue branch (gated on the per-note total) and the
        // "Migrate now" branch (raw balance is still below MIGRATION_RESIDUAL_MIN_ZATOSHI).
        assertNull(result)
    }

    /**
     * Contrast case: when the per-note total genuinely exceeds the threshold, the residue banner
     * fires exactly as before, and still displays the raw balance (not the net-of-fee total) as
     * [MigrationHomeMessageData.residualBalanceZatoshi] — the user should see what they actually
     * hold, not the fee-adjusted figure used only for the internal gate.
     */
    @Test
    fun rawBalanceAndPerNoteTotalBothAboveDustShowsResidueWithRawBalanceDisplayed() {
        val rawBalance = MIGRATION_DUST_THRESHOLD_ZATOSHI + 50_000L
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = rawBalance,
                migratableOrchardTotalZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 1L,
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = rawBalance,
            ),
            result,
        )
    }

    /**
     * Omitting [migrationMessageFor]'s migratableOrchardTotalZatoshi parameter defaults it to
     * orchardBalanceZatoshi — i.e. legacy raw-balance behavior — so every pre-existing call site in
     * this file and [GetHomeMessageUseCaseMigrationTest] that doesn't pass it keeps its original
     * meaning unchanged.
     */
    @Test
    fun migratableOrchardTotalZatoshiDefaultsToRawBalance() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L, // in (dust, min) gap — residue, per legacy behavior
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = 500_000L,
            ),
            result,
        )
    }

    // ─── §6 RESIDUE gated on isFullySynced (mid-sync race) ────────────────────

    /**
     * Neal's repro (Slack, 2026-08-24): a wallet that was already fully swept elsewhere (0 ZEC)
     * but hadn't finished syncing this instance showed a bogus "X ZEC left in Orchard" residue
     * popup, and its "Migrate anyway" action failed with "no Orchard input found for TXID..." —
     * orchardBalanceZatoshi and migratableOrchardTotalZatoshi are two independent reads of the
     * same, still-changing DB mid-sync and can disagree window-to-window. The residue branch must
     * not fire until the wallet has caught up with the chain tip.
     */
    @Test
    fun residueBranchSuppressedWhileNotFullySynced() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L, // in (dust, min) gap — would be residue if synced
                migratableOrchardTotalZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 1L,
                isFullySynced = false,
            )
        assertNull(result, "Residue banner must not fire while the wallet is still mid-sync")
    }

    /**
     * Contrast case: identical inputs, but the wallet is fully synced — the residue banner fires
     * exactly as before. Confirms the new gate doesn't change behavior once sync has caught up.
     */
    @Test
    fun residueBranchFiresOnceFullySynced() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L,
                migratableOrchardTotalZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 1L,
                isFullySynced = true,
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = 500_000L,
            ),
            result,
        )
    }

    /**
     * The plain "Migrate now" branch (raw balance ≥ [MIGRATION_RESIDUAL_MIN_ZATOSHI], no run) is
     * deliberately NOT gated on [isFullySynced] — unlike the residue branch it only surfaces a CTA
     * (no one-click transfer proposal reading stale notes), so the same mid-sync race isn't
     * user-visible there. This pins that it still fires while mid-sync.
     */
    @Test
    fun migrateNowBranchStillFiresWhileNotFullySynced() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 1_000_000L,
                isFullySynced = false,
            )
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }

    /**
     * A null [orchardBalanceZatoshi] (balance snapshot not loaded yet) must not be treated as a
     * zero balance — coercing it to zero would make the celebration/residue/"Migrate now" branches
     * fire (or fail to fire) on a value the wallet never actually reported. With no run active and
     * no attention condition, an unknown balance must show no banner at all.
     */
    @Test
    fun nullOrchardBalanceWithNoRunShowsNothing() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = null,
            )
        assertNull(result, "A not-yet-loaded balance must not be coerced to zero and must show no banner")
    }

    /**
     * Same as above, but the engine reports Complete and the celebration hasn't been seen yet — the
     * celebration branch requires a known balance below the residual minimum; a null balance must
     * not satisfy that (0 < MIGRATION_RESIDUAL_MIN_ZATOSHI would incorrectly fire it if coerced).
     */
    @Test
    fun nullOrchardBalanceWithCompleteStateDoesNotFireCelebration() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.Complete,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = null,
            )
        assertNull(result, "A not-yet-loaded balance must not fire the celebration branch")
    }

    /**
     * A null balance must not suppress the RequiresAttention branch above it — that branch never
     * reads the balance at all.
     */
    @Test
    fun nullOrchardBalanceDoesNotSuppressRequiresAttentionBanner() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.TransferExpired),
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = null,
            )
        assertEquals(MigrationAttentionKind.TRANSFER_EXPIRED, result?.attentionKind)
    }

    /**
     * A null balance must not suppress the plain InProgress branch above it either — it also never
     * reads the balance.
     */
    @Test
    fun nullOrchardBalanceDoesNotSuppressInProgressBanner() {
        val progress = MigrationProgress(completedTransfers = 1, totalTransfers = 3, nextTransferReadyAtHeight = null)
        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(progress),
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = null,
            )
        assertEquals(MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 2), result)
    }
}
