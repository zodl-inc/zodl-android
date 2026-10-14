package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Proposal
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ShieldTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

/**
 * [CancelLedgerSigningUseCase] leaves the sign sheet for the confirm surface beneath it, which keeps
 * its own proposal - except a shield proposal, which has no confirm surface holding it and is
 * dropped along with the session. A sheet that is gone stops its session without navigating,
 * unless the device has signed and submission is under way.
 */
class CancelLedgerSigningUseCaseTest {
    @Test
    fun aShieldProposalClearsTheRepositoryInsteadOfJustCancellingTheSession() {
        val repository = repository(ShieldTransactionProposal(mockk<Proposal>()))
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val useCase = CancelLedgerSigningUseCase(repository, navigationRouter)

        useCase()

        verify(exactly = 1) { repository.clear() }
        verify(exactly = 0) { repository.cancelSigning() }
        verify(exactly = 1) { navigationRouter.back() }
    }

    @Test
    fun anyOtherProposalOnlyCancelsTheSession() {
        val repository = repository(mockk<RegularTransactionProposal>(relaxed = true))
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val useCase = CancelLedgerSigningUseCase(repository, navigationRouter)

        useCase()

        verify(exactly = 1) { repository.cancelSigning() }
        verify(exactly = 0) { repository.clear() }
        verify(exactly = 1) { navigationRouter.back() }
    }

    @Test
    fun stoppingTheSessionCancelsItWithoutNavigating() {
        val repository = repository(mockk<RegularTransactionProposal>(relaxed = true), LedgerSigningState.Connecting)
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)

        CancelLedgerSigningUseCase(repository, navigationRouter).stopSession()

        verify(exactly = 1) { repository.cancelSigning() }
        verify(exactly = 0) { navigationRouter.back() }
    }

    @Test
    fun stoppingASignedSessionLeavesItsSubmissionAlone() {
        val repository = repository(mockk<RegularTransactionProposal>(relaxed = true), LedgerSigningState.Signed)

        CancelLedgerSigningUseCase(repository, mockk(relaxed = true)).stopSession()

        verify(exactly = 0) { repository.cancelSigning() }
        verify(exactly = 0) { repository.clear() }
    }

    private fun repository(
        proposal: TransactionProposal,
        signingState: LedgerSigningState? = null,
    ) = mockk<LedgerProposalRepository>(relaxed = true) {
        every { transactionProposal } returns MutableStateFlow(proposal)
        every { this@mockk.signingState } returns MutableStateFlow(signingState)
    }
}
