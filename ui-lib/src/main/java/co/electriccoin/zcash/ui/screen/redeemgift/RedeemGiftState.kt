package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState

sealed interface RedeemGiftState {
    val onBack: () -> Unit

    /**
     * Checking, pending, empty, error, redeeming and result screens, all drawn with the transaction progress layout.
     */
    data class Status(
        val progress: TransactionProgressState
    ) : RedeemGiftState {
        override val onBack: () -> Unit
            get() = progress.onBack
    }

    /**
     * The card holds funds that can be redeemed now.
     *
     * @param message the note written into the link by the card's sender, if any.
     */
    data class Ready(
        val amount: StringResource,
        val fiatAmount: StringResource?,
        val message: StringResource?,
        val destination: StringResource,
        val redeemButton: ButtonState,
        override val onBack: () -> Unit,
    ) : RedeemGiftState
}
