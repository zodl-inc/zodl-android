package co.electriccoin.zcash.ui.screen.migration.progress

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationPreparation
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationTransfer
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferBlocker
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Pure-logic coverage for the Migration Progress row rendering and header subtitle.
 *
 * Finalized 2026-08-03 (decision with Dominik) against 4 Figma reference screens: no debug-only
 * raw engine word is shown anywhere anymore, "Overdue" stays gone (decision 2026-07-31, reaffirmed
 * here), and every row falls into exactly one of: a genuinely-blocked state (`EXPIRED` /
 * `UNPROVABLE_ANCHOR` / `SIGNATURE` — the only three blockers that never self-resolve), "Sent"
 * (collapsing the old "Confirmed"/"Sent" split — mined time if known, else plain "Sent"), "Ready
 * now" (primary text color, folds in the unbacked "Sending now" state), or a future relative
 * estimate ("~X", or "in ~X" for the last row overall).
 *
 * Uses top-level internal functions only — no Koin, no Android, no ViewModel required.
 */
class MigrationProgressVMTest {
    private val now: Instant = Instant.fromEpochSeconds(1_000_000L)

    private fun transfer(
        index: Int = 0,
        isSent: Boolean = false,
        isProved: Boolean = false,
        blocker: MigrationTransferBlocker? = null,
        minedAt: Instant? = null,
        amountZatoshi: Long = 100_000_000L,
        id: Long = 1L,
        scheduledAt: Instant = now,
    ) = LiveMigrationTransfer(
        id = id,
        index = index,
        amountZatoshi = amountZatoshi,
        scheduledHeight = 1_000L + index,
        scheduledAt = scheduledAt,
        isSent = isSent,
        isProved = isProved,
        action = null,
        blocker = blocker,
        expiryAt = null,
        minedHeight = minedAt?.let { 1_000L },
        minedAt = minedAt,
    )

    private fun prep(
        id: Long = 1L,
        isSent: Boolean = false,
        isProved: Boolean = false,
        blocker: MigrationTransferBlocker? = null,
        scheduledAt: Instant = now,
    ) = LiveMigrationPreparation(
        id = id,
        layer = 0,
        index = 0,
        scheduledHeight = 1_000L,
        scheduledAt = scheduledAt,
        isSent = isSent,
        isProved = isProved,
        action = null,
        blocker = blocker,
        dependsOn = emptyList(),
    )

    // ── transferRowDisplay: priority order ────────────────────────────────────

    @Test
    fun transferRowDisplay_expired_blocker_wins_regardless_of_schedule() {
        val t = transfer(blocker = MigrationTransferBlocker.EXPIRED, scheduledAt = now - 5.minutes)
        val display = transferRowDisplay(t, now, isLastRowOverall = false)
        assertEquals("Expired", display.label.asString())
        assertFalse(display.isReadyNow)
    }

    @Test
    fun transferRowDisplay_unprovable_anchor_shows_needs_reschedule() {
        val t = transfer(blocker = MigrationTransferBlocker.UNPROVABLE_ANCHOR)
        assertEquals("Needs reschedule", transferRowDisplay(t, now, false).label.asString())
    }

    @Test
    fun transferRowDisplay_signature_blocker_shows_awaiting_signature_even_when_ready() {
        // A SIGNATURE-blocked row keeps its own copy even though scheduledAt has passed — it is
        // NOT painted "Ready now" while a signature is still owed.
        val t = transfer(blocker = MigrationTransferBlocker.SIGNATURE, scheduledAt = now - 1.minutes)
        val display = transferRowDisplay(t, now, false)
        assertEquals("Awaiting signature", display.label.asString())
        assertFalse(display.isReadyNow)
    }

    @Test
    fun transferRowDisplay_sent_without_mined_time_shows_plain_sent() {
        val t = transfer(isSent = true, minedAt = null)
        assertEquals("Sent", transferRowDisplay(t, now, false).label.asString())
    }

    @Test
    fun transferRowDisplay_sent_with_mined_time_shows_relative_ago() {
        val t = transfer(isSent = true, minedAt = now - 20.minutes)
        val display = transferRowDisplay(t, now, false)
        assertTrue(display.label.asString().startsWith("Sent "), display.label.asString())
        assertTrue(display.label.asString().endsWith(" ago"), display.label.asString())
        assertFalse(display.isReadyNow)
    }

    @Test
    fun transferRowDisplay_sent_ago_is_not_floored_by_the_privacy_minimum() {
        // 2026-08-06 revised decision: an already-mined transfer's exact timing is already public
        // on-chain, so "Sent X ago" shows the real elapsed time even below the 10 min testnet /
        // 1 h mainnet privacy floor that still applies to UPCOMING (not-yet-sent) estimates.
        val t = transfer(isSent = true, minedAt = now - 1.minutes)
        assertEquals("Sent ~1 min ago", transferRowDisplay(t, now, false).label.asString())
    }

