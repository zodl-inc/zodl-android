package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository

class SelectLedgerSigningDeviceUseCase(
    private val ledgerProposalRepository: LedgerProposalRepository,
) {
    operator fun invoke(identifier: String) = ledgerProposalRepository.selectDevice(identifier)
}
