package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.BundleDelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingDelegationPirPrecomputeKey
import co.electriccoin.zcash.ui.common.repository.VotingDelegationPirPrecomputeRequest
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
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubmitVotesUseCaseBackgroundProofTest {
    @Test
    fun reusesBackgroundProofWhenPhaseProved() =
        runTest {
            val fixture = BackgroundProofFixture()

            assertFailsWith<StoppedAfterDelegationProof> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(1, fixture.backgroundProofWaits)
            fixture.assertOnDemandProofCount(0)
            assertEquals(DelegationPhase.PROVED, fixture.delegationPhases[0])
        }

    @Test
    fun provesOnDemandWhenBackgroundProofFailed() =
        runTest {
            val fixture = BackgroundProofFixture(backgroundProofSucceeds = false)

            assertFailsWith<StoppedAfterDelegationProof> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(1, fixture.backgroundProofWaits)
            fixture.assertOnDemandProofCount(1)
        }

    @Test
    fun ignoresBackgroundProofWhenSetupJustBuilt() =
        runTest {
            // A software wallet's construct step writes fresh alpha, so any background proof was
            // produced against material that no longer applies.
            val fixture = BackgroundProofFixture(selectedAccount = softwareAccount())

            assertFailsWith<StoppedAfterDelegationProof> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(0, fixture.backgroundProofWaits)
            fixture.assertOnDemandProofCount(1)
        }

    @Test
    fun ignoresBackgroundProofForRebuiltBundle() =
        runTest {
            val fixture = BackgroundProofFixture(rebuiltSinceProofBundles = setOf(0))

            assertFailsWith<StoppedAfterDelegationProof> {
                fixture.newUseCase()(ROUND_ID, mapOf(1 to 0))
            }

            assertEquals(0, fixture.backgroundProofWaits)
            fixture.assertOnDemandProofCount(1)
        }

    private class BackgroundProofFixture(
        private val selectedAccount: WalletAccount = keystoneAccount(),
        private val backgroundProofSucceeds: Boolean = true,
        rebuiltSinceProofBundles: Set<Int> = emptySet()
    ) {
        val crypto = mockk<VotingCryptoClient>(relaxed = true)
        val delegationPhases = mutableListOf(DelegationPhase.PCZT_BUILT)
        var backgroundProofWaits = 0

        private val api = mockk<VotingApiProvider>(relaxed = true)
        private val walletSeedBytes = mockk<GetWalletSeedBytesUseCase>()
        private val accountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
        private val recoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)

        // A hand-written fake rather than a mock: awaitDelegationProof returns Result, a value
        // class MockK cannot box through a stubbed suspend call.
        private val proofPrecomputeRepository =
            object : VotingProofPrecomputeRepository {
                override fun warmProvingCaches() = Unit

                override fun startDelegationPirPrecompute(request: VotingDelegationPirPrecomputeRequest) = Unit

                override suspend fun awaitDelegationPirPrecompute(
                    key: VotingDelegationPirPrecomputeKey
                ) = null

                override suspend fun awaitDelegationProof(key: VotingDelegationPirPrecomputeKey): Result<Unit>? {
                    backgroundProofWaits += 1
                    return if (backgroundProofSucceeds) {
                        delegationPhases[0] = DelegationPhase.PROVED
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("background proof failed"))
                    }
                }

                override fun cancelBackgroundProofs() = Unit
            }
        private val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)
        private val prepareVotingRound = mockk<PrepareVotingRoundUseCase>()
        private val resolveVotingRoundSession = mockk<ResolveVotingRoundSessionUseCase>()
        private val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()
        private val pirSnapshotResolver = mockk<PirSnapshotResolver>()
        private val hotkeySeedProvider = mockk<VotingHotkeySeedProvider>()

        private var recovery =
            VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = ROUND_ID,
                phase = VotingRecoveryPhase.BUNDLES_PREPARED,
                bundleCount = 1,
                eligibleWeight = 1,
                bundleWeights = listOf(1),
                hotkeyAddress = "hotkey",
                rebuiltSinceProofBundles = rebuiltSinceProofBundles,
                keystoneBundleSignatures =
                    mapOf(
                        0 to
                            VotingKeystoneBundleSignature(
                                spendAuthSigBase64 = encode(byteArrayOf(2)),
                                sighashBase64 = encode(byteArrayOf(3)),
                                rkBase64 = encode(byteArrayOf(4))
                            )
                    )
            )

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
                    session = votingSession(),
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
            coEvery { hotkeySeedProvider.get(accountUuid) } returns ByteArray(64) { 9 }
            coEvery { walletSeedBytes() } returns ByteArray(64)

            coEvery { crypto.getWalletNotesJson(any(), any(), any(), any()) } returns "[]"
            coEvery { crypto.openVotingDb(any()) } returns 1
            coEvery { crypto.getVotes(any(), any()) } returns emptyList()
            coEvery { crypto.getShareDelegations(any(), any()) } returns emptyList()
            coEvery { crypto.getDelegationTxHash(any(), any(), any()) } returns VotingTxHashLookup.NotFound
            coEvery { crypto.delegationPhases(any(), any()) } answers {
                delegationPhases.mapIndexed { index, phase -> BundleDelegationPhase(index, phase) }
            }
            coEvery { crypto.generateNoteWitnessesJson(any(), any(), any(), any(), any(), any()) } returns "{}"
            coEvery { crypto.extractOrchardFvkFromUfvk(any(), any()) } returns ByteArray(32)
            coEvery {
                crypto.buildAndProveDelegation(
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
                delegationPhases[0] = DelegationPhase.PROVED
                mockk(relaxed = true)
            }
            // Stopping here keeps the fixture to the delegation-proof decision, which is what these
            // tests are about.
            coEvery {
                crypto.getDelegationSubmissionWithKeystoneSignature(any(), any(), any(), any(), any())
            } throws StoppedAfterDelegationProof()
            coEvery {
                crypto.getDelegationSubmission(any(), any(), any(), any(), any(), any(), any(), any())
            } throws StoppedAfterDelegationProof()
        }

        fun assertOnDemandProofCount(expected: Int) {
            coVerify(exactly = expected) {
                crypto.buildAndProveDelegation(
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
                getWalletSeedBytes = walletSeedBytes,
                prepareVotingRound = prepareVotingRound,
                votingShareTrackingScheduler = mockk<VotingShareTrackingScheduler>(relaxed = true)
            )
    }

    private class StoppedAfterDelegationProof : CancellationException()

    private companion object {
        const val ROUND_ID = "1111111111111111111111111111111111111111111111111111111111111111"

        fun encode(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

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
                            title = "Proposal 1",
                            description = "Proposal 1",
                            options = listOf(VoteOption(0, "Yes"), VoteOption(1, "No"))
                        )
                    ),
                status = SessionStatus.ACTIVE,
                createdAtHeight = 1
            )

        fun keystoneAccount() =
            KeystoneAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                transparentBalance = Zatoshi(0),
                isSelected = true
            )

        fun softwareAccount() =
            ZashiAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                saplingAddress = WalletAddressFixture.SAPLING_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                saplingBalance = WalletBalanceFixture.newLong(),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentBalance = Zatoshi(0),
                isSelected = true
            )
    }
}
