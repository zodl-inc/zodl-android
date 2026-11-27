package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.isProposalTooLarge
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MaxSendAmountDataSource {
    /**
     * Proposes sending everything [account] can spend to [recipient] and reports what the recipient would
     * receive. [memo] must be null for a recipient that cannot receive one (see
     * [co.electriccoin.zcash.ui.common.model.ProposalMemoPolicy]). A transaction over [sizeBound] fails as
     * [MaxSendAmountFailure.TOO_MANY_NOTES]: Max never sends less than everything.
     */
    @Throws(MaxSendAmountUnavailableException::class, InsufficientFundsException::class)
    suspend fun proposeMaxSendAmount(
        account: WalletAccount,
        recipient: RecipientAddress,
        memo: MemoContent?,
        sizeBound: TransactionSizeBound
    ): MaxSendAmount
}

/**
 * @property amount what the recipient receives, excluding the fee
 * @property fee the fee of the max-send proposal. It is never adjusted: when a buffer is kept back from [amount]
 * (see [co.electriccoin.zcash.ui.common.usecase.MaxSendTarget.OwnTransparentReceiver]) the buffer is not added
 * here, so this is the fee of the proposal that was priced, not of the transaction that is eventually built.
 */
data class MaxSendAmount(
    val amount: Zatoshi,
    val fee: Zatoshi,
)

enum class MaxSendAmountFailure {
    /** The transaction would spend too many notes to fit in a block. */
    TOO_MANY_NOTES,

    /** The recipient is unsupported, or cannot receive the memo that was given. */
    INVALID_RECIPIENT,

    /** The wallet has not synced far enough to propose. */
    SYNC_REQUIRED,
    OTHER
}

class MaxSendAmountUnavailableException(
    val reason: MaxSendAmountFailure,
    cause: Throwable? = null
) : Exception("Max send amount unavailable: $reason", cause)

class MaxSendAmountDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
) : MaxSendAmountDataSource {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun proposeMaxSendAmount(
        account: WalletAccount,
        recipient: RecipientAddress,
        memo: MemoContent?,
        sizeBound: TransactionSizeBound
    ): MaxSendAmount =
        withContext(Dispatchers.IO) {
            try {
                val proposal =
                    synchronizerProvider.getSynchronizer().proposeSendMax(
                        account = account.sdkAccount,
                        recipient = recipient,
                        memo = memo,
                        sizeBound = sizeBound
                    )
                MaxSendAmount(amount = proposal.totalPaymentAmount(), fee = proposal.totalFeeRequired())
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransactionEncoderException.InsufficientFundsException) {
                throw InsufficientFundsException(e)
            } catch (e: Exception) {
                throw MaxSendAmountUnavailableException(e.toMaxSendAmountFailure(), e)
            }
        }
}

private fun Exception.toMaxSendAmountFailure(): MaxSendAmountFailure =
    when {
        isProposalTooLarge() -> {
            MaxSendAmountFailure.TOO_MANY_NOTES
        }

        this is TransactionEncoderException.ProposalRecipientCannotReceiveMemoException ||
            this is TransactionEncoderException.ProposalUnsupportedRecipientException -> {
            MaxSendAmountFailure.INVALID_RECIPIENT
        }

        this is TransactionEncoderException.ProposalSyncRequiredException -> {
            MaxSendAmountFailure.SYNC_REQUIRED
        }

        else -> {
            MaxSendAmountFailure.OTHER
        }
    }