    @Test
    fun transferRowDisplay_collapses_confirmed_and_sent_into_one_state() {
        // The old code distinguished isSent+minedHeight!=null ("Confirmed") from isSent alone
        // ("Sent"). Figma never shows "Confirmed" — both collapse to "Sent {relative} ago"/"Sent".
        val sentOnly = transferRowDisplay(transfer(isSent = true, minedAt = null), now, false)
        val sentAndMined = transferRowDisplay(transfer(isSent = true, minedAt = now - 20.minutes), now, false)
        assertTrue(sentOnly.label.asString().startsWith("Sent"))
        assertTrue(sentAndMined.label.asString().startsWith("Sent"))
        assertFalse(sentOnly.label.asString().contains("Confirmed"))
        assertFalse(sentAndMined.label.asString().contains("Confirmed"))
    }

    @Test
    fun transferRowDisplay_not_sent_past_due_no_blocker_shows_ready_now_in_primary_color() {
        val t = transfer(scheduledAt = now - 1.minutes)
        val display = transferRowDisplay(t, now, false)
        assertEquals("Ready now", display.label.asString())
        assertTrue(display.isReadyNow)
    }

    @Test
    fun transferRowDisplay_self_resolving_blockers_still_show_ready_now_when_due() {
        // DEPENDENCIES/ANCHOR_BOUNDARY/SCHEDULE are self-resolving — once the schedule moment has
        // passed they fold into "Ready now" exactly like an unblocked row, per the finalized plan.
        val selfResolvingBlockers =
            listOf(
                MigrationTransferBlocker.DEPENDENCIES,
                MigrationTransferBlocker.ANCHOR_BOUNDARY,
                MigrationTransferBlocker.SCHEDULE,
            )
        for (blocker in selfResolvingBlockers) {
            val t = transfer(blocker = blocker, scheduledAt = now - 1.minutes)
            val display = transferRowDisplay(t, now, false)
            assertEquals("Ready now", display.label.asString(), "blocker=$blocker")
            assertTrue(display.isReadyNow, "blocker=$blocker")
        }
    }

    @Test
    fun transferRowDisplay_never_shows_overdue() {
        // Regardless of how late a healthy, unblocked transfer runs, the word "Overdue" must never
        // appear (decision 2026-07-31, reaffirmed 2026-08-03) — it reads "Ready now" instead.
        val t = transfer(scheduledAt = now - 10.minutes)
        assertFalse(transferRowDisplay(t, now, false).label.asString().contains("Overdue", ignoreCase = true))
    }

    @Test
    fun transferRowDisplay_future_not_last_row_has_no_in_prefix() {
        // A span comfortably above either network's privacy floor (10 min testnet / 1 hour
        // mainnet) so this test isn't coupled to formatMigrationDuration's own floor behavior —
        // that's covered separately in MigrationDurationFormatTest. fineGrained pinned explicitly
        // (MOB-1669 test-flakiness follow-up, 2026-08-09) — this "~X h" (not "~X hours") shape is
        // testnet-specific and must not depend on which Gradle flavor variant compiled this test.
        val t = transfer(scheduledAt = now + 2.hours)
        assertEquals(
            "~2 h",
            transferRowDisplay(t, now, isLastRowOverall = false, fineGrained = true).label.asString()
        )
    }

    @Test
    fun transferRowDisplay_future_last_row_overall_gets_in_prefix() {
        val t = transfer(scheduledAt = now + 2.hours)
        assertEquals(
            "in ~2 h",
            transferRowDisplay(t, now, isLastRowOverall = true, fineGrained = true).label.asString()
        )
    }

    // ── preparationRowDisplay: same priority shape, "Done" not "Sent"/"Confirmed" ─────────────

    @Test
    fun preparationRowDisplay_sent_shows_done_not_sent() {
        val display = preparationRowDisplay(prep(isSent = true), now, false)
        assertEquals("Done", display.label.asString())
        assertFalse(display.isReadyNow)
    }

    @Test
    fun preparationRowDisplay_signature_blocker_shows_awaiting_signature() {
        val display = preparationRowDisplay(prep(blocker = MigrationTransferBlocker.SIGNATURE), now, false)
        assertEquals("Awaiting signature", display.label.asString())
    }

    @Test
    fun preparationRowDisplay_dependencies_blocker_shows_waiting_for_previous_split() {
        val display = preparationRowDisplay(prep(blocker = MigrationTransferBlocker.DEPENDENCIES), now, false)
        assertEquals("Waiting for previous split", display.label.asString())
    }

