package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Synchronizer
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class SynchronizerProviderTest {
    @Test
    fun retainsSynchronizerDuringWalletRebuild() {
        val retained = mockk<Synchronizer>()

        assertSame(
            retained,
            resolveRetainedSynchronizer(retained, current = null, hasWallet = true)
        )
    }

    @Test
    fun replacesRetainedSynchronizer() {
        val retained = mockk<Synchronizer>()
        val replacement = mockk<Synchronizer>()

        assertSame(
            replacement,
            resolveRetainedSynchronizer(retained, current = replacement, hasWallet = true)
        )
    }

    @Test
    fun clearsRetainedSynchronizerWithoutWallet() {
        assertNull(
            resolveRetainedSynchronizer(
                retained = mockk(),
                current = mockk(),
                hasWallet = false
            )
        )
    }
}
