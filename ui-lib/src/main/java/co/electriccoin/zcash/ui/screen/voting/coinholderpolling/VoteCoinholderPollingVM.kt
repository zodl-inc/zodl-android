package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.Lce
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiSnapshot
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.effectiveChoices
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.chainconfig.VoteChainConfigArgs
import co.electriccoin.zcash.ui.screen.voting.normalizedVotingRoundIds
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import co.electriccoin.zcash.ui.screen.voting.VoteTrustIndicator
import co.electriccoin.zcash.ui.screen.voting.voteTrustIndicatorFor
import co.electriccoin.zcash.ui.screen.voting.votingerror.VotingErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteCoinholderPollingVM(
    private val refreshActiveVotingSession: RefreshActiveVotingSessionUseCase,
    private val refreshVotingRounds: RefreshVotingRoundsUseCase,
    private val configurationRepository: ConfigurationRepository,
    private val votingChainConfigRepository: VotingChainConfigRepository,
    private val votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
) : ViewModel() {
    private val roundsLce =
        mutableLce<List<VotingRound>>(
            votingApiRepository.snapshot.value.rounds
                .takeIf { it.isNotEmpty() }
                ?.let { rounds -> Lce(content = LceContent.Success(rounds)) }
        )
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
        refreshVotingData()
        startVotingDataAutoRefresh()
    }

    private val apiSnapshotWithConfig =
        combine(
            votingApiRepository.snapshot,
            votingChainConfigRepository.state,
            configurationRepository.configurationFlow
        ) { apiSnapshot, chainConfig, configuration ->
            ApiSnapshotWithConfig(
                apiSnapshot = apiSnapshot,
                isOnDefaultConfig =
                    chainConfig.isOnDefaultConfig &&
                        configuration
                            ?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue)
                            .orEmpty()
                            .isBlank()
            )
        }

    val state =
        combine(
            apiSnapshotWithConfig,
            roundsLce.state,
            recoveryVoteCounts,
            votingSessionStore.state,
            selectedAccountUuid,
        ) { apiSnapshotWithConfig, roundsLceState, persistedVoteCounts, sessionState, accountUuid ->
            val apiSnapshot = apiSnapshotWithConfig.apiSnapshot
            val rounds = when {
                apiSnapshot.rounds.isNotEmpty() -> apiSnapshot.rounds
                roundsLceState.content is LceContent.Success -> emptyList()
                else -> null
            }
            val currentAccountUuid = accountUuid ?: return@combine null

            rounds?.let {
                val normalizedEndorsedRoundIds = apiSnapshot.zodlEndorsedRoundIds.normalizedVotingRoundIds()
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
            combine(contentFlow, configErrorSheet, unverifiedPollWarningSheet) { content, configSheet, unverifiedSheet ->
                content?.copy(
                    configErrorSheet = configSheet,
                    unverifiedPollWarningSheet = unverifiedSheet
                )
            }
        }.withLce(groupLce(roundsLce)) { error ->
            errorStateMapper.mapToState(
                error = error,
                title = stringRes(R.string.vote_error_unable_to_load_polls_title),
                message = stringRes(R.string.vote_error_unable_to_load_polls_message),
                primaryStyle = ButtonStyle.PRIMARY
            )
        }.stateIn(this)

    private fun buildCard(
        round: VotingRound,
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
                        refreshVotingData()
                    }
                }
        }
    }

    private suspend fun refreshVotingDataInternal(resetVisibleConfigError: Boolean): List<VotingRound> {
        if (resetVisibleConfigError) {
            configIssue = null
            configErrorSheet.value = null
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
        return votingApiRepository.snapshot.value.rounds
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
        votingChainConfigRepository.state.value.isOnDefaultConfig &&
            configurationRepository.configurationFlow.value
                ?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue)
                .orEmpty()
                .isBlank()

    private fun buildUnverifiedPollWarningSheet() =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = stringRes(R.string.vote_unverified_poll_title),
            message = stringRes(R.string.vote_unverified_poll_message),
            primaryAction = ButtonState(
                text = stringRes(R.string.vote_continue),
                style = ButtonStyle.PRIMARY,
                onClick = ::proceedFromUnverifiedPollWarning
            ),
            secondaryAction = ButtonState(
                text = stringRes(R.string.vote_error_go_back),
                style = ButtonStyle.TERTIARY,
                onClick = ::dismissUnverifiedPollWarning
            ),
            onBack = ::dismissUnverifiedPollWarning
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
    }
}

private data class ApiSnapshotWithConfig(
    val apiSnapshot: VotingApiSnapshot,
    val isOnDefaultConfig: Boolean
)

private data class PendingRoundSelection(
    val round: VotingRound,
    val status: VotePollCardStatus
)