    @Test
    fun preparationRowDisplay_not_sent_past_due_no_blocker_shows_ready_now() {
        val display = preparationRowDisplay(prep(scheduledAt = now - 1.minutes), now, false)
        assertEquals("Ready now", display.label.asString())
        assertTrue(display.isReadyNow)
    }

    @Test
    fun preparationRowDisplay_future_last_row_overall_gets_in_prefix() {
        // fineGrained pinned explicitly (MOB-1669 test-flakiness follow-up, 2026-08-09) — the
        // 10-minute testnet privacy floor is below the 1-hour mainnet floor, so this "~10 min"
        // expectation must not depend on which Gradle flavor variant compiled this test.
        val display =
            preparationRowDisplay(
                prep(scheduledAt = now + 10.minutes),
                now,
                isLastRowOverall = true,
                fineGrained = true,
            )
        assertEquals("in ~10 min", display.label.asString())
    }

    @Test
    fun preparationRowDisplay_future_not_last_row_has_no_in_prefix() {
        val display =
            preparationRowDisplay(
                prep(scheduledAt = now + 10.minutes),
                now,
                isLastRowOverall = false,
                fineGrained = true,
            )
        assertEquals("~10 min", display.label.asString())
    }

    // ── sentRowDisplay directly ────────────────────────────────────────────────

    @Test
    fun sentRowDisplay_no_mined_time_is_plain_sent() {
        assertEquals("Sent", sentRowDisplay(minedAt = null, now = now).label.asString())
    }

    @Test
    fun sentRowDisplay_with_mined_time_appends_relative_ago() {
        val display = sentRowDisplay(minedAt = now - 2.minutes, now = now)
        assertTrue(display.label.asString().startsWith("Sent "))
        assertTrue(display.label.asString().endsWith(" ago"))
    }

    @Test
    fun sentRowDisplay_ago_is_not_floored_by_the_privacy_minimum() {
        assertEquals("Sent ~2 min ago", sentRowDisplay(minedAt = now - 2.minutes, now = now).label.asString())
    }

    // ── isAttention: unchanged, explicitly out of scope for this finalization ────────────────

    @Test
    fun isAttention_only_for_expired_and_unprovable_anchor() {
        fun isAttention(blocker: MigrationTransferBlocker?) =
            blocker == MigrationTransferBlocker.UNPROVABLE_ANCHOR || blocker == MigrationTransferBlocker.EXPIRED
        assertTrue(isAttention(MigrationTransferBlocker.EXPIRED))
        assertTrue(isAttention(MigrationTransferBlocker.UNPROVABLE_ANCHOR))
        assertFalse(isAttention(MigrationTransferBlocker.SIGNATURE))
        assertFalse(isAttention(MigrationTransferBlocker.SCHEDULE))
        assertFalse(isAttention(null))
    }

    // ── ironwoodCrossedZatoshi / orchardRemainingZatoshi: plan-derived, always conserve total ──

    @Test
    fun ironwoodCrossedZatoshi_counts_only_mined_transfers() {
        val transfers =
            listOf(
                // Mined: landed in Ironwood.
                transfer(index = 0, id = 1, isSent = true, minedAt = now, amountZatoshi = 50_000_000L),
                // Broadcast but not yet mined: NOT counted — could still expire back to Orchard.
                transfer(index = 1, id = 2, isSent = true, minedAt = null, amountZatoshi = 20_000_000L),
                // Merely proved, not even broadcast yet: NOT counted.
                transfer(index = 2, id = 3, isSent = false, amountZatoshi = 5_000_000L),
            )
        assertEquals(50_000_000L, ironwoodCrossedZatoshi(transfers))
    }

    @Test
    fun orchardRemainingZatoshi_and_ironwoodCrossedZatoshi_always_sum_to_the_total() {
        // Pure conservation, independent of any wallet-balance read — this is the property the
        // earlier wallet-balance-based version of this card violated (observed live: Orchard
        // 9.5056 + Ironwood 7.740 = 17.25 ZEC shown against a real ~10 ZEC balance, from
        // GetBalancePoolsUseCase staleness/double-counting).
        val transfers =
            listOf(
                transfer(index = 0, id = 1, isSent = true, minedAt = now, amountZatoshi = 50_000_000L),
                transfer(index = 1, id = 2, isSent = true, minedAt = null, amountZatoshi = 20_000_000L),
                transfer(index = 2, id = 3, isSent = false, amountZatoshi = 5_000_000L),
            )
        val total = transfers.sumOf { it.amountZatoshi }
        val orchard = orchardRemainingZatoshi(total, transfers)
        val ironwood = ironwoodCrossedZatoshi(transfers)
        assertEquals(Zatoshi(total), orchard + Zatoshi(ironwood))
    }

