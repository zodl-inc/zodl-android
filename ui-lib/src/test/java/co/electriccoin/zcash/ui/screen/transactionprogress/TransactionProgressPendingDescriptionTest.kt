package co.electriccoin.zcash.ui.screen.transactionprogress

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pending-screen subtitle routing for a resubmittable GrpcFailure (MOB-1145): a timeout shows the
 * dedicated "may still have been broadcast" copy; a non-timeout failure shows its description when
 * present; otherwise nothing failure-specific is shown and the caller uses the proposal default.
 */
class TransactionProgressPendingDescriptionTest {
    @Test
    fun timeoutReasonUsesDedicatedTimeoutCopy() {
        assertEquals(
            stringRes(R.string.send_pendingTimeoutInfo).withStyle(),
            SubmitResult
                .GrpcFailure(txIds = listOf("a"), reason = SubmitResult.GrpcFailure.Reason.TIMEOUT)
                .pendingDescription()
        )
    }

    @Test
    fun nonTimeoutWithDescriptionUsesDescription() {
        assertEquals(
            stringRes("boom").withStyle(),
            SubmitResult.GrpcFailure(txIds = listOf("a"), description = "boom").pendingDescription()
        )
    }

    @Test
    fun nonTimeoutWithoutDescriptionFallsThrough() {
        assertNull(SubmitResult.GrpcFailure(txIds = listOf("a"), description = null).pendingDescription())
    }

    @Test
    fun nonTimeoutWithBlankDescriptionFallsThrough() {
        assertNull(SubmitResult.GrpcFailure(txIds = listOf("a"), description = "   ").pendingDescription())
    }
}
