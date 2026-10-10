package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository

class ObserveLedgerSigningStateUseCase(
    private val ledgerProposalRepository: LedgerProposalRepository,
) {
    operator fun invoke() = ledgerProposalRepository.signingState
}
