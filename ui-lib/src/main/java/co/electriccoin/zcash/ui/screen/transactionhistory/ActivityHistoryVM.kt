package co.electriccoin.zcash.ui.screen.transactionhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.mapper.ActivityMapper
import co.electriccoin.zcash.ui.common.repository.Transaction
import co.electriccoin.zcash.ui.common.repository.TransactionFilter
import co.electriccoin.zcash.ui.common.repository.TransactionFilterRepository
import co.electriccoin.zcash.ui.common.usecase.ActivityData
import co.electriccoin.zcash.ui.common.usecase.ApplyTransactionFulltextFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.GetFilteredActivitiesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.ResetTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateSwapActivityMetadataUseCase
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.swap.detail.SwapDetailArgs
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailArgs
import co.electriccoin.zcash.ui.screen.transactionfilters.TransactionFiltersArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Objects

class ActivityHistoryVM(
    getFilteredActivities: GetFilteredActivitiesUseCase,
    getTransactionFilters: GetTransactionFiltersUseCase,
    transactionFilterRepository: TransactionFilterRepository,
    private val applyTransactionFulltextFilters: ApplyTransactionFulltextFiltersUseCase,
    private val activityMapper: ActivityMapper,
    private val navigationRouter: NavigationRouter,
    private val resetTransactionFilters: ResetTransactionFiltersUseCase,
    private val restoreTimestampDataSource: RestoreTimestampDataSource,
    private val updateSwapActivityMetadata: UpdateSwapActivityMetadataUseCase
) : ViewModel() {
    val search =
        transactionFilterRepository.fulltextFilter
            .map {
                TextFieldState(stringRes(it.orEmpty()), onValueChange = ::onFulltextFilterChanged)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue =
                    TextFieldState(
                        stringRes(
                            transactionFilterRepository.fulltextFilter.value.orEmpty()
                        ),
                        onValueChange = ::onFulltextFilterChanged
                    )
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredActivities =
        getFilteredActivities
            .observe()
            .mapLatest { activities ->
                val searchHash =
                    Objects
                        .hash(
                            getTransactionFilters.observe().value,
                            transactionFilterRepository.fulltextFilter.value
                        ).toString()

                activities to searchHash
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state =
        combine(
            // updateSwapActivityMetadata.uiPipeline.onStart { emit(Unit) },
            filteredActivities,
            getTransactionFilters.observe(),
        ) { (activities, searchHash), filters ->
            Triple(activities, filters, searchHash)
        }.mapLatest { (activities, filters, searchHash) ->
            when {
                activities == null -> {
                    createLoadingState(filtersSize = filters.size)
                }

                activities.isEmpty() -> {
                    createEmptyState(filtersSize = filters.size)
                }

                else -> {
                    createDataState(
                        transactions = activities,
                        restoreTimestamp = restoreTimestampDataSource.getOrCreate(),
                        filters = filters,
                        searchHash = searchHash
                    )
                }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createLoadingState(filtersSize = 0)
            )

    override fun onCleared() {
        resetTransactionFilters()
        super.onCleared()
    }

    private fun createDataState(
        transactions: List<ActivityData>,
        restoreTimestamp: Instant,
        filters: List<TransactionFilter>,
        searchHash: String
    ): ActivityHistoryState.Data {
        val now = ZonedDateTime.now().toLocalDate()
        val items =
            transactions
                .groupBy {
                    val other =
                        it.timestamp
                            ?.atZone(ZoneId.systemDefault())
                            ?.toLocalDate() ?: now
                    when {
                        now == other -> {
                            stringRes(R.string.filter_today) to "today"
                        }

                        other == now.minusDays(1) -> {
                            stringRes(R.string.filter_yesterday) to "yesterday"
                        }

                        other >= now.minusDays(WEEK_THRESHOLD) -> {
                            stringRes(R.string.filter_previous7days) to "previous_7_days"
                        }

                        other >= now.minusDays(MONTH_THRESHOLD) -> {
                            stringRes(R.string.filter_previous30days) to "previous_30_days"
                        }

                        else -> {
                            val yearMonth = YearMonth.from(other)
                            stringRes(yearMonth) to yearMonth.toString()
                        }
                    }
                }.map { (entry, transactions) ->
                    val (headerStringRes, headerId) = entry
                    listOf(
                        ActivityHistoryItem.Header(
                            title = headerStringRes,
                            key = headerId,
                        ),
                    ) +
                        transactions.map { activity ->
                            ActivityHistoryItem.Activity(
                                state =
                                    activityMapper.createTransactionState(
                                        data = activity,
                                        restoreTimestamp = restoreTimestamp,
                                        onTransactionClick = ::onTransactionClick,
                                        onSwapClick = ::onSwapClick,
                                        onDisplayed = ::onActivityDisplayed
                                    )
                            )
                        }
                }.flatten()

        return ActivityHistoryState.Data(
            onBack = ::onBack,
            items = items,
            filterButton =
                IconButtonState(
                    icon =
                        if (filters.isEmpty()) {
                            R.drawable.ic_transaction_filters
                        } else {
                            R.drawable.ic_transactions_filters_selected
                        },
                    badge = stringRes(filters.size.toString()).takeIf { filters.isNotEmpty() },
                    onClick = ::onTransactionFiltersClicked,
                ),
            filtersId = searchHash
        )
    }

    private fun onActivityDisplayed(activity: ActivityData) = updateSwapActivityMetadata(activity)

    private fun onFulltextFilterChanged(value: String) {
        applyTransactionFulltextFilters(value)
    }

    private fun createLoadingState(filtersSize: Int) =
        ActivityHistoryState.Loading(
            onBack = ::onBack,
            filterButton =
                IconButtonState(
                    icon =
                        if (filtersSize <= 0) {
                            R.drawable.ic_transaction_filters
                        } else {
                            R.drawable.ic_transactions_filters_selected
                        },
                    badge = stringRes(filtersSize.toString()).takeIf { filtersSize > 0 },
                    onClick = ::onTransactionFiltersClicked,
                    contentDescription = null
                ),
        )

    private fun createEmptyState(filtersSize: Int) =
        ActivityHistoryState.Empty(
            onBack = ::onBack,
            filterButton =
                IconButtonState(
                    icon =
                        if (filtersSize <= 0) {
                            R.drawable.ic_transaction_filters
                        } else {
                            R.drawable.ic_transactions_filters_selected
                        },
                    badge = stringRes(filtersSize.toString()).takeIf { filtersSize > 0 },
                    onClick = ::onTransactionFiltersClicked,
                    contentDescription = null
                ),
        )

    private fun onBack() {
        navigationRouter.back()
    }

    private fun onTransactionClick(transaction: Transaction) {
        navigationRouter.forward(TransactionDetailArgs(transaction.id.txIdString()))
    }

    private fun onSwapClick(depositAddress: String) = navigationRouter.forward(SwapDetailArgs(depositAddress))

    private fun onTransactionFiltersClicked() = navigationRouter.forward(TransactionFiltersArgs)
}

private const val WEEK_THRESHOLD = 7L
private const val MONTH_THRESHOLD = 30L
