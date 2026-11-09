@file:Suppress("MaxLineLength")

package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What counts as a failed send for the private-connection outage detection. A relay that is down surfaces as a
 * gRPC failure (a timeout among them) or as a partial result, not as a rejection, so only a full success is not a
 * failure; the policy then needs the deciding call's own evidence to be a transport failure.
 */
class SubmitResultOperationFailureTest {
    @Test
    fun `only a full success is not a failed operation`() {
        assertEquals(false, SubmitResult.Success(listOf("tx")).isOperationFailure())
        assertEquals(true, SubmitResult.GrpcFailure(listOf("tx")).isOperationFailure())
        assertEquals(
            true,
            SubmitResult.GrpcFailure(listOf("tx"), "timeout", SubmitResult.GrpcFailure.Reason.TIMEOUT).isOperationFailure(),
        )
        assertEquals(true, SubmitResult.Partial(listOf("a", "b"), listOf("ok", "failed")).isOperationFailure())
        assertEquals(true, SubmitResult.Failure(listOf("tx"), code = 1, description = "rejected").isOperationFailure())
        assertEquals(true, SubmitResult.Error(IllegalStateException("local")).isOperationFailure())
    }
}
