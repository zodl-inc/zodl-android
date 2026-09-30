package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSigningRequest
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.VotingRoundSessionContext
import com.sparrowwallet.hummingbird.UREncoder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Coverage for [VotingKeystoneRepositoryImpl.createPcztEncoder] -- the Keystone Sign-screen entry
 * point into the delegation pipeline, previously untested at the class level (the only existing
 * coverage in this package, `VotingKeystoneRepositoryRejectMismatchTest`, drives just the
 * standalone [rejectMismatchedKeystoneSighash] function, not this class). Fills part of nit #13 of
 * Milan's review of PR #6 (pre-4.0 `VotingKeystoneRepositoryTest.kt` was deleted during the
 * round-driver port with no replacement). Also doubles as regression coverage for two of this same
 * review's earlier fixes: the precompute-cancel-ordering guard (should-fix #5) and the empty-
 * voteServerUrls guard (nit #11), both added to this exact function this session.
 */
class VotingKeystoneRepositoryCreatePcztEncoderTest {
    @Test
    fun `derives a signing bundle for the next unsigned bundle index and persists the pending request`() =
        runTest {
            val env = Env()
            val request = env.signingRequest(bundleIndex = 0, actionIndex = 0)
            coEvery {
                env.votingKeystoneSessionHolder.getKeystoneSigningRequests(env.roundId, listOf(0))
            } returns listOf(request)

            val bundle = env.createPcztEncoder()

            assertEquals(0, bundle.bundleIndex)
            assertEquals(env.roundId, bundle.roundId)
            coVerify(exactly = 1) {
                env.votingRecoveryRepository.storePendingKeystoneRequest(
                    accountUuid = env.accountUuid,
                    roundId = env.roundId,
                    bundleIndex = 0,
                    actionIndex = 0,
                    redactedPczt = request.redactedPcztBytes,
                    expectedSighash = request.pcztSighash,
                    expectedRk = request.rk
                )
            }
        }

    @Test
    fun `skips already-signed bundle indices and derives the first one still unsigned`() =
        runTest {
            val env = Env(bundleCount = 3, keystoneBundleSignatures = setOf(0))
            val request = env.signingRequest(bundleIndex = 1, actionIndex = 0)
            coEvery {
                env.votingKeystoneSessionHolder.getKeystoneSigningRequests(env.roundId, listOf(1))
            } returns listOf(request)

            val bundle = env.createPcztEncoder()

            assertEquals(1, bundle.bundleIndex)
        }

    @Test
    fun `throws VotingKeystoneBundlesAlreadySignedException when every bundle is already signed`() =
        runTest {
            val env = Env(bundleCount = 2, keystoneBundleSignatures = setOf(0, 1))

            assertFailsWith<VotingKeystoneBundlesAlreadySignedException> {
                env.createPcztEncoder()
            }
        }

    @Test
    fun `background precompute is cancelled and awaited before the delegation pipeline opens`() =
        runTest {
            val env = Env()
            coEvery {
                env.votingKeystoneSessionHolder.getKeystoneSigningRequests(env.roundId, listOf(0))
            } returns listOf(env.signingRequest(bundleIndex = 0, actionIndex = 0))

            env.createPcztEncoder()

            // Milan's review of PR #6, should-fix #5's own regression -- see this class's
            // createPcztEncoder for the full reasoning (this is a second, independent entry point
            // into the same native (dbPath, walletId) lock SubmitVotesUseCase's own identical
            // guard protects).
            coVerifyOrder {
                env.votingProofPrecomputeRepository.cancelAndAwaitPrecompute(env.accountUuid, env.roundId)
                env.votingKeystoneSessionHolder.ensureDelegationPipeline(
                    roundId = any(),
                    votingDbPath = any(),
                    accountUuidString = any(),
                    networkId = any(),
                    proposals = any(),
                    hotkeySecret = any(),
                    chainEndpoints = any(),
                    ceremonyStartSeconds = any(),
                    voteEndTimeSeconds = any(),
                    delegationInputs = any()
                )
            }
        }

    @Test
    fun `an empty configured vote server list fails fast instead of reaching the delegation pipeline`() =
        runTest {
            val env = Env(voteServerUrls = emptyList())

            assertFailsWith<IllegalStateException> {
                env.createPcztEncoder()
            }

            // Nit #11 of the same review: without this guard the empty list would have reached
            // ensureDelegationPipeline as chainEndpoints and failed deep inside the native
            // delegation pipeline instead of surfacing here.
            coVerify(exactly = 0) {
                env.votingKeystoneSessionHolder.ensureDelegationPipeline(
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

    /** Shared fixture wiring for [VotingKeystoneRepositoryImpl.createPcztEncoder]. */
    private class Env(
        bundleCount: Int = 1,
        keystoneBundleSignatures: Set<Int> = emptySet(),
        voteServerUrls: List<String> = listOf("https://vote.example")
    ) {
        val roundIdBytes = ByteArray(32) { 0x0F }
        val roundId = roundIdBytes.toHex()
        val accountUuid: String

        val accountDataSource = mockk<AccountDataSource>()
        val votingKeystoneSessionHolder = mockk<VotingKeystoneSessionHolder>(relaxed = true)
        val votingCryptoClient = mockk<VotingCryptoClient>(relaxed = true)
        val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
        val votingHotkeySeedProvider = mockk<VotingHotkeySeedProvider>()
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val keystoneSDKProvider = mockk<KeystoneSDKProvider>()
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val synchronizer = mockk<Synchronizer>()

        private val repository =
            VotingKeystoneRepositoryImpl(
                accountDataSource = accountDataSource,
                votingKeystoneSessionHolder = votingKeystoneSessionHolder,
                votingCryptoClient = votingCryptoClient,
                resolveVotingRoundSession = resolveVotingRoundSession,
                votingRecoveryRepository = votingRecoveryRepository,
                votingHotkeySeedProvider = votingHotkeySeedProvider,
                synchronizerProvider = synchronizerProvider,
                keystoneSDKProvider = keystoneSDKProvider,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository
            )

        init {
            val voteEndTime = Instant.parse("2026-09-23T12:00:00Z")
            val session = votingSession(voteRoundId = roundIdBytes, voteEndTime = voteEndTime, snapshotHeight = 100L)
            val selectedAccount = keystoneAccount()
            accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            val serviceConfig =
                VotingServiceConfig(
                    voteServers =
                        voteServerUrls.map { url -> VotingServiceConfig.ServiceEndpoint(url = url, label = "v") },
                    pirEndpoints =
                        listOf(VotingServiceConfig.ServiceEndpoint(url = "https://pir.example", label = "p1")),
                    pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048)
                )
            every { synchronizer.network } returns ZcashNetwork.Testnet
            coEvery { synchronizer.getTreeState(any()) } returns ByteArray(32)

            coEvery { accountDataSource.getSelectedAccount() } returns selectedAccount
            coEvery { resolveVotingRoundSession(roundId) } returns
                VotingRoundSessionContext(session = session, serviceConfig = serviceConfig)
            coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
            coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/tmp/voting-wallet.db"
            coEvery { votingHotkeySeedProvider.get(accountUuid) } returns ByteArray(32)
            coEvery { votingRecoveryRepository.get(accountUuid, roundId) } returns
                VotingRecoverySnapshot(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    bundleCount = bundleCount,
                    keystoneBundleSignatures =
                        keystoneBundleSignatures.associateWith { index ->
                            VotingKeystoneBundleSignature(
                                spendAuthSigBase64 = "sig",
                                sighashBase64 = "sighash",
                                rkBase64 = "rk"
                            )
                        }
                )
            every { keystoneSDKProvider.generatePczt(any()) } returns mockk<UREncoder>(relaxed = true)
        }

        suspend fun createPcztEncoder() = repository.createPcztEncoder(accountUuid, roundId)

        fun signingRequest(
            bundleIndex: Int,
            actionIndex: Int
        ): VotingKeystoneSigningRequest =
            VotingKeystoneSigningRequest(
                pcztBytes = ByteArray(4),
                redactedPcztBytes = ByteArray(4),
                pcztSighash = ByteArray(32) { 1 },
                rk = ByteArray(32) { 2 },
                actionIndex = actionIndex,
                displayMemo = "memo",
                eligibleWeightZatoshi = 100L,
                delegatedWeightZatoshi = 100L,
                bundleCount = 1,
                bundleIndex = bundleIndex
            )

        private fun keystoneAccount(): KeystoneAccount =
            KeystoneAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentBalance = Zatoshi(0),
                isSelected = true
            )

        private fun votingSession(
            voteRoundId: ByteArray,
            voteEndTime: Instant,
            snapshotHeight: Long
        ) = VotingSession(
            voteRoundId = voteRoundId,
            snapshotHeight = snapshotHeight,
            snapshotBlockhash = ByteArray(32) { 2 },
            proposalsHash = ByteArray(32) { 3 },
            voteEndTime = voteEndTime,
            ceremonyStart = voteEndTime.minusSeconds(3_600),
            eaPK = ByteArray(32) { 4 },
            vkZkp1 = ByteArray(32) { 5 },
            vkZkp2 = ByteArray(32) { 6 },
            vkZkp3 = ByteArray(32) { 7 },
            ncRoot = ByteArray(32) { 8 },
            nullifierIMTRoot = ByteArray(32) { 9 },
            creator = "creator",
            title = "Voting Round",
            description = "Voting round description",
            discussionUrl = null,
            proposals =
                listOf(
                    Proposal(
                        id = 1,
                        title = "Proposal",
                        description = "Proposal description",
                        options =
                            listOf(
                                VoteOption(id = 0, label = "No"),
                                VoteOption(id = 1, label = "Yes")
                            )
                    )
                ),
            status = SessionStatus.ACTIVE,
            createdAtHeight = 1
        )
    }
}
