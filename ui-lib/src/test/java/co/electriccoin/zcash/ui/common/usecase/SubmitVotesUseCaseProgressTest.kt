package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubmitVotesUseCaseProgressTest {
    @Test
    fun keystoneAuthorizationClassifierWrapsGenericFailures() {
        val cause = IllegalStateException("Delegation transaction failed")

        val classified = cause.asVotingAuthorizationExceptionIfNeeded(isKeystone = true)

        assertTrue(classified is VotingAuthorizationException)
        assertSame(cause, classified.cause)
        assertEquals("Delegation transaction failed", classified.message)
    }

    @Test
    fun authorizationClassifierPreservesNonKeystoneFailures() {
        val cause = IllegalStateException("Delegation transaction failed")

        val classified = cause.asVotingAuthorizationExceptionIfNeeded(isKeystone = false)

        assertSame(cause, classified)
    }

    @Test
    fun authorizationClassifierPreservesRecoverableFailures() {
        val recoverable = VotingSubmissionRecoverableException(VotingErrors.MissingVotingServerUrl)

        val classified = recoverable.asVotingAuthorizationExceptionIfNeeded(isKeystone = true)

        assertSame(recoverable, classified)
    }

    @Test
    fun submittingProgressDoesNotAdvanceBundleBeforeWorkCompletes() {
        assertEquals(
            0f,
            calculateSubmittingBundleProgress(
                proposalIndex = 0,
                bundleIndex = 0,
                bundleCount = 2,
                totalChoices = 1,
                bundleProgress = 0.0
            )
        )

        assertEquals(
            0.5f,
            calculateSubmittingBundleProgress(
                proposalIndex = 0,
                bundleIndex = 0,
                bundleCount = 2,
                totalChoices = 1,
                bundleProgress = 1.0
            )
        )
    }

    @Test
    fun submittingProgressIsMonotonicAcrossBundleProofAndCompletion() {
        val progress =
            listOf(
                calculateSubmittingBundleProgress(
                    proposalIndex = 0,
                    bundleIndex = 0,
                    bundleCount = 2,
                    totalChoices = 1,
                    bundleProgress = 0.0
                ),
                calculateSubmittingBundleProgress(
                    proposalIndex = 0,
                    bundleIndex = 0,
                    bundleCount = 2,
                    totalChoices = 1,
                    bundleProgress = 0.35
                ),
                calculateSubmittingBundleProgress(
                    proposalIndex = 0,
                    bundleIndex = 0,
                    bundleCount = 2,
                    totalChoices = 1,
                    bundleProgress = 1.0
                ),
                calculateSubmittingBundleProgress(
                    proposalIndex = 0,
                    bundleIndex = 1,
                    bundleCount = 2,
                    totalChoices = 1,
                    bundleProgress = 0.0
                ),
                calculateSubmittingBundleProgress(
                    proposalIndex = 0,
                    bundleIndex = 1,
                    bundleCount = 2,
                    totalChoices = 1,
                    bundleProgress = 1.0
                )
            )

        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (previous, next) -> previous <= next })
    }

    @Test
    fun submittingProgressAccountsForMultipleProposalsAndBundles() {
        assertEquals(
            0.625f,
            calculateSubmittingBundleProgress(
                proposalIndex = 2,
                bundleIndex = 1,
                bundleCount = 2,
                totalChoices = 4,
                bundleProgress = 0.0
            )
        )
        assertEquals(
            0.75f,
            calculateSubmittingBundleProgress(
                proposalIndex = 2,
                bundleIndex = 1,
                bundleCount = 2,
                totalChoices = 4,
                bundleProgress = 1.0
            )
        )
    }
}
