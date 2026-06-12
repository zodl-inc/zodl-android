package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.screen.exchangerateunavailable.ExchangeRateUnavailableArgs
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType
import co.electriccoin.zcash.ui.screen.request.RequestArgs

class NavigateToRequestZecUseCase(
    private val navigationRouter: NavigationRouter,
    private val exchangeRateRepository: ExchangeRateRepository,
) {
    operator fun invoke(addressTypeOrdinal: Int = ReceiveAddressType.Unified.ordinal) {
        val args = RequestArgs(addressTypeOrdinal)
        if (hasExchangeRateError()) {
            navigationRouter.forward(args, ExchangeRateUnavailableArgs)
        } else {
            navigationRouter.forward(args)
        }
    }

    private fun hasExchangeRateError(): Boolean {
        val state = exchangeRateRepository.state.value as? ExchangeRateState.Data ?: return false
        return state.error != null && state.expectedCurrency != FiatCurrency.USD
    }
}
