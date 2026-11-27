package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository

class GetProposalUseCase(
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val ledgerProposalPipeline: LedgerProposalPipeline,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val accountDataSource: AccountDataSource,
) {
    suspend operator fun invoke(): TransactionProposal =
        when (accountDataSource.getSelectedAccount()) {
            is KeystoneAccount -> keystoneProposalRepository.getTransactionProposal()
            is LedgerAccount -> ledgerProposalPipeline.getTransactionProposal()
            is ViewOnlyAccount -> throw AccountOperationUnsupportedException.viewOnly()
            is ZodlAccount -> zashiProposalRepository.getTransactionProposal()
        }
}
