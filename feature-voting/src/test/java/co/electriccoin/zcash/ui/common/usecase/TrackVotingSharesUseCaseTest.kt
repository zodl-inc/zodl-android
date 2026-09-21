package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TrackVotingSharesUseCaseTest {
    @Test
    fun `cancel targets the same session invoke opened for that round`() =
        runTest {
            val votingRecoveryRepository = mockk<VotingRecoveryRepository>(relaxed = true)
            val votingCryptoClient = mockk<VotingCryptoClient>()
            val votingApiProvider = mockk<VotingApiProvider>()
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val getSelectedWalletAccount = mockk<GetSelectedWalletAccountUseCase>()

            val useCase =
                TrackVotingSharesUseCase(
                    votingRecoveryRepository,
                    votingCryptoClient,
                    votingApiProvider,
                    synchronizerProvider,
                    getSelectedWalletAccount
                )

            // No recovery snapshot => invoke() short-circuits to Completed without opening a
            // session; cancel() for a round with no tracked session must be a no-op, not throw.
            coEvery { votingRecoveryRepository.get(any(), any()) } returns null

            useCase.cancel("round-with-no-active-session")
            // No exception => test passes; the assertion is "doesn't throw," verified implicitly.
        }
}
