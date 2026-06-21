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
    ) = WalletSnapshot(
        status = Synchronizer.Status.SYNCING,
        progress = PercentDecimal(progress),
        synchronizerError = null,
        isSpendable = false,
        restoringState = restoringState,
        blocksRemaining = blocksRemaining,
    )

    @Test
    fun blocksRemainingAboveThresholdShowsSyncBanner() {
        val result = syncingMessageFor(snapshot(blocksRemaining = 5000), syncMessageShownBefore = false, someBalance = false)
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
        val result = syncingMessageFor(snapshot(blocksRemaining = 100), syncMessageShownBefore = false, someBalance = false)
        assertNull(result)
    }

    @Test
    fun blocksRemainingZeroHidesSyncBanner() {
        val result = syncingMessageFor(snapshot(blocksRemaining = 0), syncMessageShownBefore = false, someBalance = false)
        assertNull(result)
    }

    @Test
    fun sentinelMinusOneHidesSyncBanner() {
        val result = syncingMessageFor(snapshot(blocksRemaining = -1L), syncMessageShownBefore = false, someBalance = false)
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
        // Once the banner has been shown once, subsequent low-blocks-remaining still keeps it visible.
        val result = syncingMessageFor(snapshot(blocksRemaining = 10), syncMessageShownBefore = true, someBalance = false)
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
}
