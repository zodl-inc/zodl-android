package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafBlock
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubmitVotesUseCaseProgressTest {
    @Test
    fun vanCommitmentFallbackFindsOriginalWhenRetryHashIsNotConfirmed() =
        runTest {
            val expectedVan = ByteArray(32) { 7 }

            val resolution =
                reconcileDelegationTransactionResult(
                    result = spentNullifierResult(txHash = "rejected-retry-hash"),
                    rejectionMessage = "rejected",
                    fetchTxConfirmation = { null },
                    findVanPosition = {
                        findVanCommitmentPosition(
                            roundId = "round",
                            expectedVanCmx = expectedVan,
                            recoveryDelayMillis = 0,
                            fetchLatest = { CommitmentTreeLatest(height = 100, nextIndex = 2) },
                            fetchLeafPage = { _, _, _ ->
                                leafPage(
                                    blocks =
                                        listOf(
                                            leafBlock(
                                                height = 100,
                                                startIndex = 0,
                                                leaves = listOf(ByteArray(32) { 1 }, expectedVan)
                                            )
                                        )
                                )
                            }
                        )
                    }
                )

            assertEquals(1, (resolution as DelegationSubmissionResolution.ConfirmedVan).position)
        }

    @Test
    fun vanCommitmentLookupFollowsServerContinuationCursor() =
        runTest {
            val expectedVan = ByteArray(32) { 9 }
            val requestedRanges = mutableListOf<LongRange>()

            val position =
                findVanCommitmentPosition(
                    roundId = "round",
                    expectedVanCmx = expectedVan,
                    recoveryDelayMillis = 0,
                    fetchLatest = { CommitmentTreeLatest(height = 18, nextIndex = 4) },
                    fetchLeafPage = { roundId, fromHeight, toHeight ->
                        assertEquals("round", roundId)
                        requestedRanges += fromHeight..toHeight
                        when (fromHeight) {
                            0L ->
                                leafPage(
                                    blocks =
                                        listOf(
                                            leafBlock(
                                                height = 12,
                                                startIndex = 0,
                                                leaves = listOf(ByteArray(32) { 1 }, ByteArray(32) { 2 })
                                            )
                                        ),
                                    nextFromHeight = 15
                                )

                            15L ->
                                leafPage(
                                    blocks =
                                        listOf(
                                            leafBlock(
                                                height = 16,
                                                startIndex = 2,
                                                leaves = listOf(ByteArray(32) { 3 }, expectedVan)
                                            )
                                        )
                                )

                            else -> error("unexpected cursor $fromHeight")
                        }
                    }
                )

            assertEquals(3, position)
            assertEquals(listOf(0L..18L, 15L..18L), requestedRanges)
        }

    @Test
    fun vanCommitmentLookupRejectsUnboundedServerPagination() =
        runTest {
            var pageCalls = 0

            val failure =
                assertFailsWith<IllegalStateException> {
                    findVanCommitmentPosition(
                        roundId = "round",
                        expectedVanCmx = ByteArray(32) { 9 },
                        maxRecoveryAttempts = 1,
                        recoveryDelayMillis = 0,
                        maxPagesPerAttempt = 2,
                        fetchLatest = { CommitmentTreeLatest(height = 100, nextIndex = 0) },
                        fetchLeafPage = { _, fromHeight, _ ->
                            pageCalls += 1
                            leafPage(nextFromHeight = fromHeight + 1)
                        }
                    )
                }

            assertEquals("Commitment tree pagination exceeded 2 pages", failure.message)
            assertEquals(2, pageCalls)
        }

    @Test
    fun vanCommitmentLookupRejectsOmittedLeafPrefix() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                findVanCommitmentPosition(
                    roundId = "round",
                    expectedVanCmx = ByteArray(32) { 9 },
                    maxRecoveryAttempts = 1,
                    recoveryDelayMillis = 0,
                    fetchLatest = { CommitmentTreeLatest(height = 100, nextIndex = 2) },
                    fetchLeafPage = { _, _, _ ->
                        leafPage(
                            blocks =
                                listOf(
                                    leafBlock(
                                        height = 100,
                                        startIndex = 1,
                                        leaves = listOf(ByteArray(32) { 9 })
                                    )
                                )
                        )
                    }
                )
            }
        }

    @Test
    fun vanCommitmentLookupRetriesForDelayedIndexing() =
        runTest {
            val expectedVan = ByteArray(32) { 5 }
            var latestCalls = 0

            val position =
                findVanCommitmentPosition(
                    roundId = "round",
                    expectedVanCmx = expectedVan,
                    maxRecoveryAttempts = 2,
                    recoveryDelayMillis = 0,
                    fetchLatest = {
                        latestCalls += 1
                        if (latestCalls == 1) {
                            CommitmentTreeLatest(height = 100, nextIndex = 0)
                        } else {
                            CommitmentTreeLatest(height = 101, nextIndex = 1)
                        }
                    },
                    fetchLeafPage = { _, _, toHeight ->
                        if (toHeight == 101L) {
                            leafPage(
                                blocks = listOf(leafBlock(height = 101, startIndex = 0, leaves = listOf(expectedVan)))
                            )
                        } else {
                            leafPage()
                        }
                    }
                )

            assertEquals(0, position)
            assertEquals(2, latestCalls)
        }

    @Test
    fun absentVanCommitmentExhaustsBoundedAttemptsWithoutPosition() =
        runTest {
            var latestCalls = 0

            val position =
                findVanCommitmentPosition(
                    roundId = "round",
                    expectedVanCmx = ByteArray(32) { 5 },
                    maxRecoveryAttempts = 3,
                    recoveryDelayMillis = 0,
                    fetchLatest = {
                        latestCalls += 1
                        CommitmentTreeLatest(height = 100, nextIndex = 0)
                    },
                    fetchLeafPage = { _, _, _ -> leafPage() }
                )

            assertNull(position)
            assertEquals(3, latestCalls)
        }

    @Test
    fun duplicateVanCommitmentIsRejected() =
        runTest {
            val expectedVan = ByteArray(32) { 5 }

            assertFailsWith<IllegalArgumentException> {
                findVanCommitmentPosition(
                    roundId = "round",
                    expectedVanCmx = expectedVan,
                    recoveryDelayMillis = 0,
                    fetchLatest = { CommitmentTreeLatest(height = 100, nextIndex = 2) },
                    fetchLeafPage = { _, _, _ ->
                        leafPage(
                            blocks =
                                listOf(
                                    leafBlock(
                                        height = 100,
                                        startIndex = 0,
                                        leaves = listOf(expectedVan, expectedVan)
                                    )
                                )
                        )
                    }
                )
            }
        }

    @Test
    fun malformedVanCommitmentLeafIsRejected() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                findVanCommitmentPosition(
                    roundId = "round",
                    expectedVanCmx = ByteArray(32) { 5 },
                    recoveryDelayMillis = 0,
                    fetchLatest = { CommitmentTreeLatest(height = 100, nextIndex = 1) },
                    fetchLeafPage = { _, _, _ ->
                        leafPage(
                            blocks =
                                listOf(
                                    CommitmentTreeLeafBlock(
                                        height = 100,
                                        startIndex = 0,
                                        leavesBase64 = listOf("not base64")
                                    )
                                )
                        )
                    }
                )
            }
        }

    @Test
    fun acceptedVotingTransactionDoesNotPollForRecovery() =
        runTest {
            var fetchCount = 0

            val accepted =
                reconcileVotingTransactionResult(
                    result = TxResult(txHash = "accepted-tx", code = 0),
                    rejectionMessage = "rejected",
                    fetchTxConfirmation = {
                        fetchCount += 1
                        null
                    }
                )

            assertEquals("accepted-tx", accepted.txHash)
            assertNull(accepted.confirmation)
            assertEquals(0, fetchCount)
        }

    @Test
    fun spentNullifierRecoversAfterTransactionIndexingDelay() =
        runTest {
            var fetchCount = 0
            val confirmation = TxConfirmation(height = 12, code = 0)

            val accepted =
                reconcileVotingTransactionResult(
                    result = spentNullifierResult(),
                    rejectionMessage = "rejected",
                    recoveryDelayMillis = 0,
                    fetchTxConfirmation = {
                        fetchCount += 1
                        if (fetchCount == 3) confirmation else null
                    }
                )

            assertEquals("duplicate-tx", accepted.txHash)
            assertSame(confirmation, accepted.confirmation)
            assertEquals(3, fetchCount)
        }

    @Test
    fun spentNullifierStopsAfterBoundedRecoveryMisses() =
        runTest {
            var fetchCount = 0

            val failure =
                assertFailsWith<IllegalStateException> {
                    reconcileVotingTransactionResult(
                        result = spentNullifierResult(),
                        rejectionMessage = "rejected",
                        recoveryDelayMillis = 0,
                        fetchTxConfirmation = {
                            fetchCount += 1
                            null
                        }
                    )
                }

            assertEquals("nullifier already spent: abc123", failure.message)
            assertEquals(3, fetchCount)
        }

    @Test
    fun spentNullifierWithoutHashDoesNotPoll() =
        runTest {
            var fetchCount = 0

            assertFailsWith<IllegalStateException> {
                reconcileVotingTransactionResult(
                    result = spentNullifierResult(txHash = ""),
                    rejectionMessage = "rejected",
                    fetchTxConfirmation = {
                        fetchCount += 1
                        null
                    }
                )
            }

            assertEquals(0, fetchCount)
        }

    @Test
    fun unrelatedRejectionDoesNotPoll() =
        runTest {
            var fetchCount = 0

            val failure =
                assertFailsWith<IllegalStateException> {
                    reconcileVotingTransactionResult(
                        result = TxResult(txHash = "failed-tx", code = 1, log = "invalid proof"),
                        rejectionMessage = "rejected",
                        fetchTxConfirmation = {
                            fetchCount += 1
                            null
                        }
                    )
                }

            assertEquals("invalid proof", failure.message)
            assertEquals(0, fetchCount)
        }

    @Test
    fun rejectedConfirmationDoesNotRecoverSpentNullifier() =
        runTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    reconcileVotingTransactionResult(
                        result = spentNullifierResult(),
                        rejectionMessage = "rejected",
                        fetchTxConfirmation = {
                            TxConfirmation(height = 12, code = 2, log = "transaction failed")
                        }
                    )
                }

            assertEquals("transaction failed", failure.message)
        }

    @Test
    fun cancellationDuringSpentNullifierLookupPropagates() =
        runTest {
            val cancellation = CancellationException("cancelled")

            val failure =
                assertFailsWith<CancellationException> {
                    reconcileVotingTransactionResult(
                        result = spentNullifierResult(),
                        rejectionMessage = "rejected",
                        fetchTxConfirmation = { throw cancellation }
                    )
                }

            assertSame(cancellation, failure)
        }

    @Test
    fun acceptedVotingTransactionRequiresHash() =
        runTest {
            assertFailsWith<IllegalStateException> {
                reconcileVotingTransactionResult(
                    result = TxResult(txHash = "", code = 0),
                    rejectionMessage = "rejected",
                    fetchTxConfirmation = { null }
                )
            }
        }

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
    fun castVoteLeafPositionsParseRecoveredConfirmation() {
        val confirmation =
            TxConfirmation(
                height = 1,
                code = 0,
                events =
                    listOf(
                        TxEvent(
                            type = "cast_vote",
                            attributes =
                                listOf(
                                    TxEventAttribute(
                                        key = "leaf_index",
                                        value = "7, 12"
                                    )
                                )
                        )
                    )
            )

        assertEquals(7 to 12L, confirmation.castVoteLeafPositions())
    }

    @Test
    fun castVoteLeafPositionsReportsMissingEventAsRecoverableSdkResponse() {
        val exception =
            assertFailsWith<VotingSubmissionRecoverableException> {
                TxConfirmation(height = 1, code = 0).castVoteLeafPositions()
            }

        val failure = assertIs<VotingErrors.UnexpectedSdkResponse>(exception.failure)
        assertEquals("Missing cast_vote leaf_index", failure.userMessage)
    }

    @Test
    fun castVoteLeafPositionsReportsMalformedEventAsRecoverableSdkResponse() {
        val confirmation =
            TxConfirmation(
                height = 1,
                code = 0,
                events =
                    listOf(
                        TxEvent(
                            type = "cast_vote",
                            attributes =
                                listOf(
                                    TxEventAttribute(
                                        key = "leaf_index",
                                        value = "not-positions"
                                    )
                                )
                        )
                    )
            )

        val exception =
            assertFailsWith<VotingSubmissionRecoverableException> {
                confirmation.castVoteLeafPositions()
            }

        val failure = assertIs<VotingErrors.UnexpectedSdkResponse>(exception.failure)
        assertEquals("Malformed cast_vote leaf_index: not-positions", failure.userMessage)
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

    private fun spentNullifierResult(txHash: String = "duplicate-tx") =
        TxResult(
            txHash = txHash,
            code = 1,
            log = "nullifier already spent: abc123"
        )

    private fun leafBlock(
        height: Long,
        startIndex: Long,
        leaves: List<ByteArray>
    ) = CommitmentTreeLeafBlock(
        height = height,
        startIndex = startIndex,
        leavesBase64 = leaves.map { leaf -> Base64.getEncoder().encodeToString(leaf) }
    )

    private fun leafPage(
        blocks: List<CommitmentTreeLeafBlock> = emptyList(),
        nextFromHeight: Long = 0
    ) = CommitmentTreeLeafPage(
        blocks = blocks,
        nextFromHeight = nextFromHeight
    )
}
