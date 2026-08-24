package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.sdk.extension.floor
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsArgs
import co.electriccoin.zcash.ui.screen.reviewtransaction.ReviewTransactionArgs
import co.electriccoin.zcash.ui.screen.texunsupported.TEXUnsupportedArgs

class CreateProposalUseCase(
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val accountDataSource: AccountDataSource,
    private val navigationRouter: NavigationRouter,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(zecSend: ZecSend, floor: Boolean) {
        val normalized = if (floor) zecSend.copy(amount = zecSend.amount.floor()) else zecSend
        try {
            // The Send form gates its button on the cached spendable balance, which can be stale while
            // the SDK is between a chain-tip update and the scan of the queued range. Re-read it from
            // the SDK first so an amount the wallet can no longer cover lands on the Insufficient Funds
            // sheet instead of surfacing the SDK's "Insufficient balance (have 0, ...)" as a raw error.
            val account = accountDataSource.refreshSelectedAccount()
            if (!account.canSpend(normalized.amount)) {
                keystoneProposalRepository.clear()
                zashiProposalRepository.clear()
                navigationRouter.forward(InsufficientFundsArgs)
                return
            }
            when (account) {
                is KeystoneAccount -> {
                    keystoneProposalRepository.createProposal(normalized)
                    keystoneProposalRepository.createPCZTFromProposal()
                }

                is ZashiAccount -> {
                    zashiProposalRepository.createProposal(normalized)
                }
            }
            navigationRouter.forward(ReviewTransactionArgs)
        } catch (_: TexUnsupportedOnKSException) {
            navigationRouter.forward(TEXUnsupportedArgs)
            keystoneProposalRepository.clear()
            zashiProposalRepository.clear()
        } catch (_: InsufficientFundsException) {
            keystoneProposalRepository.clear()
            zashiProposalRepository.clear()
            navigationRouter.forward(InsufficientFundsArgs)
        } catch (e: Exception) {
            keystoneProposalRepository.clear()
            zashiProposalRepository.clear()
            throw e
        }
    }
}
