package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PercentDecimal
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetHomeMessageUseCaseSyncThresholdTest {
    private fun snapshot(
        blocksRemaining: Long,
        restoringState: WalletRestoringState = WalletRestoringState.SYNCING,
        progress: Float = 0.5f,
        behindSeconds: Long = -1L,
    ) = WalletSnapshot(
        status = Synchronizer.Status.SYNCING,
        progress = PercentDecimal(progress),
        synchronizerError = null,
        isSpendable = false,
        restoringState = restoringState,
        blocksRemaining = blocksRemaining,
        behindSeconds = behindSeconds,
    )

    @Test
    fun blocksRemainingAboveThresholdShowsSyncBanner() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 5000),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertEquals(HomeMessageData.Syncing(progress = 50f), result)
    }

    @Test
    fun blocksRemainingAtThresholdShowsSyncBanner() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = SYNCING_BANNER_HIDE_BELOW_BLOCKS),
                syncMessageShownBefore = false,
                someBalance = false
            )
        assertEquals(HomeMessageData.Syncing(progress = 50f), result)
    }

    @Test
    fun blocksRemainingBelowThresholdHidesSyncBanner() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 100),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertNull(result)
    }

    @Test
    fun blocksRemainingZeroHidesSyncBanner() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 0),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertNull(result)
    }

    @Test
    fun sentinelMinusOneHidesSyncBanner() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = -1L),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertNull(result)
    }

    @Test
    fun nonSyncingStatusReturnsNullRegardlessOfBlocksRemaining() {
        val snap = snapshot(blocksRemaining = 100_000).copy(status = Synchronizer.Status.SYNCED)
        val result = syncingMessageFor(snap, syncMessageShownBefore = false, someBalance = false)
        assertNull(result)
    }

    @Test
    fun thresholdNotAppliedAfterFirstShow() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 10),
                syncMessageShownBefore = true,
                someBalance = false,
            )
        assertEquals(HomeMessageData.Syncing(progress = 50f), result)
    }

    @Test
    fun restoringStateBypassesThresholdAndAlwaysShows() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 10, restoringState = WalletRestoringState.RESTORING),
                syncMessageShownBefore = false,
                someBalance = true,
            )
        assertEquals(HomeMessageData.Restoring(isSpendable = false, progress = 50f), result)
    }

    @Test
    fun resyncingStateBypassesThresholdAndAlwaysShows() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 10, restoringState = WalletRestoringState.RESYNCING),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertEquals(HomeMessageData.Resyncing(progress = 50f), result)
    }

    // NU7 characterization pins (MOB-2287): the threshold is a block COUNT sized for 75 s blocks.

    @Test
    fun `characterization pin of pre-NU7 75s - banner threshold is 3456 blocks which is 3 days at 75s`() {
        // NU7: revisit - spans activation once helper is height-aware
        assertEquals(3456L, SYNCING_BANNER_HIDE_BELOW_BLOCKS)
        assertEquals(3L * 24L * 3600L, SYNCING_BANNER_HIDE_BELOW_BLOCKS * 75L)
    }

    @Test
    fun `characterization pin of pre-NU7 75s - one block under the threshold hides and the threshold shows`() {
        // NU7: revisit - spans activation once helper is height-aware
        assertNull(
            syncingMessageFor(
                snapshot(blocksRemaining = SYNCING_BANNER_HIDE_BELOW_BLOCKS - 1),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        )
        assertEquals(
            HomeMessageData.Syncing(progress = 50f),
            syncingMessageFor(
                snapshot(blocksRemaining = SYNCING_BANNER_HIDE_BELOW_BLOCKS),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        )
    }

    // ── NU7 (MOB-2287): the threshold is three days of target block time, not a fixed block count. ──

    @Test
    fun `NU7 - 10000 blocks at 25s is under three days and hides the banner`() {
        // 10_000 * 25 s = 250_000 s, below 259_200 s. In blocks alone it would be well above 3456.
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 10_000, behindSeconds = 10_000L * 25L),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertNull(result)
    }

    @Test
    fun `NU7 - 10368 blocks at 25s is exactly three days and shows the banner`() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = 10_368, behindSeconds = 10_368L * 25L),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertEquals(HomeMessageData.Syncing(progress = 50f), result)
    }

    @Test
    fun `NU7 - threshold in seconds equals the pre-NU7 3456 blocks at 75s`() {
        assertEquals(3456L * 75L, SYNCING_BANNER_HIDE_BELOW_SECONDS)
    }

    @Test
    fun `NU7 - an unknown behind time falls back to the block count`() {
        val result =
            syncingMessageFor(
                snapshot(blocksRemaining = SYNCING_BANNER_HIDE_BELOW_BLOCKS - 1, behindSeconds = -1L),
                syncMessageShownBefore = false,
                someBalance = false,
            )
        assertNull(result)
    }
}
