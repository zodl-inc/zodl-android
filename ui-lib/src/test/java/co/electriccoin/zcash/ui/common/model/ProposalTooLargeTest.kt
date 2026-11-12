package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProposalTooLargeTest {
    private val tooLarge = TransactionEncoderException.ProposalTooLargeException(RuntimeException("raw"))

    @Test
    fun theSizeGateItselfIsTooLarge() {
        assertTrue(tooLarge.isProposalTooLarge())
    }

    @Test
    fun theSizeGateIsFoundAlongTheCauses() {
        assertTrue(IllegalStateException(IllegalArgumentException(tooLarge)).isProposalTooLarge())
    }

    /** The create path carries the typed reason as a property, not as the cause. */
    @Test
    fun theSizeGateIsFoundInATransactionNotCreatedRootCause() {
        val notCreated = TransactionEncoderException.TransactionNotCreatedException(tooLarge)

        assertTrue(notCreated.isProposalTooLarge())
        assertTrue(RuntimeException(notCreated).isProposalTooLarge())
    }

    @Test
    fun theSizeGateIsFoundAmongSuppressedFailures() {
        val failure = RuntimeException("outer").apply { addSuppressed(tooLarge) }

        assertTrue(failure.isProposalTooLarge())
    }

    @Test
    fun otherFailuresAreNotTooLarge() {
        val failure =
            RuntimeException(
                TransactionEncoderException.TransactionNotCreatedException(IllegalStateException("other"))
            ).apply { addSuppressed(IllegalArgumentException("suppressed")) }

        assertFalse(failure.isProposalTooLarge())
    }

    @Test
    fun aCycleOfWrappedFailuresEnds() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.addSuppressed(second)

        assertFalse(first.isProposalTooLarge())
    }
}
