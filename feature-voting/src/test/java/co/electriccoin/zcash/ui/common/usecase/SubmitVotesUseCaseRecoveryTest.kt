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
import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingCommittedVoteRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationSubmission
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteCommitment
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneBundleSignature
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
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

    private class CastVoteRecoveryFixture(
        private val selectedAccount: KeystoneAccount
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
        var closeDbCalls = 0

        private val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
        var recovery =
            VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = ROUND_ID,
                phase = VotingRecoveryPhase.DELEGATION_SUBMITTED,
                bundleCount = 1,
                eligibleWeight = 1,
                bundleWeights = listOf(1),
                hotkeyAddress = "hotkey"
            )

        private val recoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        private val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)
        private val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
        private val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        private val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        private val pirSnapshotResolver = mockk<PirSnapshotResolver>()
        private val hotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        private val session = votingSession()
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
            coEvery { recoveryRepository.markProposalSubmitted(accountUuid, ROUND_ID, 1) } answers {
                recovery = recovery.copy(submittedProposalIds = recovery.submittedProposalIds + 1)
            }
            coEvery { hotkeySeedProvider.get(accountUuid) } returns ByteArray(32) { 9 }

            coEvery { crypto.getWalletNotesJson(any(), any(), any(), any()) } returns "[]"
            coEvery { crypto.openVotingDb(any()) } returns 1
            coEvery { crypto.closeVotingDb(any()) } answers { closeDbCalls += 1 }
            coEvery { crypto.getVotes(any(), any()) } returns emptyList()
            coEvery { crypto.getShareDelegations(any(), any()) } returns emptyList()
            coEvery { crypto.getVoteTxHash(any(), any(), any(), any()) } returns VotingTxHashLookup.NotFound
            coEvery { crypto.syncVoteTree(any(), any(), any()) } returns 10
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
            } returns
                castVoteCommitment()
            coEvery { crypto.storeVoteTxHash(any(), any(), any(), any(), any()) } answers {
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
                val bundleIndex = if (firstArg<co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration>().vanCmx[0] == 1.toByte()) 0 else 1
                submissionCounts[bundleIndex] = submissionCounts.getValue(bundleIndex) + 1
                when {
                    bundleIndex == 0 && bundle0Failure != null ->
                        throw bundle0Failure

                    bundleIndex == 0 ->
                        TxResult(txHash = "", code = 2, log = "nullifier already spent")

                    submissionCounts.getValue(1) == 1 ->
                        throw FirstRunInterrupted()

                    else ->
                        TxResult(txHash = "bundle-1-tx", code = 0)
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
                    1L ->
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

                    11L ->
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

                    else -> error("Unexpected cursor $fromHeight")
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

        fun castVoteCommitment() =
            VotingVoteCommitment(
                vanNullifier = byteArrayOf(1),
                voteAuthorityNoteNew = byteArrayOf(2),
                voteCommitment = byteArrayOf(3),
                rVpk = byteArrayOf(4),
                voteAuthSig = byteArrayOf(5),
                anchorHeight = 10,
                encSharesJson = """[{"c1":"06","c2":"07","share_index":0}]""",
                rawBundleJson =
                    """
                    {
                      "van_nullifier":"01",
                      "vote_authority_note_new":"02",
                      "vote_commitment":"03",
                      "proposal_id":1,
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

        fun votingSession() =
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
                    listOf(
                        Proposal(
                            id = 1,
                            title = "Proposal",
                            description = "Proposal",
                            options = listOf(VoteOption(0, "Yes"), VoteOption(1, "No"))
                        )
                    ),
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
