package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PercentDecimal
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.WalletSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendViewModelIsSyncingTest {
    private fun snapshot(status: Synchronizer.Status) =
        WalletSnapshot(
            status = status,
            progress = PercentDecimal(0.5f),
            synchronizerError = null,
            isSpendable = true,
            restoringState = WalletRestoringState.SYNCING,
        )

    @Test
    fun syncedIsNotSyncing() = assertFalse(snapshot(Synchronizer.Status.SYNCED).isSyncing())

    @Test
    fun missingSnapshotIsNotSyncing() = assertFalse((null as WalletSnapshot?).isSyncing())

    @Test
    fun everyOtherStatusIsSyncing() {
        Synchronizer.Status.entries
            .filter { it != Synchronizer.Status.SYNCED }
            .forEach { assertTrue(snapshot(it).isSyncing(), "expected $it to count as syncing") }
    }
}
