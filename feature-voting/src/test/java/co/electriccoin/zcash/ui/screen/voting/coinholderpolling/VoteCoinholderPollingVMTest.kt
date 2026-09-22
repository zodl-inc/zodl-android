package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingServiceConfigUseCase
import co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase
import co.electriccoin.zcash.ui.common.usecase.WarmVotingPirProofsUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * voting-5.0.0 background-precompute port (Task 4 fix round): covers the
 * [VoteCoinholderPollingVM.onScreenEntered] PIR proof warm-up trigger, which was previously
 * untested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteCoinholderPollingVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onScreenEntered warms PIR proofs`() =
        runTest {
            val warmVotingPirProofs = mockk<WarmVotingPirProofsUseCase>(relaxed = true)
            val vm = vm(warmVotingPirProofs = warmVotingPirProofs)

            vm.onScreenEntered()
            advanceUntilIdle()

            coVerify(exactly = 1) { warmVotingPirProofs() }
        }

    @Test
    fun `constructing the VM alone does not warm PIR proofs`() =
        runTest {
            val warmVotingPirProofs = mockk<WarmVotingPirProofsUseCase>(relaxed = true)

            vm(warmVotingPirProofs = warmVotingPirProofs)
            advanceUntilIdle()

            coVerify(exactly = 0) { warmVotingPirProofs() }
        }

    private fun vm(
        warmVotingPirProofs: WarmVotingPirProofsUseCase,
        refreshVotingServiceConfig: RefreshVotingServiceConfigUseCase = mockk(relaxed = true),
        refreshVotingRounds: RefreshVotingRoundsUseCase = mockk(relaxed = true),
        configurationRepository: ConfigurationRepository = mockk(relaxed = true),
        votingChainConfigRepository: VotingChainConfigRepository = mockk(relaxed = true),
        votingConfigRepository: VotingConfigRepository = mockk(relaxed = true),
        votingApiProvider: VotingApiProvider = mockk(relaxed = true),
        votingApiRepository: VotingApiRepository = mockk(relaxed = true),
        votingRecoveryRepository: VotingRecoveryRepository = mockk(relaxed = true),
        votingSessionStore: VotingSessionStore = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        errorStateMapper: ErrorMapperUseCase = mockk(relaxed = true),
        trackVotingShares: TrackVotingSharesUseCase = mockk(relaxed = true),
        observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase = mockk(relaxed = true),
    ) = VoteCoinholderPollingVM(
        refreshVotingServiceConfig = refreshVotingServiceConfig,
        refreshVotingRounds = refreshVotingRounds,
        configurationRepository = configurationRepository,
        votingChainConfigRepository = votingChainConfigRepository,
        votingConfigRepository = votingConfigRepository,
        votingApiProvider = votingApiProvider,
        votingApiRepository = votingApiRepository,
        votingRecoveryRepository = votingRecoveryRepository,
        votingSessionStore = votingSessionStore,
        navigationRouter = navigationRouter,
        errorStateMapper = errorStateMapper,
        trackVotingShares = trackVotingShares,
        warmVotingPirProofs = warmVotingPirProofs,
        observeSelectedWalletAccount = observeSelectedWalletAccount,
    )
}
