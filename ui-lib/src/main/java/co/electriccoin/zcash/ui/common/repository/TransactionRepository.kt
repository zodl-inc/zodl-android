package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionOutput
import cash.z.ecc.android.sdk.model.TransactionOverview
import cash.z.ecc.android.sdk.model.TransactionRecipient
import cash.z.ecc.android.sdk.model.TransactionState
import cash.z.ecc.android.sdk.model.TransactionState.Confirmed
import cash.z.ecc.android.sdk.model.TransactionState.Expired
import cash.z.ecc.android.sdk.model.TransactionState.Pending
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip318Kind
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

interface TransactionRepository {
    val transactions: Flow<List<Transaction>?>

    suspend fun getMemos(transaction: Transaction): List<String>

    fun observeTransaction(txId: String): Flow<Transaction?>

    fun observeTransactionsByMemo(memo: String): Flow<List<TransactionId>?>

    suspend fun getTransactions(): List<Transaction>

    suspend fun resolveWalletAddress(address: String): WalletAddress?
}

class TransactionRepositoryImpl(
    accountDataSource: AccountDataSource,
    private val synchronizerProvider: SynchronizerProvider,
) : TransactionRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Outputs/recipients are only immutable once a transaction is fully enhanced (raw != null), so only enhanced txs
     * are cached; unenhanced txs are re-fetched via the batched queries on every emission — that re-fetch is what
     * picks up post-enhancement data.
     */
    private val enhancedTxCache = ConcurrentHashMap<String, TxDetails>()

    private val ownAddressCache = ConcurrentHashMap<AccountUuid, String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("TooGenericExceptionCaught")
    override val transactions: Flow<List<Transaction>?> =
        accountDataSource
            .selectedAccount
            .map { it?.sdkAccount?.accountUuid }
            .distinctUntilChanged()
            .flatMapLatest { uuid ->
                if (uuid == null) {
                    flowOf(null)
                } else {
                    synchronizerProvider
                        .synchronizer
                        .flatMapLatest { synchronizer ->
                            if (synchronizer == null) {
                                flowOf(null)
                            } else {
                                val normalizedTransactions =
                                    combine(
                                        synchronizer.getTransactions(uuid),
                                        synchronizer.status,
                                        synchronizer.rejectedTransactionIds
                                    ) { transactions, status, rejected ->
                                        transactions.map {
                                            if (it.isSentTransaction) {
                                                it
                                                    .copy(
                                                        transactionState =
                                                            createTransactionState(
                                                                minedHeight = it.minedHeight,
                                                                transactionState = it.transactionState,
                                                                isSyncing = status == Synchronizer.Status.SYNCING
                                                            ) ?: it.transactionState
                                                    ).asRejectedIfIn(rejected)
                                            } else {
                                                it
                                            }
                                        } to rejected
                                    }.distinctUntilChanged()

                                normalizedTransactions
                                    .conflate()
                                    .map { (transactions, rejected) ->
                                        val now = Instant.now()

                                        // Refresh via batched queries whenever any tx is not yet cached,
                                        // which includes every unenhanced tx since those are never stored.
                                        val needsRefresh =
                                            transactions.any { transaction ->
                                                enhancedTxCache[transaction.txId.txIdString()] == null
                                            }
                                        val freshDetails =
                                            if (needsRefresh) {
                                                try {
                                                    coroutineScope {
                                                        val outputsDeferred =
                                                            async { synchronizer.getTransactionOutputs() }
                                                        val recipientsDeferred =
                                                            async { synchronizer.getRecipients() }
                                                        val batchedOutputs = outputsDeferred.await()
                                                        val batchedRecipients = recipientsDeferred.await()
                                                        transactions.associate { transaction ->
                                                            val details =
                                                                TxDetails(
                                                                    outputs =
                                                                        batchedOutputs[transaction.txId].orEmpty(),
                                                                    recipient =
                                                                        batchedRecipients[transaction.txId]
                                                                            .orEmpty()
                                                                            .let { recipients ->
                                                                                selectDisplayRecipient(recipients)
                                                                                    ?: recipients
                                                                                        .firstNotNullOfOrNull {
                                                                                            it.accountUuid
                                                                                        }?.let {
                                                                                            ownUnifiedAddress(
                                                                                                synchronizer,
                                                                                                it
                                                                                            )
                                                                                        }
                                                                            }
                                                                )
                                                            val key = transaction.txId.txIdString()
                                                            if (transaction.raw != null) {
                                                                enhancedTxCache[key] = details
                                                            }
                                                            key to details
                                                        }
                                                    }
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    Twig.error(e) { "Batched transaction enhancement failed" }
                                                    emptyMap()
                                                }
                                            } else {
                                                emptyMap()
                                            }

                                        transactions
                                            .map { transaction ->
                                                val key = transaction.txId.txIdString()
                                                val details =
                                                    freshDetails[key]
                                                        ?: enhancedTxCache[key]
                                                        ?: TxDetails(outputs = emptyList(), recipient = null)
                                                createTransaction(
                                                    transaction = transaction,
                                                    details = details,
                                                    isRejected = transaction.txId in rejected
                                                )
                                            }.sortedByDescending { transaction ->
                                                transaction.timestamp ?: now
                                            }
                                    }
                            }
                        }.onStart { emit(null) }
                }
            }.retryWhen { cause, attempt ->
                if (cause is CancellationException) {
                    false
                } else {
                    Twig.error(cause) { "Transactions flow failed; retrying" }
                    emit(null)
                    delay(attempt.coerceAtMost(TRANSACTIONS_RETRY_DELAY_CAP).seconds)
                    true
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Lazily,
                initialValue = null
            )

    internal fun createTransaction(
        transaction: TransactionOverview,
        details: TxDetails,
        isRejected: Boolean = false
    ): Transaction =
        when (transaction.transactionState) {
            Expired -> {
                when {
                    transaction.isShielding -> {
                        ShieldTransaction.Failed(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = transaction.totalSpent,
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            fee = transaction.netValue,
                            overview = transaction,
                            recipient = null
                        )
                    }

                    transaction.isSentTransaction -> {
                        SendTransaction.Failed(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = sentTransactionAmount(transaction),
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            fee = transaction.feePaid,
                            overview = transaction,
                            recipient = details.recipient,
                            isRejected = isRejected
                        )
                    }

                    else -> {
                        ReceiveTransaction.Failed(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = transaction.netValue,
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            overview = transaction,
                            recipient = null
                        )
                    }
                }
            }

            Confirmed -> {
                when {
                    transaction.isShielding -> {
                        ShieldTransaction.Success(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = transaction.totalSpent,
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            fee = transaction.netValue,
                            overview = transaction,
                            recipient = null
                        )
                    }

                    transaction.isSentTransaction -> {
                        SendTransaction.Success(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = sentTransactionAmount(transaction),
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            fee = transaction.feePaid,
                            overview = transaction,
                            recipient = details.recipient
                        )
                    }

                    else -> {
                        ReceiveTransaction.Success(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = transaction.netValue,
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            overview = transaction,
                            recipient = null
                        )
                    }
                }
            }

            Pending -> {
                when {
                    transaction.isShielding -> {
                        ShieldTransaction.Pending(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = transaction.totalSpent,
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            fee = transaction.netValue,
                            overview = transaction,
                            recipient = null
                        )
                    }

                    transaction.isSentTransaction -> {
                        SendTransaction.Pending(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = sentTransactionAmount(transaction),
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            fee = transaction.feePaid,
                            overview = transaction,
                            recipient = details.recipient
                        )
                    }

                    else -> {
                        ReceiveTransaction.Pending(
                            timestamp = createTimestamp(transaction),
                            transactionOutputs = details.outputs,
                            amount = transaction.netValue,
                            id = transaction.txId,
                            memoCount = transaction.memoCount,
                            overview = transaction,
                            recipient = null
                        )
                    }
                }
            }
        }

    // MOB-1577: minedHeight == null alone used to fall straight to `isSyncing -> Pending`, so an
    // Expired transaction flickered back to Pending on every sync cycle. transactionState carries
    // the terminal Expired classification through explicitly instead of re-deriving it here.
    internal fun createTransactionState(
        minedHeight: BlockHeight?,
        transactionState: TransactionState,
        isSyncing: Boolean
    ): TransactionState? =
        when {
            minedHeight != null -> Confirmed
            transactionState == Expired -> null
            isSyncing -> Pending
            else -> null
        }

    private fun createTimestamp(overview: TransactionOverview): Instant? =
        overview.blockTimeEpochSeconds?.let { Instant.ofEpochSecond(it) }

    override suspend fun getMemos(transaction: Transaction): List<String> =
        withContext(Dispatchers.IO) {
            synchronizerProvider
                .getSynchronizer()
                .getMemos(transaction.overview)
                .mapNotNull { memo -> memo.takeIf { it.isNotEmpty() } }
                .toList()
        }

    override fun observeTransaction(txId: String): Flow<Transaction?> =
        transactions
            .map { transactions ->
                transactions?.find { it.id.txIdString() == txId }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTransactionsByMemo(memo: String): Flow<List<TransactionId>?> =
        synchronizerProvider
            .synchronizer
            .flatMapLatest { synchronizer ->
                synchronizer?.getTransactionsByMemoSubstring(memo)?.onEmpty { emit(listOf()) } ?: flowOf(null)
            }.distinctUntilChanged()

    override suspend fun getTransactions(): List<Transaction> = transactions.filterNotNull().first()

    override suspend fun resolveWalletAddress(address: String): WalletAddress? =
        when (synchronizerProvider.getSynchronizer().validateAddress(address)) {
            AddressType.Shielded -> WalletAddress.Sapling.new(address)
            AddressType.Tex -> WalletAddress.Tex.new(address)
            AddressType.Transparent -> WalletAddress.Transparent.new(address)
            AddressType.Unified -> WalletAddress.Unified.new(address)
            else -> null
        }

    /**
     * Resolves the wallet's own unified address for [uuid] — the display recipient of a
     * wallet-internal transaction (e.g. an Orchard->Ironwood pool migration), whose outputs
     * carry only the receiving account and no stored address string in the wallet database.
     * Failures degrade to null (the detail screen renders the row empty), rethrowing
     * cancellation per the coroutine exception-handling convention.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun ownUnifiedAddress(
        synchronizer: Synchronizer,
        uuid: AccountUuid
    ): String? =
        ownAddressCache[uuid] ?: try {
            synchronizer
                .getAccounts()
                .firstOrNull { it.accountUuid == uuid }
                ?.let { synchronizer.getUnifiedAddress(it) }
                ?.also { ownAddressCache[uuid] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}

/**
 * A transaction the SDK recorded as rejected (see [Synchronizer.rejectedTransactionIds]: every endpoint it was
 * submitted to answered, none accepted it and one refused it for good) is shown as failed, not pending, from that
 * verdict rather than when its expiry height passes. A transaction that was mined anyway stays as it is.
 */
internal fun TransactionOverview.asRejectedIfIn(rejected: Set<TransactionId>): TransactionOverview =
    if (minedHeight == null && transactionState != Confirmed && txId in rejected) {
        copy(transactionState = Expired)
    } else {
        this
    }

sealed interface Transaction {
    val id: TransactionId
    val amount: Zatoshi
    val memoCount: Int
    val timestamp: Instant?
    val transactionOutputs: List<TransactionOutput>
    val overview: TransactionOverview
    val fee: Zatoshi?
    val recipient: String?

    /** Whether a server refused the transaction for good: it is shown as failed before its expiry height passes. */
    val isRejected: Boolean
        get() = false
}

sealed interface SendTransaction : Transaction {
    data class Success(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val fee: Zatoshi?,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : SendTransaction

    data class Pending(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val fee: Zatoshi?,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : SendTransaction

    data class Failed(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val fee: Zatoshi?,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
        override val isRejected: Boolean = false,
    ) : SendTransaction
}

sealed interface ReceiveTransaction : Transaction {
    override val fee: Zatoshi?
        get() = null

    data class Success(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : ReceiveTransaction

    data class Pending(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : ReceiveTransaction

    data class Failed(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : ReceiveTransaction
}

sealed interface ShieldTransaction : Transaction {
    data class Success(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val fee: Zatoshi?,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : ShieldTransaction

    data class Pending(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val timestamp: Instant?,
        override val memoCount: Int,
        override val fee: Zatoshi?,
        override val transactionOutputs: List<TransactionOutput>,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : ShieldTransaction

    data class Failed(
        override val id: TransactionId,
        override val amount: Zatoshi,
        override val memoCount: Int,
        override val timestamp: Instant?,
        override val transactionOutputs: List<TransactionOutput>,
        override val fee: Zatoshi?,
        override val overview: TransactionOverview,
        override val recipient: String?,
    ) : ShieldTransaction
}

val Transaction.isPending: Boolean
    get() = this is SendTransaction.Pending || this is ShieldTransaction.Pending || this is ReceiveTransaction.Pending

internal data class TxDetails(
    val outputs: List<TransactionOutput>,
    val recipient: String?,
)

/**
 * For an ordinary external send, [TransactionOverview.netValue] correctly nets out change
 * returned to this account. Three same-account cases collapse it down to just the fee instead of
 * the real value, and all need [TransactionOverview.totalReceived] instead:
 * - A *mined* cross-pool transfer (e.g. a pool migration), where the scanner's change-detection
 *   heuristic misclassifies the crossing output as change ([TransactionOverview.sentNoteCount]
 *   and [TransactionOverview.receivedNoteCount] both end up 0).
 * - A *pending* (not-yet-broadcast) migration transaction read from `v_transactions_with_pending_migrations`,
 *   which has a different shape: `sentNoteCount == 0` but `receivedNoteCount >= 1`. Requires
 *   [TransactionOverview.raw] `== null` AND [TransactionOverview.minedHeight] `== null` to
 *   distinguish from ordinary sends: [Zip318Kind.TRANSFER] is deliberately built in canonical
 *   shape to blend into the migration anonymity set ("Nothing observable on chain separates
 *   such a payment from a wallet's own transfer"), so shape alone is insufficient. An ordinary
 *   send to a third party can also have [Zip318Kind.TRANSFER], and those must show [netValue]
 *   (nothing comes back). The `raw == null && minedHeight == null` gates ensure we only apply
 *   [totalReceived] to genuinely not-yet-broadcast rows (for which the migration schema provides
 *   the crossing amount). A mined transaction can have `raw == null` indefinitely if unenhanced
 *   after a restore, so [minedHeight] != null is the terminal indicator of confirmation.
 */
internal fun sentTransactionAmount(transaction: TransactionOverview): Zatoshi =
    if ((transaction.sentNoteCount == 0 && transaction.receivedNoteCount == 0) ||
        (
            transaction.raw == null && transaction.minedHeight == null &&
                (transaction.zip318Kind == Zip318Kind.PREPARATION || transaction.zip318Kind == Zip318Kind.TRANSFER)
        )
    ) {
        transaction.totalReceived
    } else {
        transaction.netValue
    }

/**
 * Chooses the address to display as a transaction's recipient from its [recipients]. An external
 * row ([TransactionRecipient.accountUuid] `== null`) is preferred regardless of list order; if
 * only wallet-internal rows are present, the first one's stored [TransactionRecipient.addressValue]
 * is used (this is what surfaces a self-transfer, e.g. a pool migration). Returns `null` when
 * [recipients] is empty, or when no entry has a stored address.
 */
internal fun selectDisplayRecipient(recipients: List<TransactionRecipient>): String? =
    recipients.sortedBy { it.accountUuid != null }.firstNotNullOfOrNull { it.addressValue }

private const val TRANSACTIONS_RETRY_DELAY_CAP = 10L
