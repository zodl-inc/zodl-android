package co.electriccoin.zcash.ui.screen.transactiondetail.info

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.ZashiMessageState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
sealed interface TransactionDetailInfoState

@Immutable
data class SendShieldedState(
    val contact: StringResource?,
    val address: StringResource,
    val transactionId: StringResource,
    val onTransactionIdClick: () -> Unit,
    val onTransactionAddressClick: () -> Unit,
    val fee: StringResource,
    val completedTimestamp: StringResource,
    val memo: TransactionDetailMemosState?,
    val note: StringResource?,
    val isPending: Boolean
) : TransactionDetailInfoState

@Immutable
data class SendSwapState(
    val status: SwapStatus?,
    val message: ZashiMessageState?,
    val quoteHeader: SwapQuoteHeaderState,
    val depositAddress: StringResource,
    val totalFees: StringResource?,
    val recipientAddress: StringResource?,
    val transactionId: StringResource,
    val refundedAmount: StringResource?,
    val onTransactionIdClick: () -> Unit,
    val onDepositAddressClick: () -> Unit,
    val onRecipientAddressClick: (() -> Unit)?,
    val maxSlippage: StringResource?,
    val note: StringResource?,
    val isSlippageRealized: Boolean,
    val isPending: Boolean,
    val completedTimestamp: StringResource,
) : TransactionDetailInfoState

@Immutable
data class SendTransparentState(
    val contact: StringResource?,
    val address: StringResource,
    val addressAbbreviated: StringResource,
    val transactionId: StringResource,
    val onTransactionIdClick: () -> Unit,
    val onTransactionAddressClick: () -> Unit,
    val fee: StringResource,
    val completedTimestamp: StringResource,
    val note: StringResource?,
    val isPending: Boolean
) : TransactionDetailInfoState

@Immutable
data class ReceiveShieldedState(
    val memo: TransactionDetailMemosState?,
    val transactionId: StringResource,
    val onTransactionIdClick: () -> Unit,
    val completedTimestamp: StringResource,
    val note: StringResource?,
    val isPending: Boolean
) : TransactionDetailInfoState

@Immutable
data class ReceiveTransparentState(
    val transactionId: StringResource,
    val onTransactionIdClick: () -> Unit,
    val completedTimestamp: StringResource,
    val note: StringResource?,
    val isPending: Boolean
) : TransactionDetailInfoState

@Immutable
data class ShieldingState(
    val transactionId: StringResource,
    val onTransactionIdClick: () -> Unit,
    val completedTimestamp: StringResource,
    val fee: StringResource,
    val note: StringResource?,
    val isPending: Boolean
) : TransactionDetailInfoState

@Immutable
data class TransactionDetailMemosState(
    val memos: List<TransactionDetailMemoState>?,
)

@Immutable
data class TransactionDetailMemoState(
    val content: StringResource,
    val onClick: () -> Unit
)
