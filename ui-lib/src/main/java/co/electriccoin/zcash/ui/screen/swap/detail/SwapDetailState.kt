package co.electriccoin.zcash.ui.screen.swap.detail

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.transactiondetail.ErrorFooter
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailHeaderState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRowState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState

@Immutable
data class SwapDetailState(
    val transactionHeader: TransactionDetailHeaderState,
    val quoteHeader: SwapQuoteHeaderState,
    val status: TransactionDetailSwapStatusRowState,
    val depositTo: TransactionDetailInfoRowState,
    val recipient: TransactionDetailInfoRowState,
    val totalFees: TransactionDetailInfoRowState,
    val maxSlippage: TransactionDetailInfoRowState,
    val timestamp: TransactionDetailInfoRowState,
    val errorFooter: ErrorFooter?,
    val infoFooter: StringResource?,
    val primaryButton: ButtonState?,
    val onBack: () -> Unit,
)
