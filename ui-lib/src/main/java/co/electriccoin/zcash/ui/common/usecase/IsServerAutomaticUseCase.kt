package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository

class IsServerAutomaticUseCase(
    private val automaticServerRepository: AutomaticServerRepository
) {
    fun observe() = automaticServerRepository.isServerAutomatic
}
