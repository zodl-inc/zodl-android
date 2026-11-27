package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.send.Send

class SendTransactionAgainUseCase(
    private val prefillSendUseCase: PrefillSendUseCase,
    private val navigationRouter: NavigationRouter,
    private val rejectIfSelectedAccountCannotSpend: RejectIfSelectedAccountCannotSpendUseCase,
) {
    /**
     * An account that cannot spend gets the unsupported-operation error instead of a prefilled send
     * form.
     */
    suspend operator fun invoke(value: DetailedTransactionData) {
        if (rejectIfSelectedAccountCannotSpend()) return
        prefillSendUseCase.requestFromTransactionDetail(value)
        navigationRouter.forward(
            Send(
                isScanZip321Enabled = false
            )
        )
    }
}
