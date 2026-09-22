package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.AccountFixture
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletBalanceFixture
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
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

class WarmVotingPirProofsUseCaseTest {
    @Test
    fun `invoke starts PIR warmup with the resolved config and wallet notes`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)
            coEvery {
                env.votingCryptoClient.getWalletNotesJson(
                    walletDbPath = "/wallet/db",
                    snapshotHeight = 500L,
                    networkId = 0,
                    accountUuidBytes = env.account.sdkAccount.accountUuid.value
                )
            } returns "[{\"note\":1}]"

            env.useCase()

            coVerify(exactly = 1) {
                env.votingProofPrecomputeRepository.startPirWarmup(
                    VotingPirWarmupRequest(
                        accountUuid = env.accountUuidString,
                        walletId = env.accountUuidString,
                        votingDbPath = "/wallet/voting.sqlite3",
                        snapshotHeight = 500L,
                        pirEndpoints = listOf("https://pir-a", "https://pir-b"),
                        pirLayout = VotingPirLayout(pirDepth = 3, tier0Layers = 1, tier1Layers = 2, polyLen = 2048),
                        networkId = 0,
                        notesJson = "[{\"note\":1}]"
                    )
                )
            }
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
    fun `invoke is a no-op when the wallet has not fully scanned yet`() =
        runTest {
            val env = environment()
            coEvery { env.votingConfigRepository.get() } returns
                VotingConfigSnapshot(serviceConfig = env.serviceConfig(), source = VotingConfigSource.REMOTE)
            every { env.synchronizer.fullyScannedHeight } returns MutableStateFlow(null)

            env.useCase()

            coVerify(exactly = 0) { env.votingProofPrecomputeRepository.startPirWarmup(any()) }
        }

    @Test
    fun `invoke is a no-op when the wallet has no spendable notes`() =
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

    private fun environment(): Environment {
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

        val synchronizer = mockk<Synchronizer>()
        every { synchronizer.fullyScannedHeight } returns MutableStateFlow(BlockHeight.new(500L))
        every { synchronizer.network } returns ZcashNetwork.Testnet

        coEvery { synchronizerProvider.getSynchronizer() } returns synchronizer
        coEvery { synchronizerProvider.getVotingWalletDbPath() } returns "/wallet/db"
        coEvery { getSelectedWalletAccount() } returns account

        val useCase =
            WarmVotingPirProofsUseCase(
                votingConfigRepository = votingConfigRepository,
                votingCryptoClient = votingCryptoClient,
                synchronizerProvider = synchronizerProvider,
                getSelectedWalletAccount = getSelectedWalletAccount,
                votingProofPrecomputeRepository = votingProofPrecomputeRepository
            )

        return Environment(
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
}
