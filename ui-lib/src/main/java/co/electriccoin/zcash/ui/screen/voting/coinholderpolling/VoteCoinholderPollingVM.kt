package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.Lce
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.LceSource
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.effectiveChoices
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase
import co.electriccoin.zcash.ui.common.usecase.VotingShareTrackingResult
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.VoteTrustIndicator
import co.electriccoin.zcash.ui.screen.voting.chainconfig.VoteChainConfigArgs
import co.electriccoin.zcash.ui.screen.voting.isDefaultVotingConfig
import co.electriccoin.zcash.ui.screen.voting.normalizedVotingRoundIds
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import co.electriccoin.zcash.ui.screen.voting.voteTrustIndicatorFor
import co.electriccoin.zcash.ui.screen.voting.votingerror.VotingErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteCoinholderPollingVM(
    private val refreshActiveVotingSession: RefreshActiveVotingSessionUseCase,
    private val refreshVotingRounds: RefreshVotingRoundsUseCase,
    private val configurationRepository: ConfigurationRepository,
    private val votingChainConfigRepository: VotingChainConfigRepository,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val trackVotingShares: TrackVotingSharesUseCase,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
) : ViewModel() {
    private val roundsLce = mutableLce<List<VotingRound>>(Lce(loading = true))
    private val screenRefreshPending = MutableStateFlow(true)
    private val configRefreshPending = MutableStateFlow(false)
    private val loadedConfigSource = MutableStateFlow<String?>(null)
    private val selectedConfigSource =
        votingChainConfigRepository.state
            .map { config -> config.selectedPinnedSourceKey() }
            .distinctUntilChanged()
    private val pollListLceSource =
        object : LceSource {
            override val loading = combine(
                roundsLce.loading,
                screenRefreshPending,
                configRefreshPending,
                selectedConfigSource,
                loadedConfigSource
            ) { loading, screenPending, configPending, selectedSource, loadedSource ->
                loading || screenPending || configPending || selectedSource != loadedSource
            }
            override val error = roundsLce.error
        }
    private var configIssue: VotingConfigException? = null
    private val configErrorSheet = MutableStateFlow<ZashiConfirmationState?>(null)
    private val unverifiedPollWarningSheet = MutableStateFlow<ZashiConfirmationState?>(null)
    private var pendingUnverifiedRoundSelection: PendingRoundSelection? = null
    private val recoveryVoteCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var recoveryVoteCountsJob: Job? = null
    private val selectedAccountUuid =
        observeSelectedWalletAccount.require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    init {
        viewModelScope.launch {
            selectedAccountUuid
                .filterNotNull()
                .collect { accountUuid ->
                    refreshRecoveryVoteCounts(votingApiRepository.snapshot.value.rounds, accountUuid)
                }
        }
        observeVotingChainConfigChanges()
        startVotingDataAutoRefresh()
        startForegroundShareTracking()
    }

    private val apiSnapshotWithConfig =
        combine(
            votingApiRepository.snapshot,
            votingChainConfigRepository.state,
            configurationRepository.configurationFlow
        ) { apiSnapshot, chainConfig, configuration ->
            ApiSnapshotWithConfig(
                apiSnapshot = apiSnapshot,
                selectedConfigSource = chainConfig.selectedPinnedSourceKey(),
                isOnDefaultConfig = isDefaultVotingConfig(chainConfig, configuration)
            )
        }
    private val apiSnapshotWithConfigReadiness =
        combine(apiSnapshotWithConfig, loadedConfigSource) { apiSnapshotWithConfig, loadedSource ->
            apiSnapshotWithConfig.copy(
                isSelectedConfigLoaded = apiSnapshotWithConfig.selectedConfigSource == loadedSource
            )
        }

    val state =
        combine(
            apiSnapshotWithConfigReadiness,
            roundsLce.state,
            recoveryVoteCounts,
            votingSessionStore.state,
            selectedAccountUuid,
        ) { apiSnapshotWithConfig, roundsLceState, persistedVoteCounts, sessionState, accountUuid ->
            val apiSnapshot = apiSnapshotWithConfig.apiSnapshot
            val rounds = when {
                !apiSnapshotWithConfig.isSelectedConfigLoaded -> null
                roundsLceState.loading -> null
                apiSnapshot.rounds.isNotEmpty() -> apiSnapshot.rounds
                roundsLceState.content is LceContent.Success -> emptyList()
                else -> null
            }
            val currentAccountUuid = accountUuid ?: return@combine null

            rounds?.let {
                val normalizedEndorsedRoundIds = apiSnapshot.zodlEndorsedRoundIds.normalizedVotingRoundIds()
                // Mirror iOS `RoundListItem.roundNumber` (`VotingStore+Session.swift:38-42`):
                // assign stable 1-based numbers from `createdAtHeight` ascending order over
                // the full unfiltered round list so numbering matches iOS and stays stable
                // when `isOnDefaultConfig` toggles the visible subset.
                val roundNumbersById = it
                    .sortedBy { round -> round.createdAtHeight }
                    .withIndex()
                    .associate { (index, round) -> round.id to (index + 1) }
                val visibleRounds = visibleRounds(
                    rounds = it,
                    endorsedRoundIds = normalizedEndorsedRoundIds,
                    isOnDefaultConfig = apiSnapshotWithConfig.isOnDefaultConfig
                )
                val sortedRounds = visibleRounds
                    .sortedWith(
                        compareByDescending<VotingRound> { round -> round.createdAtHeight.takeIf { it > 0 } ?: round.snapshotHeight }
                            .thenByDescending { round -> round.snapshotHeight }
                            .thenByDescending { round -> round.votingEnd.epochSecond }
                            .thenBy { round -> round.id }
                    )
                val (activeSrc, pastSrc) = sortedRounds
                    .partition { round ->
                        round.status == SessionStatus.ACTIVE || round.status == SessionStatus.TALLYING
                    }

                VoteCoinholderPollingState(
                    activeRounds = activeSrc.map { round ->
                        buildCard(
                            round = round,
                            roundNumber = roundNumbersById[round.id] ?: 0,
                            votedProposalCount = sessionState.submittedProposalCount(currentAccountUuid, round.id)
                                ?: persistedVoteCounts[round.id],
                            trustIndicator = trustIndicatorFor(
                                round = round,
                                endorsedRoundIds = normalizedEndorsedRoundIds,
                                isOnDefaultConfig = apiSnapshotWithConfig.isOnDefaultConfig
                            )
                        )
                    },
                    pastRounds = pastSrc.map { round ->
                        buildCard(
                            round = round,
                            roundNumber = roundNumbersById[round.id] ?: 0,
                            votedProposalCount = sessionState.submittedProposalCount(currentAccountUuid, round.id)
                                ?: persistedVoteCounts[round.id],
                            trustIndicator = trustIndicatorFor(
                                round = round,
                                endorsedRoundIds = normalizedEndorsedRoundIds,
                                isOnDefaultConfig = apiSnapshotWithConfig.isOnDefaultConfig
                            )
                        )
                    },
                    onBack = ::onBack,
                    onRefresh = ::refreshVotingData,
                    onConfigSettings = ::onConfigSettings,
                )
            }
        }.let { contentFlow ->
            combine(
                contentFlow,
                screenRefreshPending,
                configRefreshPending,
                configErrorSheet,
                unverifiedPollWarningSheet
            ) { content, refreshPending, configPending, configSheet, unverifiedSheet ->
                // The poll-list VM is retained behind deeper voting screens. Suppress old
                // content until ON_RESUME starts the refresh, otherwise Compose can draw a
                // stale poll card for one frame before the loading state arrives.
                content.takeUnless { refreshPending || configPending }?.copy(
                    configErrorSheet = configSheet,
                    unverifiedPollWarningSheet = unverifiedSheet
                )
            }
        }.withLce(pollListLceSource) { error ->
            errorStateMapper.mapToState(
                error = error,
                title = stringRes(R.string.vote_error_unable_to_load_polls_title),
                message = stringRes(R.string.vote_error_unable_to_load_polls_message),
                primaryStyle = ButtonStyle.PRIMARY
            )
        }.stateIn(this)

    private fun buildCard(
        round: VotingRound,
        roundNumber: Int,
        votedProposalCount: Int?,
        trustIndicator: VoteTrustIndicator?,
    ): VotePollCardState {
        val total = round.proposals.size
        val count = votedProposalCount?.coerceIn(0, total) ?: 0
        val hasConfirmedVote = votedProposalCount != null
        val status = when {
            round.status == SessionStatus.ACTIVE && hasConfirmedVote -> VotePollCardStatus.VOTED
            round.status == SessionStatus.ACTIVE -> VotePollCardStatus.ACTIVE
            else -> VotePollCardStatus.CLOSED
        }

        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val dateLabel = when (status) {
            VotePollCardStatus.ACTIVE,
            VotePollCardStatus.VOTED -> stringRes(R.string.vote_poll_card_closes, formatter.format(round.votingEnd))

            VotePollCardStatus.CLOSED -> stringRes(R.string.vote_poll_card_closed, formatter.format(round.votingEnd))
        }

        return VotePollCardState(
            roundId = round.id,
            roundNumber = roundNumber,
            title = stringRes(round.title),
            description = if (round.description.isNotEmpty()) {
                stringRes(round.description)
            } else {
                stringRes("")
            },
            status = status,
            sessionStatus = round.status,
            isActionEnabled = true,
            dateLabel = dateLabel,
            trustIndicator = trustIndicator,
            votedLabel = if (hasConfirmedVote && total > 0) {
                stringRes(R.string.vote_poll_voted_count, count, total)
            } else {
                null
            },
            proposalCount = total,
            votedCount = count,
            onAction = { onRoundSelected(round, status) }
        )
    }

    fun onScreenEntered() {
        refreshVotingData()
        screenRefreshPending.value = false
    }

    fun onScreenExited() {
        screenRefreshPending.value = true
    }

    private fun refreshVotingData() {
        roundsLce.execute {
            refreshVotingDataInternal(resetVisibleConfigError = true)
        }
    }

    private fun startVotingDataAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(votingDataAutoRefreshIntervalMs())
                if (roundsLce.state.value.loading) {
                    continue
                }

                try {
                    refreshVotingDataInternal(resetVisibleConfigError = false)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    Log.w(TAG, "Round list auto refresh failed", throwable)
                }
            }
        }
    }

    /**
     * Foreground driver that mirrors iOS `pollShareStatus` (`VotingStore+Navigation.swift:267-419`).
     *
     * iOS starts foreground polling on `governanceTabAppeared` and cancels it on
     * `governanceTabDisappeared` (`VotingStore+Session.swift:565-575`). The polls-list VM is the
     * Android analogue of that lifecycle: the user's entry to (and exit from) the voting flow.
     * Without this driver, share confirmations on a fresh launch lag until the WorkManager worker
     * fires its scheduled run; the worker continues to handle backgrounded/killed-app coverage
     * and is not modified here.
     *
     * Per-round work runs `TrackVotingSharesUseCase` in a `Pending(delayMillis) -> delay -> invoke`
     * loop, matching the cadence the worker uses (3-30s adaptive). Each `viewModelScope.launch`
     * is cancelled by `ViewModel.clear()` when the user navigates back out of the polls list, so
     * no explicit teardown is required.
     *
     * Multi-round: tracks every round with a non-empty `submittedProposalIds` for the currently
     * selected account, mirroring `getRoundIdsRequiringShareTracking` (the same enumeration the
     * cold-launch resume in `HomeVM` uses). The use case is idempotent across the worker and the
     * foreground driver — `markShareConfirmed` is a no-op when already confirmed and
     * `addSentServers` excludes already-sent URLs — so duplicate work between the WorkManager
     * worker and this driver is safe. Re-discovery inside the outer loop picks up rounds that
     * become eligible while the user is on this screen (e.g. after returning from a successful
     * submission).
     */
    private fun startForegroundShareTracking() {
        viewModelScope.launch {
            // `collectLatest` cancels the previous tracking scope when the selected account
            // changes — `runForegroundShareTracking` never returns on its own (`while (true)`),
            // so a plain `collect` would starve later emissions.
            selectedAccountUuid
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { accountUuid ->
                    runForegroundShareTracking(accountUuid)
                }
        }
    }

    private suspend fun runForegroundShareTracking(accountUuid: String) {
        // `coroutineScope` makes the per-round children a structured group so a switch to a
        // different `accountUuid` (the outer `collect` re-invokes us) cancels every in-flight
        // round loop deterministically before the next account starts.
        coroutineScope {
            val activeRoundIds = mutableSetOf<String>()
            // `Completed` is sticky for this driver's lifetime: the repo's
            // `getRoundIdsRequiringShareTracking` only filters on `submittedProposalIds.isNotEmpty()`
            // and does NOT exclude rounds whose shares are all confirmed, so without this set we
            // would re-launch `TrackVotingSharesUseCase` every 15s for already-finished rounds.
            // Mirrors the WorkManager worker which doesn't re-enqueue on `Completed`.
            val completedRoundIds = mutableSetOf<String>()
            while (true) {
                val pendingRoundIds = runCatching {
                    votingRecoveryRepository.getRoundIdsRequiringShareTracking(accountUuid)
                }.getOrDefault(emptyList())
                pendingRoundIds
                    .filter { roundId ->
                        roundId !in completedRoundIds && activeRoundIds.add(roundId)
                    }
                    .forEach { roundId ->
                        launch {
                            var completed = false
                            try {
                                completed = trackRoundUntilCompleted(roundId)
                            } finally {
                                if (completed) {
                                    // Keep the id in `activeRoundIds` so a defensive `add(roundId)`
                                    // would also reject it; `completedRoundIds` is the
                                    // authoritative gate.
                                    completedRoundIds.add(roundId)
                                } else {
                                    // Cancellation or non-`Completed` error: free the slot so the
                                    // next outer tick can retry.
                                    activeRoundIds.remove(roundId)
                                }
                            }
                        }
                    }
                delay(FOREGROUND_REDISCOVERY_INTERVAL_MS)
            }
        }
    }

    /**
     * @return `true` iff the round exited via `VotingShareTrackingResult.Completed` (terminal),
     *         `false` if a non-cancellation error short-circuited the loop. Cancellation
     *         propagates as `CancellationException` and never returns.
     */
    private suspend fun trackRoundUntilCompleted(roundId: String): Boolean {
        while (true) {
            val outcome = runCatching { trackVotingShares(roundId) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    Log.w(TAG, "Foreground share tracking failed for round $roundId", throwable)
                }
                .getOrElse { return false }
            when (outcome) {
                VotingShareTrackingResult.Completed -> return true
                is VotingShareTrackingResult.Pending ->
                    delay(outcome.delayMillis.coerceAtLeast(FOREGROUND_MIN_DELAY_MILLIS))
            }
        }
    }

    private fun observeVotingChainConfigChanges() {
        viewModelScope.launch {
            var isFirstEmission = true
            votingChainConfigRepository.state
                .map { config -> config.selectedPinnedSource.orEmpty() }
                .distinctUntilChanged()
                .collect {
                    if (isFirstEmission) {
                        isFirstEmission = false
                    } else {
                        refreshVotingDataForConfigChange()
                    }
                }
        }
    }

    private fun refreshVotingDataForConfigChange() {
        configRefreshPending.value = true
        roundsLce.execute {
            try {
                refreshVotingDataInternal(resetVisibleConfigError = true)
            } finally {
                configRefreshPending.value = false
            }
        }
    }

    private suspend fun refreshVotingDataInternal(resetVisibleConfigError: Boolean): List<VotingRound> {
        if (resetVisibleConfigError) {
            configIssue = null
            configErrorSheet.value = null
            clearLoadedVotingStateForServiceConfigRefresh()
            // Mirror iOS `prepareForServiceConfigRefresh` (VotingStore+Session.swift:644-647):
            // every flow entry / user-driven refresh drops the cached resolved config so
            // downstream callers (authenticateVotingSession, configuredVoteServerUrls,
            // delegateShares) cannot serve a stale config across flow openings. Auto-refresh
            // polls leave the cache intact since they already force-refresh via
            // RefreshVotingRoundsUseCase -> fetchServiceConfig.
            votingApiProvider.invalidateConfigCache()
        }

        refreshVotingRounds()
        var nextConfigIssue: VotingConfigException? = null
        runCatching {
            refreshActiveVotingSession()
        }.onFailure { throwable ->
            if (throwable is VotingConfigException) {
                nextConfigIssue = throwable
            } else {
                Log.w(TAG, "Active round refresh failed", throwable)
            }
        }
        configIssue = nextConfigIssue

        selectedAccountUuid.value?.let { accountUuid ->
            refreshRecoveryVoteCounts(votingApiRepository.snapshot.value.rounds, accountUuid)
        } ?: run {
            recoveryVoteCounts.value = emptyMap()
        }
        loadedConfigSource.value = votingChainConfigRepository.state.value.selectedPinnedSourceKey()
        return votingApiRepository.snapshot.value.rounds
    }

    private suspend fun clearLoadedVotingStateForServiceConfigRefresh() {
        unverifiedPollWarningSheet.value = null
        pendingUnverifiedRoundSelection = null
        recoveryVoteCountsJob?.cancel()
        recoveryVoteCountsJob = null
        recoveryVoteCounts.value = emptyMap()
        votingConfigRepository.clear()
        votingSessionStore.clear()
        votingApiRepository.clear()
    }

    private fun votingDataAutoRefreshIntervalMs(): Long {
        val rounds = votingApiRepository.snapshot.value.rounds
        val hasRoundChangingStatus = rounds.isEmpty() ||
            rounds.any { round -> round.status == SessionStatus.ACTIVE || round.status == SessionStatus.TALLYING }
        return if (hasRoundChangingStatus) {
            ROUND_STATUS_AUTO_REFRESH_INTERVAL_MS
        } else {
            ROUND_LIST_AUTO_REFRESH_INTERVAL_MS
        }
    }

    private fun refreshRecoveryVoteCounts(
        rounds: List<VotingRound>,
        accountUuid: String
    ) {
        recoveryVoteCountsJob?.cancel()
        if (rounds.isEmpty()) {
            recoveryVoteCounts.value = emptyMap()
            return
        }

        recoveryVoteCountsJob = viewModelScope.launch {
            recoveryVoteCounts.value =
                buildMap {
                    rounds.forEach { round ->
                        val recovery = votingRecoveryRepository.get(accountUuid, round.id) ?: return@forEach
                        if (recovery.submittedAtEpochSeconds == null) {
                            return@forEach
                        }

                        val votedCount = recovery.effectiveChoices(round.proposals).size
                        if (votedCount > 0) {
                            put(round.id, votedCount)
                        }
                    }
                }
        }
    }

    private fun onRoundSelected(
        round: VotingRound,
        status: VotePollCardStatus
    ) {
        viewModelScope.launch {
            if (!isOnDefaultConfig()) {
                pendingUnverifiedRoundSelection = PendingRoundSelection(round, status)
                unverifiedPollWarningSheet.value = buildUnverifiedPollWarningSheet()
                return@launch
            }

            openRound(round, status)
        }
    }

    private suspend fun openRound(
        round: VotingRound,
        status: VotePollCardStatus
    ) {
        val accountUuid = selectedAccountUuid.value ?: return
        when (status) {
            VotePollCardStatus.ACTIVE -> {
                val issue = configIssue
                if (issue != null) {
                    configErrorSheet.value = buildConfigErrorSheet(issue.message.orEmpty())
                    return
                }

                // Re-hydrate any draft votes persisted during a prior session that was killed
                // before reaching the confirmation screen. Mirrors iOS `loadDrafts` at
                // `VotingStore+Session.swift:293`. The VOTED branch already does this for
                // submitted rounds; ACTIVE re-entry needs the same so per-tap persistence
                // (see VoteProposalDetailVM.persistDraftsForCurrentRound) round-trips.
                val persistedDrafts =
                    votingRecoveryRepository.get(accountUuid, round.id)?.draftChoices.orEmpty()
                if (persistedDrafts.isNotEmpty()) {
                    votingSessionStore.restoreDraftVotes(accountUuid, round.id, persistedDrafts)
                }

                navigationRouter.forward(
                    VoteProposalListArgs(
                        roundId = round.id,
                        mode = VoteProposalListMode.VOTING
                    )
                )
            }

            VotePollCardStatus.VOTED -> {
                val recovery = votingRecoveryRepository.get(accountUuid, round.id)
                val draftChoices = recovery?.effectiveChoices(round.proposals).orEmpty()

                if (draftChoices.isNotEmpty()) {
                    votingSessionStore.restoreDraftVotes(accountUuid, round.id, draftChoices)
                    navigationRouter.forward(
                        VoteProposalListArgs(
                            roundId = round.id,
                            mode = VoteProposalListMode.VOTED
                        )
                    )
                } else {
                    navigateToRoundOutcome(round)
                }
            }

            VotePollCardStatus.CLOSED -> navigateToRoundOutcome(round)
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun onConfigSettings() = navigationRouter.forward(VoteChainConfigArgs)

    private fun buildConfigErrorSheet(rawMessage: String) =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = VotingErrorMapper.toConfigErrorTitle(rawMessage),
            message = VotingErrorMapper.toConfigErrorMessage(rawMessage),
            primaryAction = ButtonState(
                text = stringRes(R.string.vote_dismiss),
                style = ButtonStyle.PRIMARY,
                onClick = ::dismissConfigErrorSheet
            ),
            secondaryAction = ButtonState(
                text = stringRes(R.string.vote_error_go_back),
                style = ButtonStyle.TERTIARY,
                onClick = ::goBackFromConfigErrorSheet
            ),
            onBack = ::dismissConfigErrorSheet
        )

    private fun dismissConfigErrorSheet() {
        configErrorSheet.value = null
    }

    private fun goBackFromConfigErrorSheet() {
        dismissConfigErrorSheet()
        navigationRouter.back()
    }

    private fun navigateToRoundOutcome(round: VotingRound) {
        when (round.status) {
            SessionStatus.TALLYING ->
                navigationRouter.forward(VoteTallyingArgs(roundIdHex = round.id))

            else ->
                navigationRouter.forward(VoteResultsArgs(roundIdHex = round.id))
        }
    }

    private fun visibleRounds(
        rounds: List<VotingRound>,
        endorsedRoundIds: Set<String>,
        isOnDefaultConfig: Boolean
    ): List<VotingRound> {
        if (!isOnDefaultConfig) {
            return rounds
        }
        return rounds.filter { round -> round.id.lowercase() in endorsedRoundIds }
    }

    private fun trustIndicatorFor(
        round: VotingRound,
        endorsedRoundIds: Set<String>,
        isOnDefaultConfig: Boolean
    ): VoteTrustIndicator? =
        voteTrustIndicatorFor(
            roundId = round.id,
            endorsedRoundIds = endorsedRoundIds,
            isOnDefaultConfig = isOnDefaultConfig
        )

    private fun isOnDefaultConfig(): Boolean =
        isDefaultVotingConfig(
            chainConfig = votingChainConfigRepository.state.value,
            configuration = configurationRepository.configurationFlow.value
        )

    private fun buildUnverifiedPollWarningSheet() =
        ZashiConfirmationState(
            icon = R.drawable.ic_alert_circle,
            title = stringRes(R.string.vote_unverified_poll_title),
            message = stringRes(R.string.vote_unverified_poll_message),
            primaryAction = ButtonState(
                text = stringRes(R.string.vote_error_go_back),
                style = ButtonStyle.PRIMARY,
                onClick = ::dismissUnverifiedPollWarning
            ),
            secondaryAction = ButtonState(
                text = stringRes(R.string.vote_proceed_anyway),
                style = ButtonStyle.SECONDARY,
                onClick = ::proceedFromUnverifiedPollWarning
            ),
            onBack = ::dismissUnverifiedPollWarning,
            style = ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING
        )

    private fun proceedFromUnverifiedPollWarning() {
        val selection = pendingUnverifiedRoundSelection
        pendingUnverifiedRoundSelection = null
        unverifiedPollWarningSheet.value = null
        if (selection != null) {
            viewModelScope.launch {
                openRound(selection.round, selection.status)
            }
        }
    }

    private fun dismissUnverifiedPollWarning() {
        pendingUnverifiedRoundSelection = null
        unverifiedPollWarningSheet.value = null
    }

    private companion object {
        const val TAG = "VoteCoinholderPolling"
        const val ROUND_STATUS_AUTO_REFRESH_INTERVAL_MS = 5_000L
        const val ROUND_LIST_AUTO_REFRESH_INTERVAL_MS = 30_000L

        // Minimum sleep between successive `TrackVotingSharesUseCase` invocations for a round.
        // Matches `MIN_DELAY_MILLIS` inside the use case itself; duplicated here as a defensive
        // floor in case the use case ever returns a smaller value.
        const val FOREGROUND_MIN_DELAY_MILLIS = 3_000L

        // Cadence at which the foreground driver re-checks `getRoundIdsRequiringShareTracking`
        // to pick up newly submitted rounds. Each round-specific loop runs independently; this
        // only governs how quickly a freshly submitted round becomes tracked while the user is
        // already on the polls list. Aligned with the worker's default `Pending` delay.
        const val FOREGROUND_REDISCOVERY_INTERVAL_MS = 15_000L
    }
}

private data class ApiSnapshotWithConfig(
    val apiSnapshot: VotingApiSnapshot,
    val selectedConfigSource: String,
    val isOnDefaultConfig: Boolean,
    val isSelectedConfigLoaded: Boolean = false
)

private data class PendingRoundSelection(
    val round: VotingRound,
    val status: VotePollCardStatus
)

private fun VotingChainConfigState.selectedPinnedSourceKey(): String =
    selectedPinnedSource.orEmpty()
