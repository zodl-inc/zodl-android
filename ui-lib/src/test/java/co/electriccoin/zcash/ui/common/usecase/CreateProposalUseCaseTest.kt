package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsArgs
import co.electriccoin.zcash.ui.screen.reviewtransaction.ReviewTransactionArgs
import co.electriccoin.zcash.ui.screen.texunsupported.TEXUnsupportedArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * [CreateProposalUseCase] must consult a freshly refreshed balance (not the cached account) before
 * asking the SDK for a proposal, and route a shortfall to the Insufficient Funds sheet exactly like
 * the SDK-reported [InsufficientFundsException] path does.
 */
class CreateProposalUseCaseTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true)
    private val keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true)
    private val accountDataSource = mockk<AccountDataSource>()

    @Test
    fun refreshedBalanceTooLowRoutesToInsufficientFundsWithoutProposing() =
        runBlocking {
            val account = zashi(canSpend = false)
            useCase(account).invoke(zecSend(), floor = false)

            coVerify(exactly = 1) { accountDataSource.refreshSelectedAccount() }
            coVerify(exactly = 0) { accountDataSource.getSelectedAccount() }
            coVerify(exactly = 0) { zashiProposalRepository.createProposal(any()) }
            coVerify(exactly = 0) { keystoneProposalRepository.createProposal(any()) }
            verify { zashiProposalRepository.clear() }
            verify { keystoneProposalRepository.clear() }
            verify(exactly = 1) { navigationRouter.forward(InsufficientFundsArgs) }
            verify(exactly = 0) { navigationRouter.forward(ReviewTransactionArgs) }
        }

    @Test
    fun refreshedBalanceSufficientCreatesZashiProposalAndForwards() =
        runBlocking {
            val send = zecSend()
            useCase(zashi(canSpend = true)).invoke(send, floor = false)

            coVerify(exactly = 1) { zashiProposalRepository.createProposal(send) }
            coVerify(exactly = 0) { keystoneProposalRepository.createProposal(any()) }
            verify(exactly = 1) { navigationRouter.forward(ReviewTransactionArgs) }
            verify(exactly = 0) { navigationRouter.forward(InsufficientFundsArgs) }
        }

    @Test
    fun refreshedBalanceSufficientCreatesKeystoneProposalAndPczt() =
        runBlocking {
            val send = zecSend()
            useCase(keystone(canSpend = true)).invoke(send, floor = false)

            coVerify(exactly = 1) { keystoneProposalRepository.createProposal(send) }
            coVerify(exactly = 1) { keystoneProposalRepository.createPCZTFromProposal() }
            coVerify(exactly = 0) { zashiProposalRepository.createProposal(any()) }
            verify(exactly = 1) { navigationRouter.forward(ReviewTransactionArgs) }
        }

    @Test
    fun keystoneShortfallAlsoRoutesToInsufficientFunds() =
        runBlocking {
            useCase(keystone(canSpend = false)).invoke(zecSend(), floor = false)

            coVerify(exactly = 0) { keystoneProposalRepository.createProposal(any()) }
            verify(exactly = 1) { navigationRouter.forward(InsufficientFundsArgs) }
        }

    @Test
    fun preCheckUsesTheFlooredAmountWhenFlooring() =
        runBlocking {
            val account = zashi(canSpend = true)
            useCase(account).invoke(zecSend(amount = Zatoshi(7_000L)), floor = true)

            // 7 000 zatoshi floors to the nearest 5 000; the SDK receives the same floored amount.
            verify(exactly = 1) { account.canSpend(Zatoshi(5_000L)) }
            coVerify(exactly = 1) { zashiProposalRepository.createProposal(match { it.amount == Zatoshi(5_000L) }) }
        }

    @Test
    fun sdkInsufficientFundsStillRoutesToInsufficientFunds() =
        runBlocking {
            coEvery { zashiProposalRepository.createProposal(any()) } throws InsufficientFundsException()
            useCase(zashi(canSpend = true)).invoke(zecSend(), floor = false)

            verify { zashiProposalRepository.clear() }
            verify { keystoneProposalRepository.clear() }
            verify(exactly = 1) { navigationRouter.forward(InsufficientFundsArgs) }
        }

    @Test
    fun texUnsupportedStillRoutesToTexSheet() =
        runBlocking {
            coEvery { keystoneProposalRepository.createProposal(any()) } throws TexUnsupportedOnKSException()
            useCase(keystone(canSpend = true)).invoke(zecSend(), floor = false)

            verify(exactly = 1) { navigationRouter.forward(TEXUnsupportedArgs) }
            verify { zashiProposalRepository.clear() }
            verify { keystoneProposalRepository.clear() }
        }

    @Test
    fun genericFailureClearsAndRethrows() {
        coEvery { zashiProposalRepository.createProposal(any()) } throws IllegalStateException("boom")
        assertFailsWith<IllegalStateException> {
            runBlocking { useCase(zashi(canSpend = true)).invoke(zecSend(), floor = false) }
        }
        verify { zashiProposalRepository.clear() }
        verify { keystoneProposalRepository.clear() }
        verify(exactly = 0) { navigationRouter.forward(InsufficientFundsArgs) }
        verify(exactly = 0) { navigationRouter.forward(ReviewTransactionArgs) }
    }

    private fun useCase(account: WalletAccount): CreateProposalUseCase {
        coEvery { accountDataSource.refreshSelectedAccount() } returns account
        coEvery { accountDataSource.getSelectedAccount() } returns account
        return CreateProposalUseCase(
            keystoneProposalRepository = keystoneProposalRepository,
            zashiProposalRepository = zashiProposalRepository,
            accountDataSource = accountDataSource,
            navigationRouter = navigationRouter,
        )
    }

    private fun zashi(canSpend: Boolean): ZashiAccount =
        mockk { every { canSpend(any()) } returns canSpend }

    private fun keystone(canSpend: Boolean): KeystoneAccount =
        mockk { every { canSpend(any()) } returns canSpend }

    private suspend fun zecSend(amount: Zatoshi = Zatoshi(10_000L)) =
        ZecSend(
            destination = WalletAddress.Unified.new("destination"),
            amount = amount,
            memo = Memo(""),
            proposal = null
        )
}
