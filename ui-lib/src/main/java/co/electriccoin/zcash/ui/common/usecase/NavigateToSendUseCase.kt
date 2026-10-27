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
    private val rejectIfSelectedAccountCannotSpend: RejectIfSelectedAccountCannotSpendUseCase,
) {
    /**
     * A selected account that cannot spend gets the unsupported-operation error instead of the
     * send form.
     */
    suspend operator fun invoke() {
        if (rejectIfSelectedAccountCannotSpend()) return
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
