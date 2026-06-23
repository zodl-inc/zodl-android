package co.electriccoin.zcash.ui.screen.transactionfilters.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.TransactionFilter
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.BOOKMARKED
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.MEMOS
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.NOTES
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.RECEIVED
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.SENT
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.SWAP
import co.electriccoin.zcash.ui.common.repository.TransactionFilter.UNREAD
import co.electriccoin.zcash.ui.common.usecase.ApplyTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionFiltersUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactionfilters.TransactionFilterState
import co.electriccoin.zcash.ui.screen.transactionfilters.TransactionFiltersState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class TransactionFiltersVM(
    private val navigationRouter: NavigationRouter,
    getTransactionFilters: GetTransactionFiltersUseCase,
    private val applyTransactionFilters: ApplyTransactionFiltersUseCase,
) : ViewModel() {
    private val selectedFilters = MutableStateFlow(getTransactionFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TransactionFiltersState?> =
        selectedFilters
            .mapLatest { current ->
                TransactionFiltersState(
                    filters =
                        TransactionFilter.entries.map {
                            when (it) {
                                SENT -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.filter_sent),
                                        isSelected = current.contains(SENT),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }

                                RECEIVED -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.filter_received),
                                        isSelected = current.contains(RECEIVED),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }

                                MEMOS -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.filter_memos),
                                        isSelected = current.contains(MEMOS),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }

                                UNREAD -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.transaction_filters_unread),
                                        isSelected = current.contains(UNREAD),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }

                                BOOKMARKED -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.filter_bookmarked),
                                        isSelected = current.contains(BOOKMARKED),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }

                                NOTES -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.filter_notes),
                                        isSelected = current.contains(NOTES),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }

                                SWAP -> {
                                    TransactionFilterState(
                                        text = stringRes(R.string.swapAndPay_swap),
                                        isSelected = current.contains(SWAP),
                                        onClick = { onTransactionFilterClicked(it) }
                                    )
                                }
                            }
                        },
                    onBack = ::onBack,
                    primaryButton =
                        ButtonState(
                            text = stringRes(R.string.filter_apply),
                            onClick = ::onApplyTransactionFiltersClick,
                        ),
                    secondaryButton =
                        ButtonState(
                            text = stringRes(R.string.filter_reset),
                            onClick = ::onResetTransactionFiltersClick,
                        ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun onTransactionFilterClicked(filter: TransactionFilter) =
        selectedFilters.update { if (it.contains(filter)) it - filter else it + filter }

    private fun onBack() = navigationRouter.back()

    private fun onApplyTransactionFiltersClick() = applyTransactionFilters(selectedFilters.value)

    private fun onResetTransactionFiltersClick() = selectedFilters.update { emptyList() }
}
