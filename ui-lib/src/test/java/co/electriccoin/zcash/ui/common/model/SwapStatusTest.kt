package co.electriccoin.zcash.ui.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the [SwapStatus.isTerminal] contract that the swap-status polling loop relies on to stop
 * (MOB-1345: FAILED/EXPIRED must end polling, not only SUCCESS/REFUNDED).
 */
class SwapStatusTest {
    @Test
    fun terminalStatusesEndPolling() {
        assertTrue(SwapStatus.SUCCESS.isTerminal)
        assertTrue(SwapStatus.REFUNDED.isTerminal)
        assertTrue(SwapStatus.FAILED.isTerminal)
        assertTrue(SwapStatus.EXPIRED.isTerminal)
    }

    @Test
    fun nonTerminalStatusesContinuePolling() {
        assertFalse(SwapStatus.INCOMPLETE_DEPOSIT.isTerminal)
        assertFalse(SwapStatus.PENDING.isTerminal)
        assertFalse(SwapStatus.PROCESSING.isTerminal)
    }

    @Test
    fun everyStatusIsClassified() {
        // Guards against a new enum entry silently defaulting to non-terminal and looping forever.
        val expectedTerminal = setOf(SwapStatus.SUCCESS, SwapStatus.REFUNDED, SwapStatus.FAILED, SwapStatus.EXPIRED)
        assertEquals(expectedTerminal, SwapStatus.entries.filter { it.isTerminal }.toSet())
    }
}
