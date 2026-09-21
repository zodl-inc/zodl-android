package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VotingRoundQuiescenceMapperTest {
    private fun reportWith(
        quiescence: VotingRoundQuiescence,
        failures: List<cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure> = emptyList()
    ) = VotingRoundRunReport(
        quiescence = quiescence,
        plan = null,
        completedProposals = 0,
        totalProposals = 0,
        remainingObligations = 0,
        failures = failures,
        skippedBundles = emptyList(),
        chainOutcomes = emptyList(),
        shareDeliveries = emptyList(),
        delegationsSignedCount = 0
    )

    @Test
    fun `NoWorkLeft and BackgroundShareWorkOnly are not errors`() {
        assertNull(reportWith(VotingRoundQuiescence.NoWorkLeft).toVotingErrorOrNull("round-1"))
        assertNull(
            reportWith(VotingRoundQuiescence.BackgroundShareWorkOnly(shares = emptyList()))
                .toVotingErrorOrNull("round-1")
        )
    }

    @Test
    fun `NeedsBundleSetup maps to MissingBundleCount`() {
        val error = reportWith(VotingRoundQuiescence.NeedsBundleSetup).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.MissingBundleCount>(error)
        assertEquals("round-1", error.roundId)
    }

    @Test
    fun `NeedsBallot maps to OmittedCommittedProposal using the first open proposal`() {
        val error =
            reportWith(
                VotingRoundQuiescence.NeedsBallot(openProposals = listOf(7, 8), unrosteredIntents = emptyList())
            ).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.OmittedCommittedProposal>(error)
        assertEquals(7, error.proposalId)
    }

    @Test
    fun `NeedsBallot with both lists empty maps to UnexpectedSdkResponse instead of throwing`() {
        val error =
            reportWith(
                VotingRoundQuiescence.NeedsBallot(openProposals = emptyList(), unrosteredIntents = emptyList())
            ).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(error)
    }

    @Test
    fun `ChainRecoveryStalled maps to TxConfirmationTimedOut`() {
        val error =
            reportWith(VotingRoundQuiescence.ChainRecoveryStalled(step = null, outcome = "Tracking { .. }"))
                .toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.TxConfirmationTimedOut>(error)
    }

    @Test
    fun `PersistedChainTerminal and ChainTerminal map to UnexpectedSdkResponse carrying the outcome text`() {
        val terminal =
            reportWith(VotingRoundQuiescence.PersistedChainTerminal).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(terminal)

        val chainTerminal =
            reportWith(VotingRoundQuiescence.ChainTerminal(step = null, outcome = "Rejected"))
                .toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(chainTerminal)
    }

    @Test
    fun `NeedsDelegationSignatures maps to UnexpectedSdkResponse for the software-signed path`() {
        val error =
            reportWith(VotingRoundQuiescence.NeedsDelegationSignatures(bundles = listOf(0)))
                .toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(error)
    }

    @Test
    fun `a spent-nullifier-shaped failure maps to TxConfirmationTimedOut`() {
        val failure =
            cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure(
                step = null,
                bundleIndex = 0,
                kind = "ChainSubmission",
                message = "nullifier already spent"
            )
        val error = reportWith(VotingRoundQuiescence.Failures, failures = listOf(failure)).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.TxConfirmationTimedOut>(error)
    }

    @Test
    fun `a tree-sync-shaped failure maps to VoteTreeSyncFailed`() {
        val failure =
            cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure(
                step = null,
                bundleIndex = null,
                kind = "TreeSync",
                message = "failed to sync vote tree"
            )
        val error = reportWith(VotingRoundQuiescence.Failures, failures = listOf(failure)).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.VoteTreeSyncFailed>(error)
    }

    @Test
    fun `an unrecognized failure falls back to UnexpectedSdkResponse`() {
        val failure =
            cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure(
                step = null,
                bundleIndex = null,
                kind = "SomeFutureKind",
                message = "unrecognized"
            )
        val error = reportWith(VotingRoundQuiescence.Failures, failures = listOf(failure)).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(error)
    }

    @Test
    fun `Unknown quiescence is never treated as success`() {
        val error =
            reportWith(VotingRoundQuiescence.Unknown(kind = "FutureVariant", detailJson = null))
                .toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(error)
    }

    @Test
    fun `Cancelled maps to UnexpectedSdkResponse (no dedicated case exists yet)`() {
        val error = reportWith(VotingRoundQuiescence.Cancelled).toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(error)
    }

    @Test
    fun `PassBudgetExhausted maps to UnexpectedSdkResponse`() {
        val error =
            reportWith(VotingRoundQuiescence.PassBudgetExhausted(remaining = emptyList()))
                .toVotingErrorOrNull("round-1")
        assertIs<VotingErrors.UnexpectedSdkResponse>(error)
    }
}
