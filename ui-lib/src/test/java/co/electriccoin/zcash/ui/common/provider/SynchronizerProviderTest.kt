package co.electriccoin.zcash.ui.common.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SynchronizerProviderTest {
    @Test
    fun retainsPreviousValueDuringRebuild() {
        assertEquals(
            "retained",
            resolveRetained(retained = "retained", current = null, hasWallet = true)
        )
    }

    @Test
    fun replacesRetainedValue() {
        assertEquals(
            "replacement",
            resolveRetained(retained = "retained", current = "replacement", hasWallet = true)
        )
    }

    @Test
    fun clearsRetainedValueWithoutWallet() {
        assertNull(
            resolveRetained(
                retained = "retained",
                current = "current",
                hasWallet = false
            )
        )
    }
}
