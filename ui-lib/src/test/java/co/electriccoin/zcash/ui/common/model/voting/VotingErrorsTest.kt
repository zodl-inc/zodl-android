package co.electriccoin.zcash.ui.common.model.voting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VotingErrorsTest {
    @Test
    fun recoverableExceptionUsesFailureMessage() {
        val failure = VotingErrors.MissingPreparedRecovery("round-1")
        val exception = VotingSubmissionRecoverableException(failure)

        assertEquals(failure, exception.failure)
        assertEquals("Voting round round-1 has not been prepared", exception.message)
    }

    @Test
    fun walletSyncingMessageIncludesProgress() {
        val failure =
            VotingErrors.WalletSyncing(
                scannedHeight = 10,
                snapshotHeight = 20
            )

        assertEquals("Wallet sync is below the voting snapshot height (10/20)", failure.userMessage)
    }

    @Test
    fun replayRetryLaterCarriesTypedFailure() {
        val decision =
            VotingReplayDecision.RetryLater(
                VotingErrors.TxConfirmationTimedOut("tx-1")
            )

        assertIs<VotingErrors.TxConfirmationTimedOut>(decision.reason)
        assertEquals("Transaction tx-1 was not confirmed in time", decision.reason.userMessage)
    }
}