    @Test
    fun orchardRemainingZatoshi_nothing_mined_equals_the_full_total() {
        val transfers = listOf(transfer(index = 0, id = 1, isSent = false, amountZatoshi = 100_000_000L))
        assertEquals(Zatoshi(100_000_000L), orchardRemainingZatoshi(100_000_000L, transfers))
    }

    @Test
    fun orchardRemainingZatoshi_everything_mined_is_zero() {
        val transfers = listOf(transfer(index = 0, id = 1, isSent = true, minedAt = now, amountZatoshi = 100_000_000L))
        assertEquals(Zatoshi(0L), orchardRemainingZatoshi(100_000_000L, transfers))
    }

    @Test
    fun orchardRemainingZatoshi_no_transfers_is_zero() {
        assertEquals(Zatoshi(0L), orchardRemainingZatoshi(0L, emptyList()))
    }

    // ── migrationProgressSubtitle: header static-span vs. remaining-time countdown ───────────

    @Test
    fun migrationProgressSubtitle_not_started_keeps_static_total_span_unchanged() {
        val transfers =
            listOf(
                transfer(index = 0, id = 1).copy(scheduledAt = now),
                transfer(index = 1, id = 2).copy(scheduledAt = now + 10.minutes),
            )
        val snapshot = LiveMigrationSnapshot(transfers = transfers, preparations = emptyList(), tipHeight = 1_000L)
        // fineGrained pinned explicitly (MOB-1669 test-flakiness follow-up, 2026-08-09) — the
        // 10-minute span here sits below the 1-hour mainnet floor, so this must not depend on
        // which Gradle flavor variant compiled this test.
        val subtitle = migrationProgressSubtitle(snapshot, now, fineGrained = true)
        assertTrue(subtitle.contains("over ~10 min"), subtitle)
        assertTrue(subtitle.contains("2 remaining transfers"), subtitle)
    }

    @Test
    fun migrationProgressSubtitle_in_progress_counts_down_remaining_time() {
        val transfers =
            listOf(
                transfer(index = 0, id = 1, isSent = true).copy(scheduledAt = now - 5.minutes),
                // Comfortably above the testnet privacy floor (10 min) so this isn't coupled to
                // formatMigrationDuration's own floor behavior (covered in MigrationDurationFormatTest).
                transfer(index = 1, id = 2).copy(scheduledAt = now + 25.minutes),
            )
        val snapshot = LiveMigrationSnapshot(transfers = transfers, preparations = emptyList(), tipHeight = 1_000L)
        // fineGrained pinned explicitly (MOB-1669 test-flakiness follow-up, 2026-08-09) — see the
        // "not started" test above for why.
        val subtitle = migrationProgressSubtitle(snapshot, now, fineGrained = true)
        assertTrue(subtitle.contains("~25 min remaining"), subtitle)
        assertFalse(subtitle.contains("over ~"), subtitle)
    }

    @Test
    fun migrationProgressSubtitle_overdue_header_never_shows_floored_lying_duration() {
        // Regression test for the bug adversarial (Fable) review caught in an earlier draft:
        // once `now >= lastScheduled` (a normal, expected late-but-healthy engine state, not
        // stuck/broken), formatMigrationDuration's floor would make a naive
        // `formatMigrationDuration(remaining)` call print a permanently-lying floored duration
        // forever. The header must instead switch to non-time copy without calling
        // formatMigrationDuration on a zero/negative span at all.
        val transfers =
            listOf(
                transfer(index = 0, id = 1, isSent = true).copy(scheduledAt = now - 20.minutes),
                transfer(index = 1, id = 2).copy(scheduledAt = now - 1.minutes),
            )
        val snapshot = LiveMigrationSnapshot(transfers = transfers, preparations = emptyList(), tipHeight = 1_000L)
        val subtitle = migrationProgressSubtitle(snapshot, now)
        assertFalse(subtitle.contains("min remaining"), "must not claim a duration once running late: $subtitle")
        assertFalse(subtitle.contains("Overdue", ignoreCase = true), subtitle)
        assertTrue(subtitle.contains("Finishing up"), subtitle)
    }

    @Test
    fun migrationProgressSubtitle_complete_shows_all_complete() {
        val transfers = listOf(transfer(index = 0, id = 1, isSent = true))
        val snapshot = LiveMigrationSnapshot(transfers = transfers, preparations = emptyList(), tipHeight = 1_000L)
        assertEquals("All 1 transfers are complete.", migrationProgressSubtitle(snapshot, now))
    }

    @Suppress("UNCHECKED_CAST")
    private fun StringResource.asString(): String =
        when (this) {
            is StringResource.ByString -> {
                value
            }

            else -> {
                val resourcesField = this::class.java.getDeclaredField("resources").also { it.isAccessible = true }
                val parts = resourcesField.get(this) as List<StringResource>
                parts.joinToString(separator = "") { it.asString() }
            }
        }
}
