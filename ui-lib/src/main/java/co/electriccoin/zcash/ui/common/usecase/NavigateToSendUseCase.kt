package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.screen.exchangerateunavailable.ExchangeRateUnavailableArgs
import co.electriccoin.zcash.ui.screen.send.Send

class NavigateToSendUseCase(
    private val navigationRouter: NavigationRouter,
    private val exchangeRateRepository: ExchangeRateRepository,
) {
    operator fun invoke() {
        if (hasExchangeRateError()) {
            navigationRouter.forward(Send(), ExchangeRateUnavailableArgs)
        } else {
            navigationRouter.forward(Send())
        }
    }

    private fun hasExchangeRateError(): Boolean {
        val state = exchangeRateRepository.state.value as? ExchangeRateState.Data ?: return false
        return state.error != null && state.expectedCurrency != FiatCurrency.USD
    }
}
