package co.electriccoin.zcash.ui.screen.transactionhistory.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.mapper.ActivityMapper
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.repository.Transaction
import co.electriccoin.zcash.ui.common.usecase.ActivityData
import co.electriccoin.zcash.ui.common.usecase.GetActivitiesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletRestoringStateUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRequestShieldedUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateSwapActivityMetadataUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.swap.detail.SwapDetailArgs
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailArgs
import co.electriccoin.zcash.ui.screen.transactionhistory.ActivityHistoryArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityWidgetVM(
    getActivities: GetActivitiesUseCase,
    getWalletRestoringState: GetWalletRestoringStateUseCase,
    private val activityMapper: ActivityMapper,
    private val navigationRouter: NavigationRouter,
    private val restoreTimestampDataSource: RestoreTimestampDataSource,
    private val navigateToRequestShielded: NavigateToRequestShieldedUseCase,
    private val updateSwapActivityMetadata: UpdateSwapActivityMetadataUseCase
) : ViewModel() {
    val state =
        combine(
            // updateSwapActivityMetadata.uiPipeline.onStart { emit(Unit) },
            getActivities.observe(),
            getWalletRestoringState.observe(),
        ) { activities, restoringState ->
            when {
                activities == null -> {
                    ActivityWidgetState.Loading
                }

                activities.isEmpty() -> {
                    ActivityWidgetState.Empty(
                        subtitle =
                            stringRes(R.string.transaction_history_widget_empty_subtitle)
                                .takeIf { restoringState != WalletRestoringState.RESTORING },
                        sendTransaction =
                            ButtonState(
                                text = stringRes(R.string.transaction_history_send_transaction),
                                onClick = ::onRequestZecClick
                            ).takeIf { restoringState != WalletRestoringState.RESTORING },
                        enableShimmer = restoringState == WalletRestoringState.RESTORING
                    )
                }

                else -> {
                    ActivityWidgetState.Data(
                        header =
                            TransactionHistoryWidgetHeaderState(
                                title = stringRes(R.string.general_activity),
                                button =
                                    ButtonState(
                                        text = stringRes(R.string.transactionHistory_seeAll),
                                        onClick = ::onSeeAllTransactionsClick
                                    ).takeIf {
                                        activities.size > MAX_TRANSACTION_COUNT
                                    }
                            ),
                        transactions =
                            activities
                                .take(MAX_TRANSACTION_COUNT)
                                .map { transaction ->
                                    activityMapper.createTransactionState(
                                        data = transaction,
                                        restoreTimestamp = restoreTimestampDataSource.getOrCreate(),
                                        onTransactionClick = ::onTransactionClick,
                                        onSwapClick = ::onSwapClick,
                                        onDisplayed = ::onActivityDisplayed
                                    )
                                }
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = ActivityWidgetState.Loading
        )

    private fun onActivityDisplayed(activity: ActivityData) = updateSwapActivityMetadata(activity)

    private fun onSwapClick(depositAddress: String) = navigationRouter.forward(SwapDetailArgs(depositAddress))

    private fun onTransactionClick(transaction: Transaction) {
        navigationRouter.forward(TransactionDetailArgs(transaction.id.txIdString()))
    }

    private fun onSeeAllTransactionsClick() {
        navigationRouter.forward(ActivityHistoryArgs)
    }

    private fun onRequestZecClick() = viewModelScope.launch { navigateToRequestShielded() }
}

private const val MAX_TRANSACTION_COUNT = 5
