package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
    fun delegateVoteVanPositionReportsMissingLeafIndexAsRecoverableSdkResponse() {
        val confirmation = TxConfirmation(height = 1, code = 0)

        val exception =
            assertFailsWith<VotingSubmissionRecoverableException> {
                confirmation.delegateVoteVanPosition(bundleIndex = 3)
            }

        val failure = assertIs<VotingErrors.UnexpectedSdkResponse>(exception.failure)
        assertEquals("Missing delegate_vote leaf_index for bundle 3", failure.userMessage)
    }

    @Test
    fun delegateVoteVanPositionReportsMalformedLeafIndexAsRecoverableSdkResponse() {
        val confirmation =
            TxConfirmation(
                height = 1,
                code = 0,
                events =
                    listOf(
                        TxEvent(
                            type = "delegate_vote",
                            attributes =
                                listOf(
                                    TxEventAttribute(
                                        key = "leaf_index",
                                        value = "not-a-position"
                                    )
                                )
                        )
                    )
            )

        val exception =
            assertFailsWith<VotingSubmissionRecoverableException> {
                confirmation.delegateVoteVanPosition(bundleIndex = 4)
            }

        val failure = assertIs<VotingErrors.UnexpectedSdkResponse>(exception.failure)
        assertEquals("Malformed delegate_vote leaf_index for bundle 4: not-a-position", failure.userMessage)
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
