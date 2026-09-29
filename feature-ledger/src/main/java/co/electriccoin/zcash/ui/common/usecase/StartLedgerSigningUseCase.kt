package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository

class StartLedgerSigningUseCase(
    private val ledgerProposalRepository: LedgerProposalRepository,
) {
    operator fun invoke() = ledgerProposalRepository.startSigning()
}
