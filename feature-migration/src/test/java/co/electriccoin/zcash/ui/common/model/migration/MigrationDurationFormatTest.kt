package co.electriccoin.zcash.ui.common.model.migration

import cash.z.ecc.android.sdk.ext.ZcashSdk
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationDurationFormatTest {
    // Regression coverage for a real bug: TransferProposal's anchorHeight/nextExecutableAfterHeight
    // are block heights, not epoch seconds. Using them directly as (or against) a timestamp made
    // every scheduled transfer appear ~56 years overdue on a live device, since a raw block height
    // (~4.18M) measured back to the Unix epoch is decades in the past relative to any real 2026
    // Instant.

    @Test
    fun estimatedSecondsBetweenHeights_converts_block_delta_using_the_network_block_time() {
        val blocksApart = 288L // one ZIP-318-style transfer-scheduling step
        val expectedSeconds = blocksApart * (ZcashSdk.BLOCK_INTERVAL_MILLIS / 1000)

        val actual = estimatedSecondsBetweenHeights(fromHeight = 4_180_000L, toHeight = 4_180_000L + blocksApart)

        assertEquals(expectedSeconds, actual)
    }

    @Test
    fun estimatedSecondsBetweenHeights_is_zero_for_equal_heights() {
        assertEquals(0L, estimatedSecondsBetweenHeights(fromHeight = 4_180_000L, toHeight = 4_180_000L))
    }

    @Test
    fun estimatedSecondsBetweenHeights_is_negative_when_the_target_height_is_in_the_past() {
        // A transfer's own anchorHeight can be behind the height it's compared against (e.g. an
        // overdue transfer's nextExecutableAfterHeight is now below the current tip) — the caller
        // is responsible for clamping to zero/"overdue", this function must not do so itself.
        val actual = estimatedSecondsBetweenHeights(fromHeight = 4_180_100L, toHeight = 4_180_000L)

        assertEquals(-100L * (ZcashSdk.BLOCK_INTERVAL_MILLIS / 1000), actual)
    }

    @Test
    fun estimatedSecondsBetweenHeights_never_conflates_a_bare_height_with_epoch_seconds() {
        // The historical bug this guards against: passing a raw height straight through as if it
        // were already a duration/timestamp. A single real-world height (millions) must not survive
        // unconverted — it must always be scaled by the block interval.
        val height = 4_180_824L

        val secondsFromGenesis = estimatedSecondsBetweenHeights(fromHeight = 0L, toHeight = height)

        assertEquals(height * (ZcashSdk.BLOCK_INTERVAL_MILLIS / 1000), secondsFromGenesis)
        assertEquals(height * 75L, secondsFromGenesis)
    }

    @Test
    fun formatMigrationDuration_keeps_minute_resolution_above_an_hour_when_fine_grained() {
        assertEquals("~1 h 15 min", formatMigrationDuration(totalSeconds = 4_500L, fineGrained = true))
        assertEquals("~2 h", formatMigrationDuration(totalSeconds = 7_200L, fineGrained = true))
    }

    @Test
    fun formatMigrationDuration_uses_coarse_hours_when_not_fine_grained() {
        assertEquals("~1 hour", formatMigrationDuration(totalSeconds = 4_500L, fineGrained = false))
        assertEquals("~2 hours", formatMigrationDuration(totalSeconds = 8_000L, fineGrained = false))
    }

    @Test
    fun formatMigrationDuration_shows_minutes_below_an_hour_only_when_fine_grained() {
        assertEquals("~15 min", formatMigrationDuration(totalSeconds = 900L, fineGrained = true))
        // Not fine-grained (mainnet): floored at 1 hour, so a 15-minute span never surfaces as minutes.
        assertEquals("~1 hour", formatMigrationDuration(totalSeconds = 900L, fineGrained = false))
    }

    @Test
    fun formatMigrationDuration_never_reveals_a_duration_below_the_networks_privacy_floor() {
        // Never show anything more precise than 10 min on testnet / 1 hour on mainnet for an
        // UPCOMING estimate — 2026-08-03 privacy requirement, still the default (applyPrivacyFloor
        // defaults to true).
        assertEquals("~10 min", formatMigrationDuration(totalSeconds = 1L, fineGrained = true))
        assertEquals("~10 min", formatMigrationDuration(totalSeconds = 599L, fineGrained = true))
        assertEquals("~10 min", formatMigrationDuration(totalSeconds = 600L, fineGrained = true))
        assertEquals("~1 hour", formatMigrationDuration(totalSeconds = 1L, fineGrained = false))
        assertEquals("~1 hour", formatMigrationDuration(totalSeconds = 3_599L, fineGrained = false))
        assertEquals("~1 hour", formatMigrationDuration(totalSeconds = 3_600L, fineGrained = false))
    }

    @Test
    fun formatMigrationDuration_applyPrivacyFloor_false_reveals_the_exact_duration() {
        // 2026-08-06 revised decision — an already-mined transfer's "ago" label opts out: its exact
        // timing is already public on-chain, so there is no remaining privacy benefit to flooring it.
        assertEquals(
            "~1 min",
            formatMigrationDuration(totalSeconds = 60L, fineGrained = true, applyPrivacyFloor = false),
        )
        assertEquals(
            "~2 min",
            formatMigrationDuration(totalSeconds = 130L, fineGrained = false, applyPrivacyFloor = false),
        )
        assertEquals(
            "~0 min",
            formatMigrationDuration(totalSeconds = 0L, fineGrained = true, applyPrivacyFloor = false),
        )
    }

    @Test
    fun formatMigrationDuration_distinguishes_75min_from_119min_when_fine_grained() {
        // Issue 2: the progress screen's old inline branch bucketed to coarse integer hours and
        // dropped the minutes, so 75 min and 119 min both rendered "~1 hours". The shared formatter
        // the progress screen now calls keeps them distinct (and matches the Review screen).
        assertEquals("~1 h 15 min", formatMigrationDuration(totalSeconds = 75L * 60L, fineGrained = true))
        assertEquals("~1 h 59 min", formatMigrationDuration(totalSeconds = 119L * 60L, fineGrained = true))
    }

    // ── NU7 characterization pins (MOB-2287): pre-NU7 behaviour at the 75 s/block rate. These pass
    //    today and document what must stay true for pre-activation heights once the helper becomes
    //    height-aware (25 s post-NU7). Explicit secondsPerBlock = 75 so they survive the default change. ──

    @Test
    fun `characterization pin of pre-NU7 75s - default rate is 75 seconds per block`() {
        assertEquals(75L, ZcashSdk.BLOCK_INTERVAL_MILLIS / 1000)
        assertEquals(75L, estimatedSecondsBetweenHeights(fromHeight = 1_000L, toHeight = 1_001L))
    }

    @Test
    fun `characterization pin of pre-NU7 75s - estimatedSecondsBetweenHeights scales linearly with explicit 75`() {
        // NU7: revisit - spans activation once helper is height-aware
        assertEquals(
            288L * 75L,
            estimatedSecondsBetweenHeights(fromHeight = 4_180_000L, toHeight = 4_180_288L, secondsPerBlock = 75L),
        )
        // NU7: revisit - spans activation once helper is height-aware
        assertEquals(
            0L,
            estimatedSecondsBetweenHeights(fromHeight = 4_180_000L, toHeight = 4_180_000L, secondsPerBlock = 75L),
        )
        // NU7: revisit - spans activation once helper is height-aware
        assertEquals(
            -100L * 75L,
            estimatedSecondsBetweenHeights(fromHeight = 4_180_100L, toHeight = 4_180_000L, secondsPerBlock = 75L),
        )
    }

    @Test
    fun `characterization pin of pre-NU7 75s - 1152 blocks is exactly 24 hours`() {
        // NU7: revisit - spans activation once helper is height-aware
        val seconds = estimatedSecondsBetweenHeights(fromHeight = 5_000_000L, toHeight = 5_001_152L, secondsPerBlock = 75L)
        assertEquals(24L * 3600L, seconds)
        assertEquals("~24 hours", formatMigrationDuration(seconds, fineGrained = false))
        assertEquals("~24 h", formatMigrationDuration(seconds, fineGrained = true))
    }

    @Test
    fun `characterization pin of pre-NU7 75s - 3456 blocks is exactly 3 days`() {
        // NU7: revisit - spans activation once helper is height-aware
        val seconds = estimatedSecondsBetweenHeights(fromHeight = 0L, toHeight = 3_456L, secondsPerBlock = 75L)
        assertEquals(3L * 24L * 3600L, seconds)
        assertEquals("~72 hours", formatMigrationDuration(seconds, fineGrained = false))
    }

    @Test
    fun `characterization pin of pre-NU7 75s - sub-floor block spans surface as the privacy floor`() {
        // NU7: revisit - spans activation once helper is height-aware
        val fiveBlocks = estimatedSecondsBetweenHeights(fromHeight = 100L, toHeight = 105L, secondsPerBlock = 75L)
        assertEquals(375L, fiveBlocks)
        assertEquals("~10 min", formatMigrationDuration(fiveBlocks, fineGrained = true))
        assertEquals("~1 hour", formatMigrationDuration(fiveBlocks, fineGrained = false))
        // 48 blocks = 3600 s = exactly the mainnet floor boundary.
        val fortyEightBlocks = estimatedSecondsBetweenHeights(fromHeight = 100L, toHeight = 148L, secondsPerBlock = 75L)
        assertEquals(3_600L, fortyEightBlocks)
        assertEquals("~1 hour", formatMigrationDuration(fortyEightBlocks, fineGrained = false))
    }
}
