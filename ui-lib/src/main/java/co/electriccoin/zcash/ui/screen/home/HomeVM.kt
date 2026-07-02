package co.electriccoin.zcash.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.provider.ShieldFundsInfoProvider
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.CheckMigrationRecoveryUseCase
import co.electriccoin.zcash.ui.common.usecase.GetHomeMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsRestoreSuccessDialogVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToNearPayUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToReceiveUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSendUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsFromMessageUseCase
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.exchangerate.optin.ExchangeRateOptInArgs
import co.electriccoin.zcash.ui.screen.home.backup.SeedBackupInfo
import co.electriccoin.zcash.ui.screen.home.migration.MigrationBannerPhase
import co.electriccoin.zcash.ui.screen.home.migration.MigrationMessageState
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupArgs
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
import co.electriccoin.zcash.ui.screen.home.shieldfunds.ShieldFundsMessageState
import co.electriccoin.zcash.ui.screen.home.syncing.WalletSyncingInfo
import co.electriccoin.zcash.ui.screen.home.syncing.WalletSyncingMessageState
import co.electriccoin.zcash.ui.screen.home.tor.EnableTorMessageState
import co.electriccoin.zcash.ui.screen.home.updating.WalletUpdatingInfo
import co.electriccoin.zcash.ui.screen.home.updating.WalletUpdatingMessageState
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow
import co.electriccoin.zcash.ui.screen.tor.optin.TorOptInArgs
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingArgs
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
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
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val refreshActiveVotingSession: RefreshActiveVotingSessionUseCase,
    private val votingShareTrackingScheduler: VotingShareTrackingScheduler,
    private val checkMigrationRecovery: CheckMigrationRecoveryUseCase,
) : ViewModel() {
    private var hasSyncErrorBeenShown = false
    private var hasRestoreSuccessBeenShown = false
    private var hasAttemptedPendingVotingRouteRecovery = false
    private var hasRecoveredPendingVotingRoute = false
    private var hasResumedShareTracking = false

    init {
        viewModelScope.launch { checkMigrationRecovery() }
    }

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
            .map { messageState ->
                createState(
                    messageState = messageState
                )
            }.stateIn(
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
                hasRecoveredPendingVotingRoute = recoverPendingVotingRouteIfNeeded()
            }

            if (!hasResumedShareTracking) {
                hasResumedShareTracking = true
                resumePendingShareTracking()
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

    private suspend fun recoverPendingVotingRouteIfNeeded(): Boolean {
        runCatching {
            refreshActiveVotingSession()
        }.getOrElse {
            return false
        }
        val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
        var recovery: VotingRecoverySnapshot? = null
        for (roundId in votingApiRepository.snapshot.value.sessionsByRoundId.keys) {
            val candidate = votingRecoveryRepository.get(accountUuid, roundId)
            if (candidate?.pendingKeystoneRequest != null) {
                recovery = candidate
                break
            }
        }
        recovery ?: return false
        val roundId = recovery.roundId
        val pendingRequest = recovery.pendingKeystoneRequest ?: return false
        val draftChoices =
            recovery.draftChoices
                .ifEmpty { recovery.proposalSelections.mapValues { (_, selection) -> selection.choiceId } }
        if (draftChoices.isEmpty()) {
            return false
        }

        votingApiRepository.snapshot.value.sessionsByRoundId[roundId]
            ?.toVotingRound()
            ?.let(votingApiRepository::upsertRound)
        votingSessionStore.restoreDraftVotes(accountUuid, roundId, draftChoices)

        val restoredRoutes =
            buildList {
                add(VoteProposalListArgs(roundId = roundId, mode = VoteProposalListMode.REVIEW))
                add(
                    VoteConfirmSubmissionArgs(
                        roundIdHex = roundId,
                        choicesJson = draftChoices.toChoicesJson()
                    )
                )
                add(SignKeystoneVotingArgs(roundIdHex = roundId))
                if (pendingRequest.routeStage == VotingKeystoneRouteStage.SCAN) {
                    add(
                        ScanKeystoneVotingPCZTRequest(
                            roundIdHex = roundId,
                            bundleIndex = pendingRequest.bundleIndex,
                            actionIndex = pendingRequest.actionIndex
                        )
                    )
                }
            }

        navigationRouter.replaceAll(*restoredRoutes.toTypedArray())
        return true
    }

    /**
     * Re-enqueue share-tracking workers for any rounds the wallet finished submitting in a prior
     * launch. iOS triggers the equivalent on `governanceTabAppeared`; on Android the WorkManager
     * worker outlives the process, but if the OS killed the app between `markVoteSubmitted` and
     * the scheduler call in `SubmitVotesUseCase`, no worker was ever enqueued. Scheduling here
     * uses `ExistingWorkPolicy.REPLACE`, so re-enqueueing an active worker is a no-op, and
     * `TrackVotingSharesUseCase` short-circuits when no unconfirmed shares remain.
     *
     * Scoped to the currently selected account, mirroring the per-account pattern used by
     * `recoverPendingVotingRouteIfNeeded` above.
     */
    private suspend fun resumePendingShareTracking() {
        val accountUuid =
            runCatching {
                getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
            }.getOrNull() ?: return
        val pendingRoundIds =
            runCatching {
                votingRecoveryRepository.getRoundIdsRequiringShareTracking(accountUuid)
            }.getOrDefault(emptyList())
        pendingRoundIds.forEach { roundId ->
            votingShareTrackingScheduler.schedule(roundId)
        }
    }

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
                ShieldFundsMessageState(
                    subtitle =
                        stringRes(
                            R.string.home_message_transparent_balance_subtitle,
                            stringRes(data.zatoshi, HIDDEN).asPrivacySensitive(),
                            CURRENCY_TICKER
                        ),
                    onClick =
                        if (isShieldFundsInfoEnabled) {
                            { onShieldFundsMessageClick() }
                        } else {
                            null
                        },
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

            is HomeMessageData.Migration -> {
                val plan = data.plan
                val percent = if (plan != null && plan.totalCount > 0) {
                    (plan.completedCount * 100) / plan.totalCount
                } else {
                    0
                }
                val (phase, subtitle) = when {
                    data.isComplete -> MigrationBannerPhase.COMPLETE to "Tap to review the details"
                    plan == null -> MigrationBannerPhase.REQUIRED to null
                    plan.completedCount == 0 -> MigrationBannerPhase.IN_PROGRESS to "First transfer sending…"
                    else ->
                        MigrationBannerPhase.IN_PROGRESS to
                            "${plan.completedCount} of ${plan.totalCount} transfers done ~ $percent% complete"
                }
                MigrationMessageState(
                    phase = phase,
                    progressLabel = subtitle,
                    progressPercent = percent.toFloat(),
                    onClick = { onMigrationMessageClick(hasActivePlan = plan != null) },
                    onButtonClick = { onMigrationMessageClick(hasActivePlan = plan != null) },
                )
            }

            null -> {
                null
            }
        }

    private fun onMigrationMessageClick(hasActivePlan: Boolean) = viewModelScope.launch {
        if (hasActivePlan) {
            navigationRouter.forward(MigrationProgressArgs)
        } else {
            navigationRouter.forward(MigrationSetupArgs)
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

    private fun onWalletDisconnectedMessageClick() = navigationRouter.forward(WalletDisconnectedInfo)

    private fun onWalletBackupMessageClick() = navigationRouter.forward(SeedBackupInfo)

    private fun onWalletBackupMessageButtonClick() = navigationRouter.forward(WalletBackupDetail(false))

    private fun onShieldFundsMessageClick() = viewModelScope.launch { shieldFundsFromMessage() }

    private fun onShieldFundsMessageButtonClick() = viewModelScope.launch { shieldFundsFromMessage() }

    private fun onWalletErrorMessageClick(homeMessageData: HomeMessageData.Error) =
        navigateToError(ErrorArgs.SyncError(homeMessageData.synchronizerError))
}

private fun VotingSession.toVotingRound() =
    VotingRound(
        id = voteRoundId.toLowerHex(),
        title = title,
        description = description,
        discussionUrl = discussionUrl,
        createdAtHeight = createdAtHeight,
        snapshotHeight = snapshotHeight,
        snapshotDate = ceremonyStart.takeIf { it.epochSecond > 0 } ?: voteEndTime,
        votingStart = ceremonyStart,
        votingEnd = voteEndTime,
        proposals = proposals,
        status = status
    )

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun Map<Int, Int>.toChoicesJson(): String =
    JSONObject(toSortedMap().mapKeys { (proposalId, _) -> proposalId.toString() }).toString()
