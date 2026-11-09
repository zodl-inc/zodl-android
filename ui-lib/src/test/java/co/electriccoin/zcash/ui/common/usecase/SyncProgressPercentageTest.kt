package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PercentDecimal
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SyncProgressPercentageTest {
    private val fractions = listOf(0f to 0f, 0.001f to 0.1f, 0.12345f to 12.345f, 0.9999f to 99.99f, 1f to 100f)

    private fun snapshot(fraction: Float, restoring: WalletRestoringState) =
        WalletSnapshot(
            status = Synchronizer.Status.SYNCING,
            progress = PercentDecimal(fraction),
            synchronizerError = null,
            isSpendable = true,
            restoringState = restoring,
            blocksRemaining = 0,
            behindSeconds = 0,
        )

    @Test
    fun syncingConvertsFractionsToPercentWithoutPrematureRounding() {
        for ((fraction, percent) in fractions) {
            val result = syncingMessageFor(snapshot(fraction, WalletRestoringState.SYNCING), true, true)
            assertEquals(percent, assertIs<HomeMessageData.Syncing>(result).progress, absoluteTolerance = 0.0001f)
        }
    }

    @Test
    fun restoringAndResyncingUseTheSamePercentageScale() {
        for ((fraction, percent) in fractions) {
            val restore = syncingMessageFor(snapshot(fraction, WalletRestoringState.RESTORING), false, true)
            val resync = syncingMessageFor(snapshot(fraction, WalletRestoringState.RESYNCING), false, true)
            assertEquals(percent, assertIs<HomeMessageData.Restoring>(restore).progress, absoluteTolerance = 0.0001f)
            assertEquals(percent, assertIs<HomeMessageData.Resyncing>(resync).progress, absoluteTolerance = 0.0001f)
        }
    }

    @Test
    fun fundsAreSpendableDuringRestoreOnlyWithBothSdkReadinessAndBalance() {
        for (ready in listOf(false, true)) {
            for (balance in listOf(false, true)) {
                val snap = snapshot(0.5f, WalletRestoringState.RESTORING).copy(isSpendable = ready)
                val result = assertIs<HomeMessageData.Restoring>(syncingMessageFor(snap, false, balance))
                assertEquals(ready && balance, result.isSpendable)
            }
        }
    }

    @Test
    fun completedAndStoppedSyncsDoNotShowAnyProgressBanner() {
        for (status in Synchronizer.Status.entries.filter { it != Synchronizer.Status.SYNCING }) {
            for (restoring in listOf(WalletRestoringState.SYNCING, WalletRestoringState.RESTORING, WalletRestoringState.RESYNCING)) {
                val snap = snapshot(1f, restoring).copy(status = status)
                assertNull(syncingMessageFor(snap, true, true), "$status / $restoring")
            }
        }
    }
}
