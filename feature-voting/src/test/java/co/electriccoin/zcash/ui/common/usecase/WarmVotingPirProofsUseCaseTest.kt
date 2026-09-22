package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VoteOption
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingConfigSource
import co.electriccoin.zcash.ui.common.repository.VotingPirWarmupRequest
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class WarmVotingPirProofsUseCaseTest {
    @Test
    fun `invoke starts PIR warmup keyed on an active round's snapshot height`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(
                    walletDbPath = "/wallet/db",
                    snapshotHeight = ROUND_SNAPSHOT_HEIGHT,
                    networkId = 0,
                    accountUuidBytes = env.account.sdkAccount.accountUuid.value
                )
            } returns "[{\"note\":1}]"

            env.useCase()

            // Must resolve against the active round's own fixed snapshot height -- NOT a live,
            // continuously-changing wallet scan tip -- since PirSnapshotResolver requires an
            // exact height match against what a PIR server currently serves.
            coVerify(exactly = 1) {
                env.votingProofPrecomputeRepository.startPirWarmup(
                    VotingPirWarmupRequest(
                        accountUuid = env.accountUuidString,
                        walletId = env.accountUuidString,
                        votingDbPath = "/wallet/voting.sqlite3",
                        snapshotHeight = ROUND_SNAPSHOT_HEIGHT,
                        pirEndpoints = listOf("https://pir-a", "https://pir-b"),
                        pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048),
                        networkId = 0,
                        notesJson = "[{\"note\":1}]"
                    )
                )
            }
        }

    @Test
    fun `invoke is a no-op when no round is active yet`() =
        runTest {
            val env = environment(rounds = emptyList())
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)

            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
            // Gracefully no-ops without even reading the config -- "nothing to warm yet" is
            // resolved from the round list alone.
            coVerify(exactly = 0) { env.votingConfigRepository.get() }
        }

    @Test
    fun `invoke ignores non-active rounds when picking snapshot heights`() =
        runTest {
            val env =
                environment(
                    rounds =
                        listOf(
                            round(id = "closed-round", snapshotHeight = 999L, status = SessionStatus.TALLYING)
                        )
                )
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)

            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
        }

    @Test
    fun `invoke is a no-op when no config has been cached yet`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } returns null

            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
        }

    @Test
    fun `invoke is a no-op when the config has no PIR endpoints`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(
                    serviceConfig = env.serviceConfig().copy(pirEndpoints = emptyList()),
                    source = VotingConfigSource.REMOTE
                )

            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
        }

    @Test
    fun `invoke is a no-op when the wallet has no spendable notes at the round's snapshot`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(any(), any(), any(), any())
            } returns "[]"

            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
        }

    @Test
    fun `invoke never throws when a dependency fails`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } throws IllegalStateException("boom")

            // Must not throw -- precompute is never allowed to fail the triggering screen.
            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
        }

    private class Environment(
        val votingApiRepository: VotingApiRepository,
        val votingConfigRepository: VotingConfigRepository,
        val votingCryptoClient: VotingCryptoClient,
        val synchronizerProvider: SynchronizerProvider,
        val synchronizer: Synchronizer,
        val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
        val account: ZashiAccount,
        val accountUuidString: String,
        val useCase: WarmVotingPirProofsUseCase
    ) {
        fun serviceConfig() =
            VotingServiceConfig(
                pirEndpoints =
                    listOf(
                        VotingServiceConfig.ServiceEndpoint(url = "https://pir-a", label = "a"),
                        VotingServiceConfig.ServiceEndpoint(url = "https://pir-b", label = "b")
                    ),
                pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048)
            )
    }

    private fun environment(rounds: List<VotingRound> = listOf(round())): Environment {
        val votingApiRepository = mockk<VotingApiRepository>()
        val votingConfigRepository = mockk<VotingConfigRepository>()
        val votingCryptoClient = mockk<VotingCryptoClient>()
        val synchronizerProvider = mockk<SynchronizerProvider>()
        val votingProofPrecomputeRepository = mockk<VotingProofPrecomputeRepository>(relaxed = true)
        val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

        val account =
            ZashiAccount(
                sdkAccount = AccountFixture.new(),
                unifiedAddress = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
                transparentAddress = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING,
                saplingAddress = WalletAddressFixture.SAPLING_ADDRESS_STRING,
                orchardBalance = WalletBalanceFixture.newLong(),
                saplingBalance = WalletBalanceFixture.newLong(0, 0, 0),
                ironwoodBalance = WalletBalanceFixture.newLong(0, 0, 0),
                transparentBalance = Zatoshi(0),
                isSelected = true
            )

        every { votingApiRepository.snapshot } returns MutableStateFlow(VotingApiSnapshot(rounds = rounds))

        val synchronizer = mockk<Synchronizer>()
        every { synchronizer.network } returns ZcashNetwork.Testnet

        coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
        coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/wallet/db"
        coEvery { getSelectedWalletAccount() } returns account

        val useCase =
            WarmVotingPirProofsUseCase(
                votingApiRepository = votingApiRepository,
                votingConfigRepository = votingConfigRepository,
                votingCryptoClient = votingCryptoClient,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository
            )

        return Environment(
            votingApiRepository = votingApiRepository,
            votingConfigRepository = votingConfigRepository,
            votingCryptoClient = votingCryptoClient,
            synchronizerProvider = synchronizerProvider,
            synchronizer = synchronizer,
            votingProofPrecomputeRepository = votingProofPrecomputeRepository,
            account = account,
            accountUuidString = account.sdkAccount.accountUuid.toVotingAccountScopeId(),
            useCase = useCase
        )
    }

    private fun round(
        id: String = ROUND_ID,
        snapshotHeight: Long = ROUND_SNAPSHOT_HEIGHT,
        status: SessionStatus = SessionStatus.ACTIVE
    ) = VotingRound(
        id = id,
        title = "Round title",
        description = "Round description",
        discussionUrl = null,
        createdAtHeight = 1,
        snapshotHeight = snapshotHeight,
        snapshotDate = Instant.parse("2026-09-01T00:00:00Z"),
        votingStart = Instant.parse("2026-09-01T00:00:00Z"),
        votingEnd = Instant.parse("2026-09-08T00:00:00Z"),
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
        status = status
    )

    private companion object {
        const val ROUND_ID = "round-id"
        const val ROUND_SNAPSHOT_HEIGHT = 500L
    }
}
