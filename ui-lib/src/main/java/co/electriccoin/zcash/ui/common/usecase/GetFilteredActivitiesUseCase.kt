package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.model.TransactionId
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.model.ZecSimpleSwapAsset
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.ReceiveTransaction
import co.electriccoin.zcash.ui.common.repository.SendTransaction
import co.electriccoin.zcash.ui.common.repository.TransactionFilter
import co.electriccoin.zcash.ui.common.repository.TransactionFilterRepository
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.combineToFlow
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Suppress("TooManyFunctions")
class GetFilteredActivitiesUseCase(
    getActivities: GetActivitiesUseCase,
    transactionFilterRepository: TransactionFilterRepository,
    private val transactionRepository: TransactionRepository,
    private val restoreTimestampDataSource: RestoreTimestampDataSource,
    private val addressBookRepository: AddressBookRepository,
    private val context: Context,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val detailedActivities =
        getActivities
            .observe()
            .flatMapLatest { activities ->
                val enhancedTransactions =
                    activities
                        ?.map { activity ->
                            when (activity) {
                                is ActivityData.BySwap -> {
                                    flowOf(InternalState.BySwap(activity))
                                }

                                is ActivityData.ByTransaction -> {
                                    val recipient = activity.transaction.recipient

                                    if (recipient == null) {
                                        flowOf(InternalState.ByTransaction(activity, null))
                                    } else {
                                        addressBookRepository
                                            .observeContactByAddress(recipient)
                                            .map { contact ->
                                                InternalState.ByTransaction(activity, contact)
                                            }
                                    }
                                }
                            }
                        }

                enhancedTransactions?.combineToFlow() ?: flowOf(null)
            }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val transactionsFilteredByFulltext: Flow<List<InternalState>?> =
        channelFlow {
            val detailedActivities = this@GetFilteredActivitiesUseCase.detailedActivities.stateIn(this)

            launch {
                transactionFilterRepository
                    .fulltextFilter
                    .map { it?.trim() }
                    .distinctUntilChanged()
                    .flatMapLatest { fulltextFilter ->
                        flow {
                            emitAll(
                                if (fulltextFilter == null || fulltextFilter.length < MIN_TEXT_FILTER_LENGTH) {
                                    detailedActivities
                                } else {
                                    combine(
                                        detailedActivities,
                                        transactionRepository.observeTransactionsByMemo(fulltextFilter)
                                    ) { transactions, memoTxIds ->
                                        transactions to memoTxIds
                                    }.mapLatest { (activities, memoTxIds) ->
                                        activities
                                            ?.filter { activity ->
                                                hasMemoInFilteredIds(memoTxIds, activity) ||
                                                    hasContactInAddressBookWithFulltext(activity, fulltextFilter) ||
                                                    hasAddressWithFulltext(activity, fulltextFilter) ||
                                                    hasNotesWithFulltext(activity, fulltextFilter) ||
                                                    hasAmountWithFulltext(activity, fulltextFilter)
                                            }
                                    }
                                }
                            )
                        }
                    }.distinctUntilChanged()
                    .collect { send(it) }
            }

            awaitClose {
                // do nothing
            }
        }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val result: Flow<List<ActivityData>?> =
        channelFlow {
            val transactionsFilteredByFulltext = transactionsFilteredByFulltext.stateIn(this)
            launch {
                transactionFilterRepository.filters
                    .flatMapLatest { filters ->
                        flow {
                            emitAll(
                                transactionsFilteredByFulltext
                                    .mapLatest { transactions ->
                                        transactions
                                            ?.filter { transaction ->
                                                filterBySentReceivedSwap(filters, transaction)
                                            }?.filter { transaction ->
                                                filterByGeneralFilters(
                                                    filters = filters,
                                                    activity = transaction,
                                                    restoreTimestamp = restoreTimestampDataSource.getOrCreate()
                                                )
                                            }?.map { transaction ->
                                                transaction.activity
                                            }
                                    }
                            )
                        }
                    }.collect {
                        send(it)
                    }
            }
            awaitClose {
                // do nothing
            }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observe(): Flow<List<ActivityData>?> = result

    private fun filterByGeneralFilters(
        filters: List<TransactionFilter>,
        activity: InternalState,
        restoreTimestamp: Instant,
    ): Boolean {
        val memoPass =
            if (filters.contains(TransactionFilter.MEMOS)) {
                when (activity) {
                    is InternalState.BySwap -> false
                    is InternalState.ByTransaction -> activity.activity.transaction.memoCount > 0
                }
            } else {
                true
            }
        val unreadPass = if (filters.contains(TransactionFilter.UNREAD)) isUnread(activity, restoreTimestamp) else true
        val bookmarkPass = if (filters.contains(TransactionFilter.BOOKMARKED)) isBookmark(activity) else true
        val notesPass = if (filters.contains(TransactionFilter.NOTES)) hasNotes(activity) else true
        return memoPass && unreadPass && bookmarkPass && notesPass
    }

    @Suppress("CyclomaticComplexMethod")
    private fun filterBySentReceivedSwap(
        filters: List<TransactionFilter>,
        activity: InternalState
    ): Boolean =
        if (TransactionFilter.SENT in filters ||
            TransactionFilter.RECEIVED in filters ||
            TransactionFilter.SWAP in filters
        ) {
            when {
                TransactionFilter.SENT in filters &&
                    when (activity) {
                        is InternalState.BySwap -> false
                        is InternalState.ByTransaction -> activity.activity.transaction is SendTransaction
                    } -> true

                TransactionFilter.RECEIVED in filters &&
                    when (activity) {
                        is InternalState.BySwap -> false
                        is InternalState.ByTransaction -> activity.activity.transaction is ReceiveTransaction
                    } -> true

                TransactionFilter.SWAP in filters &&
                    when (activity) {
                        is InternalState.BySwap -> true
                        is InternalState.ByTransaction -> activity.activity.metadata.swapMetadata != null
                    } -> true

                else -> false
            }
        } else {
            true
        }

    private fun isUnread(activity: InternalState, restoreTimestamp: Instant): Boolean {
        if (activity !is InternalState.ByTransaction) return false

        val transactionDate =
            activity.activity.transaction.timestamp
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()
                ?: LocalDate.now()

        val hasMemo = activity.activity.transaction.memoCount > 0
        val restoreDate = restoreTimestamp.atZone(ZoneId.systemDefault()).toLocalDate()

        return if (hasMemo && transactionDate < restoreDate) {
            false
        } else {
            val transactionMetadata = activity.activity.metadata

            hasMemo && transactionMetadata.isRead.not()
        }
    }

    private fun isBookmark(activity: InternalState): Boolean =
        activity is InternalState.ByTransaction && activity.activity.metadata.isBookmarked

    private fun hasNotes(activity: InternalState): Boolean =
        activity is InternalState.ByTransaction && activity.activity.metadata.note != null

    private fun hasNotesWithFulltext(
        activity: InternalState,
        fulltextFilter: String
    ): Boolean {
        if (activity !is InternalState.ByTransaction) return false

        return activity.activity.metadata.note
            ?.contains(
                fulltextFilter,
                ignoreCase = true
            )
            ?: false
    }

    private fun hasAmountWithFulltext(activity: InternalState, fulltextFilter: String): Boolean =
        when (activity) {
            is InternalState.BySwap -> {
                val text =
                    (stringResByNumber(activity.activity.swap.totalFeesUsd) + stringRes(" ZEC"))
                        .getString(context)
                text.contains(fulltextFilter, ignoreCase = true)
            }

            is InternalState.ByTransaction -> {
                val text =
                    if (activity.activity.transaction is SendTransaction &&
                        (
                            activity.activity.metadata.swapMetadata == null ||
                                activity.activity.metadata.swapMetadata
                                    .destination !is ZecSimpleSwapAsset
                        )
                    ) {
                        (
                            stringRes("- ") +
                                stringRes(activity.activity.transaction.amount, HIDDEN) + stringRes(" ZEC")
                        ).getString(context)
                    } else {
                        (stringRes(activity.activity.transaction.amount, HIDDEN) + stringRes(" ZEC"))
                            .getString(context)
                    }
                text.contains(fulltextFilter, ignoreCase = true)
            }
        }

    private fun hasAddressWithFulltext(transaction: InternalState, fulltextFilter: String): Boolean =
        when (transaction) {
            is InternalState.BySwap -> {
                transaction.activity.swap.depositAddress
                    .contains(fulltextFilter, true)
            }

            is InternalState.ByTransaction -> {
                transaction.activity.transaction.recipient
                    ?.contains(fulltextFilter, ignoreCase = true) ?: false
            }
        }

    private fun hasContactInAddressBookWithFulltext(activity: InternalState, fulltextFilter: String): Boolean {
        if (activity !is InternalState.ByTransaction) return false
        return activity.contact?.name?.contains(fulltextFilter, ignoreCase = true) ?: false
    }

    private fun hasMemoInFilteredIds(memoTxIds: List<TransactionId>?, activity: InternalState): Boolean {
        if (activity !is InternalState.ByTransaction) return false
        return memoTxIds?.contains(activity.activity.transaction.id) ?: false
    }
}

private sealed interface InternalState {
    val activity: ActivityData

    data class ByTransaction(
        override val activity: ActivityData.ByTransaction,
        val contact: EnhancedABContact?,
    ) : InternalState

    data class BySwap(
        override val activity: ActivityData.BySwap
    ) : InternalState
}

const val MIN_TEXT_FILTER_LENGTH = 3
