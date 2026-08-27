package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.TransparentInfo
import co.electriccoin.zcash.ui.common.model.UnifiedInfo
import co.electriccoin.zcash.ui.common.model.voting.BundleDelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafBlock
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingCommittedVoteRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationSubmission
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteCommitment
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteRecord
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneBundleSignature
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.EOFException
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubmitVotesUseCaseRecoveryTest {
    @Test
    fun duplicateDelegationPersistsVanPositionAndRestartSkipsRecoveredBundle() =
        runTest {
            val fixture = RecoveryFixture(keystoneAccount())

            assertFailsWith<FirstRunInterrupted> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(DelegationPhase.CONFIRMED, fixture.delegationPhases[0])
            assertEquals(DelegationPhase.PROVED, fixture.delegationPhases[1])
            assertEquals(listOf(StoredVanPosition(bundleIndex = 0, position = 1)), fixture.storedVanPositions)
            assertEquals(emptyList(), fixture.storedDelegationHashes)
            assertEquals(listOf(1L..20L, 11L..20L), fixture.requestedLeafRanges)

            assertFailsWith<ContinuationReached> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(1, fixture.submissionCounts.getValue(0))
            assertEquals(2, fixture.submissionCounts.getValue(1))
            assertEquals(DelegationPhase.CONFIRMED, fixture.delegationPhases[0])
            assertEquals(DelegationPhase.CONFIRMED, fixture.delegationPhases[1])
            assertEquals(VotingRecoveryPhase.DELEGATION_SUBMITTED, fixture.recovery.phase)
            assertEquals(2, fixture.closeDbCalls)
            coVerify(exactly = 0) {
                fixture.crypto.buildAndProveDelegation(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun unclassifiedDelegationTransportFailureFallsBackToVanLookup() =
        runTest {
            val fixture =
                RecoveryFixture(
                    selectedAccount = keystoneAccount(),
                    bundle0Failure = EOFException("unexpected end of stream")
                )

            assertFailsWith<FirstRunInterrupted> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(DelegationPhase.CONFIRMED, fixture.delegationPhases[0])
            assertEquals(listOf(StoredVanPosition(bundleIndex = 0, position = 1)), fixture.storedVanPositions)
            assertEquals(listOf(1L..20L, 11L..20L), fixture.requestedLeafRanges)
        }

    @Test
    fun castVoteAcceptedResponseLossRestartsAndRecoversOriginalHash() =
        runTest {
            val fixture = CastVoteRecoveryFixture(keystoneAccount())

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(1, fixture.submittedBundles.size)
            assertEquals(emptyList(), fixture.storedVoteHashes)
            assertEquals(emptyList(), fixture.recordedVcPositions)
            assertEquals(emptyList(), fixture.recordedShares)
            assertEquals(1, fixture.closeDbCalls)

            val result = fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))

            assertEquals(1, result.submittedProposalCount)
            assertEquals(2, fixture.submittedBundles.size)
            assertEquals(fixture.submittedBundles[0], fixture.submittedBundles[1])
            assertEquals(fixture.submittedSignatures[0], fixture.submittedSignatures[1])
            assertEquals(listOf(ORIGINAL_CAST_TX_HASH), fixture.confirmationLookups)
            assertEquals(listOf(ORIGINAL_CAST_TX_HASH), fixture.storedVoteHashes)
            assertTrue(fixture.storedVanPositions.contains(StoredVanPosition(bundleIndex = 0, position = 7)))
            assertEquals(listOf(12L), fixture.recordedVcPositions)
            assertEquals(listOf(0), fixture.recordedShares)
            assertEquals(VotingRecoveryPhase.SHARES_SUBMITTED, fixture.recovery.phase)
            assertEquals(2, fixture.closeDbCalls)
        }

    @Test
    fun changedCastVoteDoesNotRecoverOriginalVoteHash() =
        runTest {
            val fixture = CastVoteRecoveryFixture(keystoneAccount())

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            val retry =
                runCatching {
                    fixture.newUseCase()(ROUND_ID, mapOf(1 to 1))
                }

            assertEquals(1, fixture.submittedBundles.size)
            val failure =
                assertIs<VotingSubmissionRecoverableException>(
                    retry.exceptionOrNull()
                )
            assertIs<VotingErrors.ConflictingProposalSelection>(failure.failure)
            assertEquals(
                VotingProposalSelection(choiceId = 0, numOptions = 2),
                fixture.recovery.proposalSelections[1]
            )
            assertEquals(emptyList(), fixture.storedVoteHashes)
            assertEquals(emptyList(), fixture.storedVanPositions)
            assertEquals(emptyList(), fixture.recordedVcPositions)
            assertEquals(emptyList(), fixture.recordedShares)
            assertEquals(VotingRecoveryPhase.DELEGATION_SUBMITTED, fixture.recovery.phase)
        }

    @Test
    fun retryOmittingCommitmentBackedProposalRequiresOriginalSelection() =
        runTest {
            val fixture = CastVoteRecoveryFixture(keystoneAccount(), proposalCount = 2)

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0, 2 to 0))
            }

            val failure =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    fixture.newUseCase()(ROUND_ID, mapOf(2 to 0))
                }

            assertIs<VotingErrors.OmittedCommittedProposal>(failure.failure)
            assertEquals(1, fixture.submittedBundles.size)
            assertEquals(emptyList(), fixture.storedVoteHashes)
            assertEquals(
                VotingProposalSelection(choiceId = 0, numOptions = 2),
                fixture.recovery.proposalSelections[1]
            )
        }

    @Test
    fun retryOmittingSelectionWithoutCommitmentStillProcessesRequestedProposal() =
        runTest {
            val fixture =
                CastVoteRecoveryFixture(
                    selectedAccount = keystoneAccount(),
                    proposalCount = 2,
                    initialSelections = mapOf(1 to VotingProposalSelection(choiceId = 0, numOptions = 2))
                )

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(2 to 0))
            }

            assertEquals(1, fixture.submittedBundles.size)
            assertEquals(2, fixture.submittedBundles.single().proposalId)
        }

    @Test
    fun recoveredCastVoteVerificationScansTreeOnce() =
        runTest {
            val fixture = CastVoteRecoveryFixture(keystoneAccount())

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            val result = fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))

            assertEquals(1, result.submittedProposalCount)
            assertEquals(1, fixture.latestFetches)
            assertEquals(1, fixture.leafPageFetches)
        }

    @Test
    fun unverifiableRecoveredCastVoteSurfacesTypedRetryableError() =
        runTest {
            val fixture =
                CastVoteRecoveryFixture(
                    selectedAccount = keystoneAccount(),
                    latestNextIndexOverride = 14
                )

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            val failure =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
                }

            assertIs<VotingErrors.RecoveredVoteVerificationUnavailable>(failure.failure)
            assertEquals(emptyList(), fixture.storedVoteHashes)
            assertEquals(emptyList(), fixture.storedVanPositions)
        }

    @Test
    fun changedChoiceCannotReuseCachedConfirmedVote() =
        runTest {
            val fixture =
                CastVoteRecoveryFixture(
                    selectedAccount = keystoneAccount(),
                    cachedVoteTxHash = ORIGINAL_CAST_TX_HASH,
                    initialSelections = mapOf(1 to VotingProposalSelection(choiceId = 0, numOptions = 2))
                )

            val failure =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    fixture.newUseCase()(ROUND_ID, mapOf(1 to 1))
                }

            assertIs<VotingErrors.ConflictingProposalSelection>(failure.failure)
            assertEquals(emptyList(), fixture.storedVoteHashes)
            assertEquals(emptyList(), fixture.storedVanPositions)
            assertEquals(emptyList(), fixture.recordedVcPositions)
            assertEquals(
                VotingProposalSelection(choiceId = 0, numOptions = 2),
                fixture.recovery.proposalSelections[1]
            )
        }

    @Test
    fun preBroadcastFailureLeavesSelectionUnlockedForRetry() =
        runTest {
            val fixture = CastVoteRecoveryFixture(keystoneAccount(), failFirstTreeSync = true)

            val failure =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
                }

            assertIs<VotingErrors.VoteTreeSyncFailed>(failure.failure)
            assertEquals(emptyMap<Int, VotingProposalSelection>(), fixture.recovery.proposalSelections)

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 1))
            }

            assertEquals(
                VotingProposalSelection(choiceId = 1, numOptions = 2),
                fixture.recovery.proposalSelections[1]
            )
        }

    @Test
    fun spentNullifierHashMustMatchCurrentVoteCommitmentLeaves() =
        runTest {
            val fixture =
                CastVoteRecoveryFixture(
                    selectedAccount = keystoneAccount(),
                    confirmedChoice = 1
                )

            assertFailsWith<CastVoteResponseLost> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            val failure =
                assertFailsWith<VotingSubmissionRecoverableException> {
                    fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
                }

            assertIs<VotingErrors.RecoveredVoteCommitmentMismatch>(failure.failure)
            assertEquals(2, fixture.submittedBundles.size)
            assertEquals(emptyList(), fixture.storedVoteHashes)
            assertEquals(emptyList(), fixture.storedVanPositions)
            assertEquals(emptyList(), fixture.recordedVcPositions)
            assertEquals(emptyList(), fixture.recordedShares)
            assertEquals(VotingRecoveryPhase.DELEGATION_SUBMITTED, fixture.recovery.phase)
        }

    private class CastVoteRecoveryFixture(
        private val selectedAccount: KeystoneAccount,
        private val confirmedChoice: Int = 0,
        private val proposalCount: Int = 1,
        private val failFirstTreeSync: Boolean = false,
        private val cachedVoteTxHash: String? = null,
        private val initialSelections: Map<Int, VotingProposalSelection> = emptyMap(),
        private val latestNextIndexOverride: Long? = null
    ) {
        val crypto = mockk<VotingCryptoClient>(relaxed = true)
        val api = mockk<VotingApiProvider>(relaxed = true)
        val submittedBundles = mutableListOf<VoteCommitmentBundle>()
        val submittedSignatures = mutableListOf<CastVoteSignature>()
        val storedVoteHashes = mutableListOf<String>()
        val storedVanPositions = mutableListOf<StoredVanPosition>()
        val recordedVcPositions = mutableListOf<Long>()
        val recordedShares = mutableListOf<Int>()
        val confirmationLookups = mutableListOf<String>()
        val persistedVotes = mutableListOf<VotingVoteRecord>()
        var closeDbCalls = 0
        var latestFetches = 0
        var leafPageFetches = 0

        private val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
        var recovery =
            VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = ROUND_ID,
                phase = VotingRecoveryPhase.DELEGATION_SUBMITTED,
                bundleCount = 1,
                eligibleWeight = 1,
                bundleWeights = listOf(1),
                hotkeyAddress = "hotkey",
                proposalSelections = initialSelections
            )

        private val recoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        private val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)
        private val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
        private val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        private val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        private val pirSnapshotResolver = mockk<PirSnapshotResolver>()
        private val hotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        private val session = votingSession(proposalCount)
        private var submissionAttempt = 0

        init {
            val synchronizer = mockk<Synchronizer>()
            every { synchronizer.network } returns ZcashNetwork.Mainnet
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/wallet/data.sqlite3"
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { prepareVotingRound(ROUND_ID) } returns
                VotingRoundPreparationResult.Ready(ROUND_ID, 1, 1, "hotkey")
            coEvery { resolveVotingRoundSession(ROUND_ID) } returns
                VotingRoundSessionContext(
                    session = session,
                    serviceConfig =
                        VotingServiceConfig(
                            voteServers = listOf(VotingServiceConfig.ServiceEndpoint("https://vote", "vote")),
                            pirEndpoints = listOf(VotingServiceConfig.ServiceEndpoint("https://pir", "pir")),
                            pirLayout = VotingPirLayout(pirDepth = 1, tier0Layers = 1, tier1Layers = 1, polyLen = 4096)
                        )
                )
            coEvery { pirSnapshotResolver.resolve(any(), any()) } returns "https://pir"
            coEvery { recoveryRepository.get(accountUuid, ROUND_ID) } answers { recovery }
            coEvery { recoveryRepository.setPhase(accountUuid, ROUND_ID, any()) } answers {
                recovery = recovery.copy(phase = thirdArg())
            }
            coEvery { recoveryRepository.storeProposalSelections(accountUuid, ROUND_ID, any()) } answers {
                // Mirrors VotingRecoveryRepositoryImpl's selection-lock contract.
                val supplied = thirdArg<Map<Int, VotingProposalSelection>>()
                val conflictingProposalId =
                    supplied.entries
                        .firstOrNull { (proposalId, selection) ->
                            recovery.proposalSelections[proposalId]?.let { it != selection } == true
                        }?.key
                if (conflictingProposalId != null) {
                    throw VotingSubmissionRecoverableException(
                        VotingErrors.ConflictingProposalSelection(
                            roundId = ROUND_ID,
                            proposalId = conflictingProposalId
                        )
                    )
                }
                recovery = recovery.copy(proposalSelections = recovery.proposalSelections + supplied)
            }
            coEvery { recoveryRepository.storeSingleShareMode(accountUuid, ROUND_ID, any()) } answers {
                recovery = recovery.copy(singleShareMode = thirdArg())
            }
            coEvery { recoveryRepository.markProposalSubmitted(accountUuid, ROUND_ID, any()) } answers {
                recovery = recovery.copy(submittedProposalIds = recovery.submittedProposalIds + thirdArg<Int>())
            }
            coEvery { hotkeySeedProvider.get(accountUuid) } returns ByteArray(32) { 9 }

            coEvery { crypto.getWalletNotesJson(any(), any(), any(), any()) } returns "[]"
            coEvery { crypto.openVotingDb(any()) } returns 1
            coEvery { crypto.closeVotingDb(any()) } answers { closeDbCalls += 1 }
            coEvery { crypto.getVotes(any(), any()) } answers { persistedVotes.toList() }
            coEvery { crypto.getShareDelegations(any(), any()) } returns emptyList()
            coEvery { crypto.getVoteTxHash(any(), any(), any(), any()) } returns
                (cachedVoteTxHash?.let(VotingTxHashLookup::Present) ?: VotingTxHashLookup.NotFound)
            var treeSyncCalls = 0
            coEvery { crypto.syncVoteTree(any(), any(), any()) } answers {
                treeSyncCalls += 1
                if (failFirstTreeSync && treeSyncCalls == 1) -1L else 10L
            }
            coEvery { crypto.generateVanWitnessJson(any(), any(), any(), any()) } returns
                """{"position":7,"anchor_height":10}"""
            coEvery {
                crypto.buildVoteCommitment(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } answers {
                val bundleIndex = arg<Int>(2)
                val proposalId = arg<Int>(4)
                val choice = arg<Int>(5)
                persistedVotes.removeAll { vote ->
                    vote.bundleIndex == bundleIndex && vote.proposalId == proposalId
                }
                persistedVotes +=
                    VotingVoteRecord(
                        proposalId = proposalId,
                        bundleIndex = bundleIndex,
                        choice = choice,
                        submitted = false
                    )
                castVoteCommitment(proposalId = proposalId, choice = choice)
            }
            coEvery { crypto.storeVoteTxHash(any(), any(), any(), any(), any()) } answers {
                val bundleIndex = thirdArg<Int>()
                val proposalId = arg<Int>(3)
                persistedVotes.replaceAll { vote ->
                    if (vote.bundleIndex == bundleIndex && vote.proposalId == proposalId) {
                        vote.copy(submitted = true)
                    } else {
                        vote
                    }
                }
                storedVoteHashes += arg<String>(4)
            }
            coEvery { crypto.storeVanPosition(any(), any(), any(), any()) } answers {
                storedVanPositions += StoredVanPosition(thirdArg(), arg(3))
            }
            coEvery { crypto.recordVcPosition(any(), any(), any(), any(), any()) } answers {
                recordedVcPositions += arg<Long>(4)
            }
            coEvery { crypto.recoverCommittedVote(any(), any(), any(), any()) } returns
                VotingCommittedVoteRecord(
                    bundleIndex = 0,
                    proposalId = 1,
                    vcTreePosition = 12,
                    sharePayloadsJson = sharePayloadsJson()
                )
            coEvery { crypto.scheduledShareSubmitAt(any(), any(), any(), any()) } returns 0
            coEvery { crypto.recordShareDelegation(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
                recordedShares += arg<Int>(4)
            }

            coEvery { api.submitVoteCommitment(any(), any()) } answers {
                submittedBundles += firstArg<VoteCommitmentBundle>()
                submittedSignatures += secondArg<CastVoteSignature>()
                submissionAttempt += 1
                if (submissionAttempt == 1) {
                    throw CastVoteResponseLost()
                }
                TxResult(
                    txHash = ORIGINAL_CAST_TX_HASH,
                    code = 2,
                    log = "nullifier already spent"
                )
            }
            coEvery { api.fetchTxConfirmation(ORIGINAL_CAST_TX_HASH) } answers {
                confirmationLookups += ORIGINAL_CAST_TX_HASH
                TxConfirmation(
                    height = 20,
                    code = 0,
                    events =
                        listOf(
                            TxEvent(
                                type = "cast_vote",
                                attributes = listOf(TxEventAttribute("leaf_index", "7,12"))
                            )
                        )
                )
            }
            val confirmedCommitment = castVoteCommitment(proposalId = 1, choice = confirmedChoice)
            val confirmedLeaves =
                MutableList(13) { ByteArray(32) }.also { leaves ->
                    leaves[7] = confirmedCommitment.voteAuthorityNoteNew
                    leaves[12] = confirmedCommitment.voteCommitment
                }
            coEvery { api.fetchCommitmentTreeLatest(ROUND_ID) } answers {
                latestFetches += 1
                CommitmentTreeLatest(20, latestNextIndexOverride ?: 13)
            }
            coEvery { api.fetchCommitmentTreeLeafPage(ROUND_ID, 1, 20) } answers {
                leafPageFetches += 1
                CommitmentTreeLeafPage(
                    blocks =
                        listOf(
                            leafBlock(
                                height = 20,
                                startIndex = 0,
                                leaves = confirmedLeaves
                            )
                        ),
                    nextFromHeight = 0
                )
            }
            coEvery { api.delegateShares(any()) } returns
                listOf(DelegatedShareInfo(shareIndex = 0, proposalId = 1, acceptedByServers = listOf("https://helper")))
        }

        fun newUseCase() =
            SubmitVotesUseCase(
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingRecoveryRepository = recoveryRepository,
                votingSessionStore = mockk<VotingSessionStore>(relaxed = true),
                votingCryptoClient = crypto,
                votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true),
                votingApiProvider = api,
                pirSnapshotResolver = pirSnapshotResolver,
                votingHotkeySeedProvider = hotkeySeedProvider,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                getWalletSeedBytes = mockk(relaxed = true),
                prepareVotingRound = prepareVotingRound,
                votingShareTrackingScheduler = mockk<VotingShareTrackingScheduler>(relaxed = true)
            )
    }

    private class RecoveryFixture(
        private val selectedAccount: KeystoneAccount,
        private val bundle0Failure: Exception? = null
    ) {
        val crypto = mockk<VotingCryptoClient>(relaxed = true)
        val api = mockk<VotingApiProvider>(relaxed = true)
        val delegationPhases = mutableListOf(DelegationPhase.PROVED, DelegationPhase.PROVED)
        val submissionCounts = mutableMapOf(0 to 0, 1 to 0)
        val storedVanPositions = mutableListOf<StoredVanPosition>()
        val storedDelegationHashes = mutableListOf<String>()
        val requestedLeafRanges = mutableListOf<LongRange>()
        var closeDbCalls = 0

        private val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
        private val signatures =
            (0..1).associateWith {
                VotingKeystoneBundleSignature(
                    spendAuthSigBase64 = encode(SPEND_AUTH_SIG),
                    sighashBase64 = encode(SIGHASH),
                    rkBase64 = encode(RK)
                )
            }
        var recovery =
            VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = ROUND_ID,
                phase = VotingRecoveryPhase.DELEGATION_PROVED,
                bundleCount = 2,
                eligibleWeight = 2,
                bundleWeights = listOf(1, 1),
                hotkeyAddress = "hotkey",
                keystoneBundleSignatures = signatures
            )

        private val recoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        private val proofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        private val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)
        private val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
        private val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        private val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        private val pirSnapshotResolver = mockk<PirSnapshotResolver>()
        private val hotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        private val session = votingSession()

        init {
            val synchronizer = mockk<Synchronizer>()
            every { synchronizer.network } returns ZcashNetwork.Mainnet
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/wallet/data.sqlite3"
            coEvery { getSelectedWalletAccount() } returns selectedAccount
            coEvery { prepareVotingRound(ROUND_ID) } returns
                VotingRoundPreparationResult.Ready(ROUND_ID, 2, 2, "hotkey")
            coEvery { resolveVotingRoundSession(ROUND_ID) } returns
                VotingRoundSessionContext(
                    session = session,
                    serviceConfig =
                        VotingServiceConfig(
                            voteServers = listOf(VotingServiceConfig.ServiceEndpoint("https://vote", "vote")),
                            pirEndpoints = listOf(VotingServiceConfig.ServiceEndpoint("https://pir", "pir")),
                            pirLayout = VotingPirLayout(pirDepth = 1, tier0Layers = 1, tier1Layers = 1, polyLen = 4096)
                        )
                )
            coEvery { pirSnapshotResolver.resolve(any(), any()) } returns "https://pir"
            coEvery { recoveryRepository.get(accountUuid, ROUND_ID) } answers { recovery }
            coEvery { recoveryRepository.setPhase(accountUuid, ROUND_ID, any()) } answers {
                recovery = recovery.copy(phase = thirdArg())
            }
            coEvery { hotkeySeedProvider.get(accountUuid) } returns ByteArray(32) { 9 }
            coEvery { proofPrecomputeRepository.awaitDelegationPirPrecompute(any()) } returns null

            coEvery { crypto.getWalletNotesJson(any(), any(), any(), any()) } returns "[]"
            coEvery { crypto.openVotingDb(any()) } returns 1
            coEvery { crypto.closeVotingDb(any()) } answers { closeDbCalls += 1 }
            coEvery { crypto.getVotes(any(), any()) } returns emptyList()
            coEvery { crypto.getShareDelegations(any(), any()) } returns emptyList()
            coEvery { crypto.getDelegationTxHash(any(), any(), any()) } returns VotingTxHashLookup.NotFound
            coEvery { crypto.delegationPhases(any(), any()) } answers {
                delegationPhases.mapIndexed { index, phase -> BundleDelegationPhase(index, phase) }
            }
            coEvery { crypto.generateNoteWitnessesJson(any(), any(), any(), any(), any(), any()) } returns "{}"
            coEvery {
                crypto.getDelegationSubmissionWithKeystoneSignature(any(), any(), any(), any(), any())
            } answers {
                delegationSubmission(thirdArg())
            }
            coEvery { crypto.storeDelegationTxHash(any(), any(), any(), any()) } answers {
                storedDelegationHashes += arg<String>(3)
                delegationPhases[thirdArg()] = DelegationPhase.SUBMITTED
            }
            coEvery { crypto.storeVanPosition(any(), any(), any(), any()) } answers {
                val bundleIndex = thirdArg<Int>()
                val position = arg<Int>(3)
                storedVanPositions += StoredVanPosition(bundleIndex, position)
                delegationPhases[bundleIndex] = DelegationPhase.CONFIRMED
            }
            coEvery { crypto.syncVoteTree(any(), any(), any()) } throws ContinuationReached()

            coEvery { api.submitDelegation(any()) } answers {
                val bundleIndex =
                    if (firstArg<DelegationRegistration>().vanCmx[0] == 1.toByte()) {
                        0
                    } else {
                        1
                    }
                submissionCounts[bundleIndex] = submissionCounts.getValue(bundleIndex) + 1
                when {
                    bundleIndex == 0 && bundle0Failure != null -> {
                        throw bundle0Failure
                    }

                    bundleIndex == 0 -> {
                        TxResult(txHash = "", code = 2, log = "nullifier already spent")
                    }

                    submissionCounts.getValue(1) == 1 -> {
                        throw FirstRunInterrupted()
                    }

                    else -> {
                        TxResult(txHash = "bundle-1-tx", code = 0)
                    }
                }
            }
            coEvery { api.fetchTxConfirmation("bundle-1-tx") } returns
                TxConfirmation(
                    height = 20,
                    code = 0,
                    events =
                        listOf(
                            TxEvent(
                                type = "delegate_vote",
                                attributes = listOf(TxEventAttribute("leaf_index", "3"))
                            )
                        )
                )
            coEvery { api.fetchCommitmentTreeLatest(ROUND_ID) } returns CommitmentTreeLatest(20, 2)
            coEvery { api.fetchCommitmentTreeLeafPage(ROUND_ID, any(), 20) } answers {
                val fromHeight = secondArg<Long>()
                requestedLeafRanges += fromHeight..20L
                when (fromHeight) {
                    1L -> {
                        CommitmentTreeLeafPage(
                            blocks =
                                listOf(
                                    leafBlock(
                                        height = 10,
                                        startIndex = 0,
                                        leaves = listOf(ByteArray(32) { 8 })
                                    )
                                ),
                            nextFromHeight = 11
                        )
                    }

                    11L -> {
                        CommitmentTreeLeafPage(
                            blocks =
                                listOf(
                                    leafBlock(
                                        height = 12,
                                        startIndex = 1,
                                        leaves = listOf(ByteArray(32).also { it[0] = 1 })
                                    )
                                ),
                            nextFromHeight = 0
                        )
                    }

                    else -> {
                        error("Unexpected cursor $fromHeight")
                    }
                }
            }
        }

        fun newUseCase() =
            SubmitVotesUseCase(
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingRecoveryRepository = recoveryRepository,
                votingSessionStore = mockk<VotingSessionStore>(relaxed = true),
                votingCryptoClient = crypto,
                votingProofPrecomputeRepository = proofPrecomputeRepository,
                votingApiProvider = api,
                pirSnapshotResolver = pirSnapshotResolver,
                votingHotkeySeedProvider = hotkeySeedProvider,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                getWalletSeedBytes = mockk(relaxed = true),
                prepareVotingRound = prepareVotingRound,
                votingShareTrackingScheduler = mockk<VotingShareTrackingScheduler>(relaxed = true)
            )
    }

    private data class StoredVanPosition(
        val bundleIndex: Int,
        val position: Int
    )

    private class FirstRunInterrupted : CancellationException()

    private class ContinuationReached : CancellationException()

    private class CastVoteResponseLost : CancellationException()

    private companion object {
        const val ROUND_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        const val ORIGINAL_CAST_TX_HASH = "original-cast-tx"
        val SPEND_AUTH_SIG = byteArrayOf(2)
        val SIGHASH = byteArrayOf(3)
        val RK = byteArrayOf(4)

        fun encode(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

        fun delegationSubmission(bundleIndex: Int) =
            VotingDelegationSubmission(
                proof = byteArrayOf(5),
                rk = RK,
                spendAuthSig = SPEND_AUTH_SIG,
                sighash = SIGHASH,
                tx1Effects = byteArrayOf(6),
                nfSigned = byteArrayOf(7),
                cmxNew = byteArrayOf(8),
                govComm = ByteArray(32).also { it[0] = (bundleIndex + 1).toByte() },
                govNullifiers = emptyList(),
                alpha = ByteArray(0),
                voteRoundId = ROUND_ID
            )

        fun castVoteCommitment(
            proposalId: Int,
            choice: Int
        ): VotingVoteCommitment {
            val vanNullifier = ByteArray(32).also { it[0] = 1 }
            val voteAuthorityNoteNew =
                ByteArray(32).also {
                    it[0] = (0x20 + proposalId).toByte()
                    it[1] = choice.toByte()
                }
            val voteCommitment =
                ByteArray(32).also {
                    it[0] = (0x30 + proposalId).toByte()
                    it[1] = choice.toByte()
                }
            return VotingVoteCommitment(
                vanNullifier = vanNullifier,
                voteAuthorityNoteNew = voteAuthorityNoteNew,
                voteCommitment = voteCommitment,
                rVpk = byteArrayOf(4),
                voteAuthSig = byteArrayOf((0x50 + proposalId + choice).toByte()),
                anchorHeight = 10,
                encSharesJson = """[{"c1":"06","c2":"07","share_index":0}]""",
                rawBundleJson =
                    """
                    {
                      "van_nullifier":"${vanNullifier.toHexString()}",
                      "vote_authority_note_new":"${voteAuthorityNoteNew.toHexString()}",
                      "vote_commitment":"${voteCommitment.toHexString()}",
                      "proposal_id":$proposalId,
                      "proof":"04",
                      "enc_shares":[{"c1":"06","c2":"07","share_index":0}],
                      "anchor_height":10,
                      "vote_round_id":"$ROUND_ID",
                      "shares_hash":"08",
                      "share_blinds":[],
                      "share_comms":[],
                      "r_vpk_bytes":"04",
                      "alpha_v":"09"
                    }
                    """.trimIndent()
            )
        }

        fun ByteArray.toHexString() = joinToString(separator = "") { byte -> "%02x".format(byte) }

        fun sharePayloadsJson() =
            """
            [
              {
                "shares_hash":"08",
                "proposal_id":1,
                "vote_decision":0,
                "enc_share":{"c1":"06","c2":"07","share_index":0},
                "tree_position":12,
                "vote_round_id":"$ROUND_ID",
                "share_comms":[],
                "primary_blind":"09"
              }
            ]
            """.trimIndent()

        fun leafBlock(
            height: Long,
            startIndex: Long,
            leaves: List<ByteArray>
        ) = CommitmentTreeLeafBlock(
            height = height,
            startIndex = startIndex,
            leavesBase64 = leaves.map { Base64.getEncoder().encodeToString(it) }
        )

        fun votingSession(proposalCount: Int = 1) =
            VotingSession(
                voteRoundId = ROUND_ID.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                snapshotHeight = 3_459_350,
                snapshotBlockhash = ByteArray(32),
                proposalsHash = ByteArray(32),
                voteEndTime = Instant.parse("2100-01-02T00:00:00Z"),
                ceremonyStart = Instant.parse("2100-01-01T00:00:00Z"),
                eaPK = ByteArray(32),
                vkZkp1 = ByteArray(32),
                vkZkp2 = ByteArray(32),
                vkZkp3 = ByteArray(32),
                ncRoot = ByteArray(32),
                nullifierIMTRoot = ByteArray(32),
                creator = "creator",
                title = "Round",
                description = "Round",
                discussionUrl = null,
                proposals =
                    (1..proposalCount).map { proposalId ->
                        Proposal(
                            id = proposalId,
                            title = "Proposal $proposalId",
                            description = "Proposal $proposalId",
                            options = listOf(VoteOption(0, "Yes"), VoteOption(1, "No"))
                        )
                    },
                status = SessionStatus.ACTIVE,
                createdAtHeight = 1
            )

        suspend fun keystoneAccount() =
            KeystoneAccount(
                sdkAccount = AccountFixture.new(),
                unified =
                    UnifiedInfo(
                        address = WalletAddressFixture.unified(),
                        balance = WalletBalanceFixture.newLong()
                    ),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparent =
                    TransparentInfo(
                        address = WalletAddressFixture.transparent(),
                        balance = Zatoshi(0)
                    ),
                isSelected = true
            )
    }
}
