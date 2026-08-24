package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationState
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationTransfer
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_DUST_THRESHOLD_ZATOSHI
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_RESIDUAL_MIN_ZATOSHI
import co.electriccoin.zcash.ui.common.model.migration.MigrationAttentionKind
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class GetHomeMessageUseCaseMigrationTest {
    private fun transfer(
        id: Long,
        index: Int,
        scheduledAt: Instant,
        isSent: Boolean = false,
    ) = LiveMigrationTransfer(
        id = id,
        index = index,
        amountZatoshi = 100_000L,
        scheduledHeight = 1_000L + index,
        scheduledAt = scheduledAt,
        isSent = isSent,
        isProved = true,
        action = null,
        blocker = null,
        expiryAt = null,
        minedHeight = null,
    )

    /** A live snapshot with one sent and two pending transfers — counts (1, 3). */
    private fun snapshot(nextScheduledAt: Instant = Instant.fromEpochSeconds(0)) =
        LiveMigrationSnapshot(
            transfers =
                listOf(
                    transfer(id = 10, index = 0, scheduledAt = Instant.fromEpochSeconds(0), isSent = true),
                    transfer(id = 11, index = 1, scheduledAt = nextScheduledAt),
                    transfer(id = 12, index = 2, scheduledAt = nextScheduledAt),
                ),
            preparations = emptyList(),
            tipHeight = 1_000L,
        )

    @Test
    fun freshWalletWithNoRunAndNoBalanceShowsNothing() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 0L,
            )
        assertNull(result)
    }

    @Test
    fun freshWalletWithMigratableBalanceAndNoRunShowsRequired() {
        // A balance at or above the migratable minimum (0.01 ZEC) is genuinely migratable, so the
        // "Migrate now" prompt is correct — tapping it will produce a real proposal, not
        // NothingToMigrate.
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI,
            )
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }

    @Test
    fun freshWalletWithLargeBalanceAndNoRunShowsRequired() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 500_000L,
            )
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }

    @Test
    fun residueInGapWithNoRunShowsCompletedNotRequired() {
        // A leftover Orchard balance above the dust threshold but below the migratable minimum
        // (here 500_000 zat = 0.005 ZEC, the live-observed residue) is un-migratable —
        // proposeMigrationTransfers would return NothingToMigrate. It must be evaluated as
        // "migration completed" and route to the residue flow (lock / migrate-anyway), NOT shown
        // as "Migrate now".
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L,
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

    @Test
    fun residueJustBelowMinWithNoRunShowsCompleted() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI - 1L,
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI - 1L,
            ),
            result,
        )
    }

    @Test
    fun residueJustAboveDustWithNoRunShowsCompleted() {
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 1L,
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 1L,
            ),
            result,
        )
    }

    @Test
    fun freshWalletWithDustBalanceAndNoRunShowsNothing() {
        // Entry-banner gating uses the same dust threshold as completion gating (spec §9.9) — a
        // balance at or below it never needs a migration prompt of its own.
        val result =
            migrationMessageFor(
                sdkState = null,
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI,
            )
        assertNull(result)
    }

    @Test
    fun inProgressShowsInProgressBannerWithLiveCountsRegardlessOfBalance() {
        val migrationProgress =
            MigrationProgress(
                completedTransfers = 1,
                totalTransfers = 3,
                nextTransferReadyAtHeight = null,
            )
        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(migrationProgress),
                snapshot = snapshot(nextScheduledAt = Clock.System.now() + 30.minutes),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L,
            )
        assertEquals(
            MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 3),
            result,
        )
    }

    @Test
    fun completeUnseenWithSubMigratableBalanceShowsCompleteBanner() {
        val snap = snapshot()
        val result =
            migrationMessageFor(
                sdkState = MigrationState.Complete,
                snapshot = snap,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI - 1L,
            )
        assertEquals(
            MigrationHomeMessageData(isRunActive = false, completedCount = 1, totalCount = 3, isComplete = true),
            result,
        )
    }

    @Test
    fun completeWithMigratableResidualShowsRequiredNotComplete() {
        // The multi-round Keystone case, now fully live: the SDK's MigrationState reports Complete
        // as soon as the current round's transfers are all mined, even with a still-migratable
        // residual balance needing another round. The balance decides — "Migrate now" fires
        // directly (the old cleared-plan marker is gone; proposal §3: this mapping is correct
        // whether the balance is a next round's residual or newly received funds).
        val result =
            migrationMessageFor(
                sdkState = MigrationState.Complete,
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 500_000L,
            )
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }

    @Test
    fun completeWithSubMigratableResidueShowsCompleteBanner() {
        // A residual left after the final round that is above the dust threshold but below the
        // migratable minimum still counts as complete: the engine cannot migrate it, so the
        // completion/residue screen (lock / migrate-anyway) is the correct destination.
        val result =
            migrationMessageFor(
                sdkState = MigrationState.Complete,
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 500_000L,
            )
        assertEquals(
            MigrationHomeMessageData(isRunActive = false, completedCount = 1, totalCount = 3, isComplete = true),
            result,
        )
    }

    @Test
    fun completeWithZeroBalanceUnseenShowsCompleteBanner() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.Complete,
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 0L,
            )
        assertEquals(
            MigrationHomeMessageData(isRunActive = false, completedCount = 1, totalCount = 3, isComplete = true),
            result,
        )
    }

    @Test
    fun completeAcknowledgedShowsNothing() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.Complete,
                snapshot = snapshot(),
                hasSeenComplete = true,
                orchardBalanceZatoshi = 0L,
            )
        assertNull(result)
    }

    // --- Spec §6.4 "Transfer Ready to Send" ---

    @Test
    fun dueTransferWithoutBackgroundExecutionAndNotOverdueShowsReadyToSend() {
        val now = Clock.System.now()
        val readySnapshot = snapshot(nextScheduledAt = now - 1.minutes)
        val migrationProgress = MigrationProgress(1, 3, null)

        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(migrationProgress),
                snapshot = readySnapshot,
                hasSeenComplete = false,
                orchardBalanceZatoshi = 100_000L,
                isBackgroundExecutionAvailable = false,
                hasOverdueTransfers = false,
                now = now,
            )

        assertEquals(
            MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 3, isReadyToSend = true),
            result,
        )
    }

    @Test
    fun dueTransferButBackgroundExecutionAvailableShowsRegularInProgress() {
        // Background execution can run the WorkManager job itself — no need for the fallback
        // ready-to-send banner in that case.
        val now = Clock.System.now()
        val migrationProgress = MigrationProgress(1, 3, null)

        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(migrationProgress),
                snapshot = snapshot(nextScheduledAt = now - 1.minutes),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 100_000L,
                isBackgroundExecutionAvailable = true,
                hasOverdueTransfers = false,
                now = now,
            )

        assertEquals(
            MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 3),
            result,
        )
    }

    @Test
    fun dueTransferAlreadyOverdueShowsRegularInProgressNotReadyToSend() {
        // Once the SDK counts it as overdue, the Progress screen's Reschedule/Send-now flow takes
        // over — the ready-to-send banner is only for the narrower "just became due" window before
        // that.
        val now = Clock.System.now()
        val migrationProgress = MigrationProgress(1, 3, null)

        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(migrationProgress),
                snapshot = snapshot(nextScheduledAt = now - 1.minutes),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 100_000L,
                isBackgroundExecutionAvailable = false,
                hasOverdueTransfers = true,
                now = now,
            )

        assertEquals(
            MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 3),
            result,
        )
    }

    @Test
    fun notYetDueTransferWithoutBackgroundExecutionShowsRegularInProgress() {
        val now = Clock.System.now()
        val migrationProgress = MigrationProgress(1, 3, null)

        val result =
            migrationMessageFor(
                sdkState = MigrationState.InProgress(migrationProgress),
                snapshot = snapshot(nextScheduledAt = now + 30.minutes),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 100_000L,
                isBackgroundExecutionAvailable = false,
                hasOverdueTransfers = false,
                now = now,
            )

        assertEquals(
            MigrationHomeMessageData(isRunActive = true, completedCount = 1, totalCount = 3),
            result,
        )
    }

    // Spec §6.2/§6.3 — the home banner must distinguish the two RequiresAttention causes instead
    // of returning null for both (the pre-fix behavior, which left the user with nothing on Home
    // if they backed out of the forced full-screen redirect).

    @Test
    fun requiresAttentionInvalidTransferShowsPlanUpdateBannerWithNoRange() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.InvalidTransfer(11L)),
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 300_000L,
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = true,
                completedCount = 1,
                totalCount = 3,
                attentionKind = MigrationAttentionKind.PLAN_UPDATE,
                attentionRangeText = null,
            ),
            result,
        )
    }

    @Test
    fun requiresAttentionTransferExpiredShowsTransferExpiredBannerWithPrecomputedRange() {
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.TransferExpired),
                snapshot = snapshot(),
                hasSeenComplete = false,
                orchardBalanceZatoshi = 300_000L,
                attentionKind = MigrationAttentionKind.TRANSFER_EXPIRED,
                attentionRangeText = "3–5",
            )
        assertEquals(
            MigrationHomeMessageData(
                isRunActive = true,
                completedCount = 1,
                totalCount = 3,
                attentionKind = MigrationAttentionKind.TRANSFER_EXPIRED,
                attentionRangeText = "3–5",
            ),
            result,
        )
    }

    @Test
    fun requiresAttentionWithNoSnapshotFallsThroughToOrdinaryLogicInsteadOfCrashing() {
        // Defensive case — RequiresAttention in practice always implies a committed run, but must
        // not NPE or otherwise misbehave if the statuses are somehow unavailable.
        val result =
            migrationMessageFor(
                sdkState = MigrationState.RequiresAttention(AttentionReason.TransferExpired),
                snapshot = null,
                hasSeenComplete = false,
                orchardBalanceZatoshi = MIGRATION_RESIDUAL_MIN_ZATOSHI + 200_000L,
            )
        assertEquals(MigrationHomeMessageData(isRunActive = false), result)
    }
}
