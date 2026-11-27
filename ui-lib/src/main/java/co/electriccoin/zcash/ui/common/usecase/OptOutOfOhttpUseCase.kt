package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.IsOhttpOptInDismissedProvider

class OptOutOfOhttpUseCase(
    private val isOhttpOptInDismissedProvider: IsOhttpOptInDismissedProvider
) {
    suspend operator fun invoke() = isOhttpOptInDismissedProvider.store(true)
}
