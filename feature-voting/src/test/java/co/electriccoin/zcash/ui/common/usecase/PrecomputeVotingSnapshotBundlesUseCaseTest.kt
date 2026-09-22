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
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingSnapshotBundlePrecomputeRequest
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class PrecomputeVotingSnapshotBundlesUseCaseTest {
    @Test
    fun `invoke starts snapshot bundle precompute for the resolved round`() =
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

            env.useCase(ROUND_ID)

            coVerify(exactly = 1) {
                env.votingProofPrecomputeRepository.startSnapshotBundlePrecompute(
                    VotingSnapshotBundlePrecomputeRequest(
                        accountUuid = env.accountUuidString,
                        walletId = env.accountUuidString,
                        votingDbPath = "/wallet/voting.sqlite3",
                        roundId = ROUND_ID,
                        pirEndpoints = listOf("https://pir-a", "https://pir-b"),
                        pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048),
                        expectedSnapshotHeight = ROUND_SNAPSHOT_HEIGHT,
                        networkId = 0,
                        notesJson = "[{\"note\":1}]"
                    )
                )
            }
        }

    @Test
    fun `invoke is a no-op for an empty round id`() =
        runTest {
            val env = environment()

            env.useCase("")

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startSnapshotBundlePrecompute(any()) }
            coVerify(exactly = 0) { env.votingConfigRepository.get() }
        }

    @Test
    fun `invoke is a no-op when the round is not yet known`() =
        runTest {
            val env = environment(includeRound = false)
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)

            env.useCase(ROUND_ID)

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startSnapshotBundlePrecompute(any()) }
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

            env.useCase(ROUND_ID)

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startSnapshotBundlePrecompute(any()) }
        }

    @Test
    fun `invoke never throws when a dependency fails`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } throws IllegalStateException("boom")

            env.useCase(ROUND_ID)

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startSnapshotBundlePrecompute(any()) }
        }

    private class Environment(
        val votingApiRepository: VotingApiRepository,
        val votingConfigRepository: VotingConfigRepository,
        val votingCryptoClient: VotingCryptoClient,
        val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
        val account: ZashiAccount,
        val accountUuidString: String,
        val useCase: PrecomputeVotingSnapshotBundlesUseCase
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

    private fun environment(includeRound: Boolean = true): Environment {
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

        val rounds = if (includeRound) listOf(round()) else emptyList()
        every { votingApiRepository.snapshot } returns MutableStateFlow(VotingApiSnapshot(rounds = rounds))

        val synchronizer = mockk<Synchronizer>()
        every { synchronizer.network } returns ZcashNetwork.Testnet

        coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
        coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/wallet/db"
        coEvery { getSelectedWalletAccount() } returns account

        val useCase =
            PrecomputeVotingSnapshotBundlesUseCase(
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
            votingProofPrecomputeRepository = votingProofPrecomputeRepository,
            account = account,
            accountUuidString = account.sdkAccount.accountUuid.toVotingAccountScopeId(),
            useCase = useCase
        )
    }

    private fun round() =
        VotingRound(
            id = ROUND_ID,
            title = "Round title",
            description = "Round description",
            discussionUrl = null,
            createdAtHeight = 1,
            snapshotHeight = ROUND_SNAPSHOT_HEIGHT,
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
            status = SessionStatus.ACTIVE
        )

    private companion object {
        const val ROUND_ID = "round-id"
        const val ROUND_SNAPSHOT_HEIGHT = 500L
    }
}
