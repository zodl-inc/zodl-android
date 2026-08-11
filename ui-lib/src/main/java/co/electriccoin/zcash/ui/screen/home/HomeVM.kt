package co.electriccoin.zcash.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.migration.MigrationHomeMessageSource
import co.electriccoin.zcash.ui.common.provider.ShieldFundsInfoProvider
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessage
import co.electriccoin.zcash.ui.common.usecase.GetHomeMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.IsRestoreSuccessDialogVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToNearPayUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToReceiveUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSendUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsFromMessageUseCase
import co.electriccoin.zcash.ui.common.voting.VotingHomeHooks
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.exchangerate.optin.ExchangeRateOptInArgs
import co.electriccoin.zcash.ui.screen.home.backup.SeedBackupInfo
import co.electriccoin.zcash.ui.screen.home.backup.WalletBackupDetail
import co.electriccoin.zcash.ui.screen.home.backup.WalletBackupMessageState
import co.electriccoin.zcash.ui.screen.home.currency.EnableCurrencyConversionMessageState
import co.electriccoin.zcash.ui.screen.home.disconnected.WalletDisconnectedInfo
import co.electriccoin.zcash.ui.screen.home.disconnected.WalletDisconnectedMessageState
import co.electriccoin.zcash.ui.screen.home.error.WalletErrorMessageState
import co.electriccoin.zcash.ui.screen.home.reporting.CrashReportMessageState
import co.electriccoin.zcash.ui.screen.home.reporting.CrashReportOptIn
import co.electriccoin.zcash.ui.screen.home.restoring.WalletRestoringInfo
import co.electriccoin.zcash.ui.screen.home.restoring.WalletRestoringMessageState
import co.electriccoin.zcash.ui.screen.home.resyncing.WalletResyncingInfo
import co.electriccoin.zcash.ui.screen.home.resyncing.WalletResyncingMessageState
import co.electriccoin.zcash.ui.screen.home.syncing.WalletSyncingInfo
import co.electriccoin.zcash.ui.screen.home.syncing.WalletSyncingMessageState
import co.electriccoin.zcash.ui.screen.home.tor.EnableTorMessageState
import co.electriccoin.zcash.ui.screen.home.updating.WalletUpdatingInfo
import co.electriccoin.zcash.ui.screen.home.updating.WalletUpdatingMessageState
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow
import co.electriccoin.zcash.ui.screen.tor.optin.TorOptInArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
class HomeVM(
    getHomeMessage: GetHomeMessageUseCase,
    shieldFundsInfoProvider: ShieldFundsInfoProvider,
    isRestoreSuccessDialogVisible: IsRestoreSuccessDialogVisibleUseCase,
    private val navigationRouter: NavigationRouter,
    private val shieldFundsFromMessage: ShieldFundsFromMessageUseCase,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigateToReceive: NavigateToReceiveUseCase,
    private val navigateToSend: NavigateToSendUseCase,
    private val navigateToNearPay: NavigateToNearPayUseCase,
    private val navigateToSwap: NavigateToSwapUseCase,
    private val votingHomeHooks: VotingHomeHooks,
    private val migrationHomeMessageSource: MigrationHomeMessageSource,
    private val homeMessageMapper: HomeMessageMapper,
) : ViewModel() {
    private var hasSyncErrorBeenShown = false
    private var hasRestoreSuccessBeenShown = false
    private var hasAttemptedPendingVotingRouteRecovery = false
    private var hasRecoveredPendingVotingRoute = false
    private var hasResumedShareTracking = false

    // NOTE: no checkMigrationRecovery() here. HomeVM is lazily created on Home's FIRST
    // composition — with replaceAll(Home, Progress) that moment is exactly when the user backs
    // out of the Progress screen, so an init-time recovery check re-redirected them straight
    // back (visible as "back closes Progress and it immediately reopens", plus stacked Home
    // entries). MainActivity.onStart and RootNavGraph's unlock redirect cover the real cases.

    private val messageData =
        getHomeMessage
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private val messageState =
        combine(
            messageData,
            shieldFundsInfoProvider.observe(),
        ) { message, isShieldFundsInfoEnabled ->
            createMessageState(message, isShieldFundsInfoEnabled)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0, 0),
            initialValue = null
        )

    private val isRestoreDialogVisible: Flow<Boolean?> =
        isRestoreSuccessDialogVisible
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    val state: StateFlow<HomeState?> =
        messageState
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    val uiLifecyclePipeline =
        combine(
            messageData,
            isRestoreDialogVisible
        ) { message, isRestoreVisible ->
            if (!hasAttemptedPendingVotingRouteRecovery) {
                hasAttemptedPendingVotingRouteRecovery = true
                hasRecoveredPendingVotingRoute = votingHomeHooks.recoverPendingRouteIfNeeded()
            }

            if (!hasResumedShareTracking) {
                hasResumedShareTracking = true
                votingHomeHooks.resumePendingShareTracking()
            }

            hasSyncErrorBeenShown =
                if (message is HomeMessageData.Error) {
                    if (!hasSyncErrorBeenShown) navigateToError.navigateToSyncError(message) else false
                } else {
                    false
                }

            if (!hasRecoveredPendingVotingRoute && isRestoreVisible == true && !hasRestoreSuccessBeenShown) {
                hasRestoreSuccessBeenShown = true
                navigationRouter.forward(KeepOpenArgs(KeepOpenFlow.RESTORE))
            }
        }.map { }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(1.seconds, Duration.ZERO),
                initialValue = Unit
            )

    private var onPayButtonClickJob: Job? = null

    private var onSwapButtonClick: Job? = null

    private fun createState(messageState: HomeMessageState?) =
        HomeState(
            firstButton =
                BigIconButtonState(
                    text = stringRes(R.string.tabs_receive),
                    icon = R.drawable.ic_home_receive,
                    onClick = ::onReceiveButtonClick,
                ),
            secondButton =
                BigIconButtonState(
                    text = stringRes(R.string.tabs_send),
                    icon = R.drawable.ic_home_send,
                    onClick = ::onSendButtonClick,
                ),
            thirdButton =
                BigIconButtonState(
                    text = stringRes(R.string.crosspay_pay),
                    icon = R.drawable.ic_home_pay,
                    onClick = ::onPayButtonClick,
                ),
            fourthButton =
                BigIconButtonState(
                    text = stringRes(R.string.swapAndPay_swap),
                    icon = R.drawable.ic_home_swap,
                    onClick = ::onSwapButtonClick,
                ),
            message = messageState
        )

    private fun createMessageState(data: HomeMessageData?, isShieldFundsInfoEnabled: Boolean) =
        when (data) {
            is HomeMessageData.Backup -> {
                WalletBackupMessageState(
                    onClick = ::onWalletBackupMessageClick,
                    onButtonClick = ::onWalletBackupMessageButtonClick,
                )
            }

            HomeMessageData.Disconnected -> {
                WalletDisconnectedMessageState(
                    onClick = ::onWalletDisconnectedMessageClick
                )
            }

            HomeMessageData.EnableCurrencyConversion -> {
                EnableCurrencyConversionMessageState(
                    onClick = ::onEnableCurrencyConversionClick,
                    onButtonClick = ::onEnableCurrencyConversionClick
                )
            }

            HomeMessageData.EnableTor -> {
                EnableTorMessageState(
                    onClick = ::onEnableTorClick,
                    onButtonClick = ::onEnableTorClick
                )
            }

            is HomeMessageData.Error -> {
                WalletErrorMessageState(
                    onClick = { onWalletErrorMessageClick(data) }
                )
            }

            is HomeMessageData.Resyncing -> {
                WalletResyncingMessageState(
                    onClick = ::onWalletResyncingMessageClick,
                )
            }

            is HomeMessageData.Restoring -> {
                WalletRestoringMessageState(
                    isSpendable = data.isSpendable,
                    progress = data.progress,
                    onClick = ::onWalletRestoringMessageClick
                )
            }

            is HomeMessageData.Syncing -> {
                WalletSyncingMessageState(
                    progress = data.progress,
                    onClick = ::onWalletSyncingMessageClick
                )
            }

            is HomeMessageData.ShieldFunds -> {
                homeMessageMapper.createState(
                    data = data,
                    isShieldFundsInfoEnabled = isShieldFundsInfoEnabled,
                    onClick = ::onShieldFundsMessageClick,
                    onButtonClick = ::onShieldFundsMessageButtonClick,
                )
            }

            HomeMessageData.Updating -> {
                WalletUpdatingMessageState(
                    onClick = ::onWalletUpdatingMessageClick
                )
            }

            HomeMessageData.CrashReport -> {
                CrashReportMessageState(
                    onClick = ::onCrashReportMessageClick,
                    onButtonClick = ::onCrashReportMessageClick
                )
            }

            is MigrationHomeMessage -> {
                migrationHomeMessageSource.createMessageState(data)
            }

            null -> {
                null
            }
        }

    private fun onCrashReportMessageClick() = navigationRouter.forward(CrashReportOptIn)

    private fun onSwapButtonClick() {
        if (onSwapButtonClick?.isActive == true) return
        onSwapButtonClick = viewModelScope.launch { navigateToSwap() }
    }

    private fun onSendButtonClick() = navigateToSend()

    private fun onReceiveButtonClick() = viewModelScope.launch { navigateToReceive() }

    private fun onPayButtonClick() {
        if (onPayButtonClickJob?.isActive == true) return
        onPayButtonClickJob = viewModelScope.launch { navigateToNearPay() }
    }

    private fun onWalletUpdatingMessageClick() = navigationRouter.forward(WalletUpdatingInfo)

    private fun onWalletSyncingMessageClick() = navigationRouter.forward(WalletSyncingInfo)

    private fun onWalletRestoringMessageClick() = navigationRouter.forward(WalletRestoringInfo)

    private fun onWalletResyncingMessageClick() = navigationRouter.forward(WalletResyncingInfo)

    private fun onEnableTorClick() = navigationRouter.forward(TorOptInArgs)

    private fun onEnableCurrencyConversionClick() = navigationRouter.forward(ExchangeRateOptInArgs)

    private fun onWalletDisconnectedMessageClick() =
        navigationRouter.forward(WalletDisconnectedInfo)

    private fun onWalletBackupMessageClick() = navigationRouter.forward(SeedBackupInfo)

    private fun onWalletBackupMessageButtonClick() =
        navigationRouter.forward(WalletBackupDetail(false))

    private fun onShieldFundsMessageClick() = viewModelScope.launch { shieldFundsFromMessage() }

    private fun onShieldFundsMessageButtonClick() =
        viewModelScope.launch { shieldFundsFromMessage() }

    private fun onWalletErrorMessageClick(homeMessageData: HomeMessageData.Error) =
        navigateToError(ErrorArgs.SyncError(homeMessageData.synchronizerError))
}
