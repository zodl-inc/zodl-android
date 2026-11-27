package co.electriccoin.zcash.ui.screen.transactionprogress

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.SubmitRejection
import co.electriccoin.zcash.ui.common.model.SubmitResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionProgressTooLargeTest {
    private val tooLarge = TransactionEncoderException.ProposalTooLargeException(RuntimeException("raw"))

    private fun failure(rejection: SubmitRejection?) =
        SubmitResult.Failure(txIds = listOf("a"), code = -25, description = "d", rejection = rejection)

    @Test
    fun serverRefusalAsTooLargeIsTooLarge() {
        assertTrue(failure(SubmitRejection.TOO_LARGE).isTooLarge())
    }

    @Test
    fun otherExplicitRejectionsAreNotTooLarge() {
        assertFalse(failure(SubmitRejection.EXPIRED).isTooLarge())
        assertFalse(failure(SubmitRejection.INVALID).isTooLarge())
    }

    @Test
    fun failureWithoutAKnownRejectionIsNotTooLarge() {
        assertFalse(failure(null).isTooLarge())
    }

    @Test
    fun sdkRefusingToCreateWithinTheBoundIsTooLarge() {
        assertTrue(SubmitResult.Error(tooLarge).isTooLarge())
        assertTrue(SubmitResult.Error(IllegalStateException(tooLarge)).isTooLarge())
    }

    @Test
    fun tooLargeReasonNestedDeeperInTheCauseChainIsTooLarge() {
        assertTrue(SubmitResult.Error(RuntimeException(IllegalArgumentException(tooLarge))).isTooLarge())
    }

    @Test
    fun otherErrorsAreNotTooLarge() {
        assertFalse(SubmitResult.Error(IllegalStateException("boom")).isTooLarge())
    }
}
