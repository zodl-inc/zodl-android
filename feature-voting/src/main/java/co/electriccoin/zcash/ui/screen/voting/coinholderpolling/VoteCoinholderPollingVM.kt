package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.Lce
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.LceSource
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
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
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingServiceConfigUseCase
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("LargeClass")
class VoteCoinholderPollingVM(
    private val refreshVotingServiceConfig: RefreshVotingServiceConfigUseCase,
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
            override val loading =
                combine(
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
        observeSelectedWalletAccount
            .require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }
            .stateIn(this)

    init {
        viewModelScope.launch {
            selectedAccountUuid
                .filterNotNull()
                .collect { accountUuid ->
                    refreshRecoveryVoteCounts(votingApiRepository.snapshot.value.rounds, accountUuid)
                }
        }
        observeVotingChainConfigChanges()
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

    private val initialLoadingState: VoteCoinholderPollingState
        get() =
            VoteCoinholderPollingState(
                onBack = ::onBack,
                onRefresh = ::refreshVotingData,
                onConfigSettings = ::onConfigSettings,
                isInitialLoading = true,
            )

    val state =
        combine(
            apiSnapshotWithConfigReadiness,
            roundsLce.state,
            recoveryVoteCounts,
            votingSessionStore.state,
            selectedAccountUuid,
        ) { apiSnapshotWithConfig, roundsLceState, persistedVoteCounts, sessionState, accountUuid ->
            val apiSnapshot = apiSnapshotWithConfig.apiSnapshot
            val rounds =
                when {
                    // Cache-and-fresh: show the repository's last-fetched rounds immediately,
                    // even before *this* VM instance (recreated on every screen re-entry) has
                    // completed its own load — `isSelectedConfigLoaded` is VM-local state that
                    // resets to false on every fresh instance, but `apiSnapshot.rounds` is a
                    // repository-scoped cache that `onScreenEntered()`'s soft refresh
                    // deliberately preserves (see softRefresh's "avoid card flicker" comment in
                    // refreshVotingDataInternal). A genuine config-source switch or explicit
                    // refresh already clears the repository first (clearLoadedVotingStateForServiceConfigRefresh),
                    // so trusting non-empty `apiSnapshot.rounds` here never shows stale-source data.
                    apiSnapshot.rounds.isNotEmpty() -> apiSnapshot.rounds

                    !apiSnapshotWithConfig.isSelectedConfigLoaded -> null

                    roundsLceState.loading -> null

                    roundsLceState.content is LceContent.Success -> emptyList()

                    else -> null
                }

            val currentAccountUuid = accountUuid ?: return@combine initialLoadingState

            rounds?.let {
                val normalizedEndorsedRoundIds = apiSnapshot.zodlEndorsedRoundIds.normalizedVotingRoundIds()
                // Mirror iOS `RoundListItem.roundNumber` (`VotingStore+Session.swift:38-42`):
                // assign stable 1-based numbers from `createdAtHeight` ascending order over
                // the full unfiltered round list so numbering matches iOS and stays stable
                // when `isOnDefaultConfig` toggles the visible subset.
                val roundNumbersById =
                    it
                        .sortedBy { round -> round.createdAtHeight }
                        .withIndex()
                        .associate { (index, round) -> round.id to (index + 1) }
                val visibleRounds =
                    visibleRounds(
                        rounds = it,
                        endorsedRoundIds = normalizedEndorsedRoundIds,
                        isOnDefaultConfig = apiSnapshotWithConfig.isOnDefaultConfig
                    )
                // Only truly ACTIVE rounds can be voted on; TALLYING rounds are already closed.
                val (activeSrc, pastSrc) =
                    visibleRounds
                        .partition { round -> round.status == SessionStatus.ACTIVE }

                // Split active rounds into voted (VOTED card) and not-yet-voted (ACTIVE card).
                val (votedActiveSrc, unvotedActiveSrc) =
                    activeSrc.partition { round ->
                        (
                            sessionState.submittedProposalCount(currentAccountUuid, round.id)
                                ?: persistedVoteCounts[round.id]
                        ) != null
                    }

                val votingEndAsc =
                    compareBy<VotingRound> { round -> round.votingEnd.epochSecond }
                        .thenBy { round -> round.id }
                val votingEndDesc =
                    compareByDescending<VotingRound> { round -> round.votingEnd.epochSecond }
                        .thenBy { round -> round.id }

                // Order: Active (unvoted) → Voted (voted) → Closed; each group by votingEnd.
                val sortedActiveRounds =
                    unvotedActiveSrc.sortedWith(votingEndAsc) + votedActiveSrc.sortedWith(votingEndAsc)
                val sortedPastRounds = pastSrc.sortedWith(votingEndDesc)

                VoteCoinholderPollingState(
                    activeRounds =
                        sortedActiveRounds.map { round ->
                            buildCard(
                                round = round,
                                roundNumber = roundNumbersById[round.id] ?: 0,
                                votedProposalCount =
                                    sessionState.submittedProposalCount(currentAccountUuid, round.id)
                                        ?: persistedVoteCounts[round.id],
                                trustIndicator =
                                    trustIndicatorFor(
                                        round = round,
                                        endorsedRoundIds = normalizedEndorsedRoundIds,
                                        isOnDefaultConfig = apiSnapshotWithConfig.isOnDefaultConfig
                                    )
                            )
                        },
                    pastRounds =
                        sortedPastRounds.map { round ->
                            buildCard(
                                round = round,
                                roundNumber = roundNumbersById[round.id] ?: 0,
                                votedProposalCount =
                                    sessionState.submittedProposalCount(currentAccountUuid, round.id)
                                        ?: persistedVoteCounts[round.id],
                                trustIndicator =
                                    trustIndicatorFor(
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
            } ?: initialLoadingState
        }.let { contentFlow ->
            combine(
                contentFlow,
                screenRefreshPending,
                configRefreshPending,
                configErrorSheet,
                unverifiedPollWarningSheet
            ) { content, _, _, configSheet, unverifiedSheet ->
                val noRoundsSheet =
                    if (content.activeRounds?.isEmpty() == true && content.pastRounds?.isEmpty() == true) {
                        buildNoRoundsSheet()
                    } else {
                        null
                    }
                content.copy(
                    configErrorSheet = configSheet,
                    unverifiedPollWarningSheet = unverifiedSheet,
                    noRoundsSheet = noRoundsSheet
                )
            }
        }.withLce(pollListLceSource) { error ->
            errorStateMapper.mapToState(
                error = error,
                title = stringRes(R.string.coinVote_pollsList_loadErrorTitle),
                message = stringRes(R.string.vote_error_unable_to_load_polls_message),
                primaryStyle = ButtonStyle.PRIMARY
            )
        }.stateIn(
            viewModel = this,
            initialValue = LceState(initialLoadingState)
        )

    private fun buildCard(
        round: VotingRound,
        roundNumber: Int,
        votedProposalCount: Int?,
        trustIndicator: VoteTrustIndicator?,
    ): VotePollCardState {
        val total = round.proposals.size
        val count = votedProposalCount?.coerceIn(0, total) ?: 0
        val hasConfirmedVote = votedProposalCount != null
        val status =
            when {
                round.status == SessionStatus.ACTIVE && hasConfirmedVote -> VotePollCardStatus.VOTED
                round.status == SessionStatus.ACTIVE -> VotePollCardStatus.ACTIVE
                else -> VotePollCardStatus.CLOSED
            }

        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val dateLabel =
            when (status) {
                VotePollCardStatus.ACTIVE,
                VotePollCardStatus.VOTED -> {
                    stringRes(R.string.coinVote_pollsList_dateCloses, formatter.format(round.votingEnd))
                }

                VotePollCardStatus.CLOSED -> {
                    stringRes(R.string.coinVote_pollsList_dateClosed, formatter.format(round.votingEnd))
                }
            }

        return VotePollCardState(
            roundId = round.id,
            roundNumber = roundNumber,
            title = stringRes(round.title),
            description =
                if (round.description.isNotEmpty()) {
                    stringRes(round.description)
                } else {
                    stringRes("")
                },
            status = status,
            sessionStatus = round.status,
            isActionEnabled = true,
            dateLabel = dateLabel,
            trustIndicator = trustIndicator,
            votedLabel =
                if (hasConfirmedVote && total > 0) {
                    stringRes(R.string.coinVote_delegationSigning_signedProgress, count, total)
                } else {
                    null
                },
            proposalCount = total,
            votedCount = count,
            onAction = { onRoundSelected(round, status) }
        )
    }

    fun onScreenEntered() {
        roundsLce.execute {
            refreshVotingDataInternal(resetVisibleConfigError = true, softRefresh = true)
        }
        screenRefreshPending.value = false
    }

    fun onScreenExited() {
        screenRefreshPending.value = true
    }

    private fun refreshVotingData() {
        roundsLce.execute {
            // softRefresh = true (MOB-1808): this is the pull-to-refresh action — it should
            // behave like onScreenEntered()'s re-entry refresh, keeping the currently-visible
            // (possibly stale) rounds up while a fresh fetch runs, not clear the repository
            // cache first and force the full-screen loading view back for the whole round trip.
            refreshVotingDataInternal(resetVisibleConfigError = true, softRefresh = true)
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
                val pendingRoundIds =
                    runCatching {
                        votingRecoveryRepository.getRoundIdsRequiringShareTracking(accountUuid)
                    }.getOrDefault(emptyList())
                pendingRoundIds
                    .filter { roundId ->
                        roundId !in completedRoundIds && activeRoundIds.add(roundId)
                    }.forEach { roundId ->
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
            val outcome =
                runCatching { trackVotingShares(roundId) }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                        Log.w(TAG, "Foreground share tracking failed for round $roundId", throwable)
                    }.getOrElse { return false }
            when (outcome) {
                VotingShareTrackingResult.Completed -> {
                    return true
                }

                is VotingShareTrackingResult.Pending -> {
                    delay(outcome.delayMillis.coerceAtLeast(FOREGROUND_MIN_DELAY_MILLIS))
                }
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

    private suspend fun refreshVotingDataInternal(
        resetVisibleConfigError: Boolean,
        softRefresh: Boolean = false,
    ): List<VotingRound> {
        if (resetVisibleConfigError) {
            configIssue = null
            configErrorSheet.value = null
            if (!softRefresh) {
                clearLoadedVotingStateForServiceConfigRefresh()
            } else {
                // Soft re-entry: keep rounds/votes visible during refresh to avoid card flicker.
                // Still clear config so downstream callers resolve a fresh service config.
                unverifiedPollWarningSheet.value = null
                pendingUnverifiedRoundSelection = null
                votingConfigRepository.clear()
            }
            // Mirror iOS `prepareForServiceConfigRefresh` (VotingStore+Session.swift:644-647):
            // every flow entry / user-driven refresh drops the cached resolved config so
            // downstream callers (authenticateVotingSession, configuredVoteServerUrls,
            // delegateShares) cannot serve a stale config across flow openings. All three
            // remaining callers (onScreenEntered, refreshVotingData, refreshVotingDataForConfigChange)
            // pass resetVisibleConfigError = true, so this always runs now — MOB-1808 removed the
            // periodic auto-refresh tick that used to call this with resetVisibleConfigError =
            // false to deliberately skip invalidation and ride fetchServiceConfig()'s
            // CONFIG_CACHE_TTL_MS cache instead; the screen no longer has a background caller.
            votingApiProvider.invalidateConfigCache()
        }

        var nextConfigIssue: VotingConfigException? = null
        try {
            // refreshVotingRounds() -> RefreshVotingRoundsUseCase calls fetchServiceConfig()
            // before fetchAllRounds(), and fetchServiceConfig() is the ONLY source of
            // VotingConfigException on this path: it never touches round authentication
            // (that lives in the unrelated fetchActiveVotingSession()), and fetchAllRounds()
            // swallows per-round auth failures internally via authenticateVotingSessionOrNull
            // rather than propagating them. So catching VotingConfigException specifically
            // here cannot accidentally absorb a real round-fetch/auth failure — only a genuine
            // config problem lands here, and everything else still falls through to the
            // caller's normal LCE error handling. A non-VotingConfigException failure here
            // means a real round-fetch failure, which must still fail this whole refresh —
            // rethrow unconditionally instead of swallowing it.
            refreshVotingRounds()
        } catch (exception: VotingConfigException) {
            nextConfigIssue = exception
        }
        if (nextConfigIssue == null) {
            runCatching {
                // MOB-1808: this used to be RefreshActiveVotingSessionUseCase, which — despite
                // its name — fetches the service config AND the entire round list, redundantly
                // re-doing the fetchAllRounds() refreshVotingRounds() (above) just did. Its only
                // non-redundant job is storing the resolved VotingConfigSnapshot into
                // votingConfigRepository (refreshVotingRounds() above never does this — it just
                // resolves+caches the config for its own internal use); RefreshVotingServiceConfigUseCase
                // does just that store, without the second round-list fetch (confirmed live:
                // the old call was doubling every /rounds request during the CHP screen's
                // auto-refresh). This call is NOT the configIssue error-surfacing site anymore —
                // it only runs once refreshVotingRounds() above has already proven the config
                // resolves cleanly, so with the TTL cache (bumped to 10min) this is a guaranteed
                // cache hit in practice; the try/catch above is the real error path now (Milan's
                // #2483 review: the old ordering made this probe's error-surfacing dead code).
                // RefreshActiveVotingSessionUseCase itself is unchanged and still used as-is by
                // VotingHomeHooksImpl, which genuinely wants its round-list side effect for
                // pending-Keystone-request recovery.
                refreshVotingServiceConfig()
            }.onFailure { throwable ->
                if (throwable is VotingConfigException) {
                    nextConfigIssue = throwable
                } else {
                    Log.w(TAG, "Voting service config refresh failed", throwable)
                }
            }
        }
        configIssue = nextConfigIssue

        // MOB-1808 review (round 2): swallowing every config-leg failure into configIssue and
        // returning successfully is only safe when cached rounds are still visible — the
        // cache-and-fresh pattern (line ~175 above) keeps showing those while configIssue sits
        // quietly behind them, and the dedicated configErrorSheet still catches a real config
        // problem when the user taps an ACTIVE card. With an EMPTY rounds repo (first-ever
        // screen entry, or right after a config-source switch's hard
        // clearLoadedVotingStateForServiceConfigRefresh()), there is no cached content to fall
        // back to and no ACTIVE card to tap — silently completing here produced
        // roundsLceState = Success -> emptyList() and the misleading "there are no polls"
        // noRoundsSheet instead of a real error+retry state, for something as ordinary as being
        // offline. Rethrow so roundsLce.execute()'s catch (MutableLce.kt) routes it through the
        // normal LCE failure path instead, which withLce (below) turns into the proper
        // error+retry state via errorStateMapper.
        if (nextConfigIssue != null &&
            votingApiRepository.snapshot.value.rounds
                .isEmpty()
        ) {
            throw nextConfigIssue
        }

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

    private fun refreshRecoveryVoteCounts(
        rounds: List<VotingRound>,
        accountUuid: String
    ) {
        recoveryVoteCountsJob?.cancel()
        if (rounds.isEmpty()) {
            recoveryVoteCounts.value = emptyMap()
            return
        }

        recoveryVoteCountsJob =
            viewModelScope.launch {
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

            VotePollCardStatus.CLOSED -> {
                navigateToRoundOutcome(round)
            }
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun onConfigSettings() = navigationRouter.forward(VoteChainConfigArgs)

    private fun buildNoRoundsSheet() =
        ZashiConfirmationState.error(
            title = stringRes(R.string.coinVote_pollsList_emptyTitle),
            message = stringRes(R.string.coinVote_pollsList_emptyMessage),
            primaryText = stringRes(R.string.coinVote_common_refresh),
            primaryStyle = ButtonStyle.TERTIARY,
            secondaryText = stringRes(R.string.coinVote_common_gotIt),
            secondaryStyle = ButtonStyle.PRIMARY,
            onPrimary = ::refreshVotingData,
            onSecondary = ::onBack,
            onBack = ::onBack,
        )

    private fun buildConfigErrorSheet(rawMessage: String) =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = VotingErrorMapper.toConfigErrorTitle(rawMessage),
            message = VotingErrorMapper.toConfigErrorMessage(rawMessage),
            primaryAction =
                ButtonState(
                    text = stringRes(R.string.coinVote_common_dismiss),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::dismissConfigErrorSheet
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(R.string.coinVote_common_goBack),
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
            SessionStatus.TALLYING -> {
                navigationRouter.forward(VoteTallyingArgs(roundIdHex = round.id))
            }

            else -> {
                navigationRouter.forward(VoteResultsArgs(roundIdHex = round.id))
            }
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
            title = stringRes(R.string.coinVote_votingView_unverifiedPollTitle),
            message = stringRes(R.string.coinVote_votingView_unverifiedPollMessage),
            primaryAction =
                ButtonState(
                    text = stringRes(R.string.coinVote_common_goBack),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::dismissUnverifiedPollWarning
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(R.string.coinVote_pollsList_unverifiedSheetProceed),
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
