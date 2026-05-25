package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.AuthorizeVotingSubmissionUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitVotesUseCase
import co.electriccoin.zcash.ui.common.usecase.VotingAuthorizationException
import co.electriccoin.zcash.ui.common.usecase.VotingSubmissionAuthorizationResult
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingArgs
import co.electriccoin.zcash.ui.screen.voting.votingerror.VotingErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoteConfirmSubmissionVM(
    private val args: VoteConfirmSubmissionArgs,
    votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    prepareVotingRound: PrepareVotingRoundUseCase,
    private val authorizeVotingSubmission: AuthorizeVotingSubmissionUseCase,
    private val submitVotes: SubmitVotesUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val statusFlow = MutableStateFlow<VoteSubmissionStatus>(VoteSubmissionStatus.Idle)
    private val isFailureSheetVisible = MutableStateFlow(false)
    private val activeSubmissionIncludesAuthorizationProgress = MutableStateFlow<Boolean?>(null)

    private val draftChoices =
        runCatching { args.choicesJson.toDraftChoices() }
            .getOrElse { throwable ->
                Log.e("VoteConfirmSubmission", "Failed to parse draft vote choices", throwable)
                emptyMap()
            }
    private val selectedAccountUuid =
        getSelectedWalletAccount
            .observe()
            .map { account -> account?.sdkAccount?.accountUuid?.toVotingAccountScopeId() }
            .stateIn(this)
    private val isKeystoneAccount =
        getSelectedWalletAccount
            .observe()
            .map { account -> account is KeystoneAccount }
            .stateIn(viewModel = this, initialValue = false)
    private val recovery =
        selectedAccountUuid
            .filterNotNull()
            .flatMapLatest { accountUuid ->
                votingRecoveryRepository.observe(accountUuid, args.roundIdHex)
            }.stateIn(this)
    private val submissionUiState =
        combine(
            statusFlow,
            isFailureSheetVisible,
            activeSubmissionIncludesAuthorizationProgress
        ) { status, isFailureSheetVisible, activeIncludesAuthorizationProgress ->
            SubmissionUiState(
                status = status,
                isFailureSheetVisible = isFailureSheetVisible,
                activeIncludesAuthorizationProgress = activeIncludesAuthorizationProgress
            )
        }

    // Guards the cold-launch auto-resume: we only ever fire once per VM
    // instance, even if `recovery` re-emits or the snapshot updates.
    private var hasAttemptedAutoResume = false

    init {
        viewModelScope.launch {
            runCatching {
                prepareVotingRound(args.roundIdHex)
            }.onFailure { throwable ->
                Log.e(
                    "VoteConfirmSubmission",
                    "Failed to prepare voting round ${args.roundIdHex}",
                    throwable
                )
            }
        }
        if (draftChoices.isNotEmpty()) {
            viewModelScope.launch {
                runCatching {
                    val round =
                        votingApiRepository.snapshot
                            .map { snapshot -> snapshot.rounds.firstOrNull { it.id == args.roundIdHex } }
                            .filterNotNull()
                            .first()
                    persistDraftChoices(round)
                }.onFailure { throwable ->
                    Log.e(
                        "VoteConfirmSubmission",
                        "Failed to persist draft vote choices for ${args.roundIdHex}",
                        throwable
                    )
                }
            }
        }
        // Cold-launch auto-resume: if the previous app process was killed while
        // SubmitVotesUseCase was mid-flight, the recovery phase advanced past
        // HOTKEY_READY but never reached SHARES_SUBMITTED. The user lands back
        // here (today via `recoverPendingVotingRouteIfNeeded` for Keystone, or
        // by re-navigating manually for software wallets) and would otherwise
        // have to press the CTA again. Mirror iOS `.advanceAfterVote` — fire
        // `onSubmit()` automatically and let SubmitVotesUseCase's resume
        // branches pick up wherever they left off.
        viewModelScope.launch {
            autoResumeIfInterrupted()
        }
    }

    @Suppress("ReturnCount")
    private suspend fun autoResumeIfInterrupted() {
        if (hasAttemptedAutoResume) return
        if (draftChoices.isEmpty()) return
        // Wait until both the selected account and a non-null recovery
        // snapshot are loaded. We read keystone-ness from the same account
        // emission to avoid races between the two derived StateFlows.
        val account = getSelectedWalletAccount.observe().filterNotNull().first()
        val accountUuid = account.sdkAccount.accountUuid.toVotingAccountScopeId()
        val snapshot = recovery.filterNotNull().first()
        if (snapshot.accountUuid != accountUuid || snapshot.roundId != args.roundIdHex) {
            // Defensive: only auto-resume against the snapshot for this VM.
            return
        }
        if (!snapshot.phase.indicatesInterruptedSubmission()) return
        // Keystone: never auto-fire if any bundles still need signing or
        // there's a pending sign/scan request in flight. The CTA in those
        // states routes to `onStartKeystoneSigning`, not `onSubmit`.
        if (account is KeystoneAccount) {
            val preparedBundles = snapshot.bundleCount ?: 0
            val signedBundles = snapshot.keystoneBundleSignatures.size
            if (preparedBundles == 0 || signedBundles < preparedBundles) return
            if (snapshot.pendingKeystoneRequest != null) return
        }
        // Final guard: don't double-trigger if a submission has already been
        // kicked off by another path (e.g. user tapped before the recovery
        // flow latched).
        if (statusFlow.value.isInFlight()) return
        hasAttemptedAutoResume = true
        Log.i(
            "VoteConfirmSubmission",
            "Auto-resuming interrupted vote submission for round ${args.roundIdHex} " +
                "from phase ${snapshot.phase}"
        )
        onSubmit()
    }

    val state: StateFlow<LceState<VoteConfirmSubmissionState>> =
        combine(
            votingApiRepository.snapshot,
            recovery,
            isKeystoneAccount,
            submissionUiState
        ) { apiSnapshot, recovery, isKeystone, submissionUiState ->
            apiSnapshot.rounds
                .firstOrNull { round -> round.id == args.roundIdHex }
                ?.let { round ->
                    createState(
                        round = round,
                        recovery = recovery,
                        isKeystone = isKeystone,
                        status = submissionUiState.status,
                        showFailureSheet = submissionUiState.isFailureSheetVisible,
                        activeIncludesAuthorizationProgress = submissionUiState.activeIncludesAuthorizationProgress
                    )
                }
        }.map { content ->
            LceState(
                content = content,
                isLoading = content == null
            )
        }.stateIn(this)

    private fun createState(
        round: VotingRound,
        recovery: VotingRecoverySnapshot?,
        isKeystone: Boolean,
        status: VoteSubmissionStatus,
        showFailureSheet: Boolean,
        activeIncludesAuthorizationProgress: Boolean?,
    ): VoteConfirmSubmissionState {
        val isPrepared = recovery?.eligibleWeight != null && recovery.hotkeyAddress != null
        val keystoneSignedBundles = recovery?.keystoneBundleSignatures?.size ?: 0
        val preparedBundleCount = recovery?.bundleCount ?: 0
        val hasPendingKeystoneRequest = recovery?.pendingKeystoneRequest != null
        val allKeystoneBundlesSigned = preparedBundleCount > 0 && keystoneSignedBundles >= preparedBundleCount
        val isSubmitting = status.isInFlight()
        val phaseIncludesAuthorizationProgress = recovery.includesAuthorizationProgress()
        val includesAuthorizationProgress =
            if (status.isInFlight()) {
                activeIncludesAuthorizationProgress ?: phaseIncludesAuthorizationProgress
            } else {
                phaseIncludesAuthorizationProgress
            }
        val memo =
            buildMemo(
                round = round,
                recovery = recovery,
                isKeystone = isKeystone,
                isPrepared = isPrepared,
                allKeystoneBundlesSigned = allKeystoneBundlesSigned,
                hasPendingKeystoneRequest = hasPendingKeystoneRequest
            )

        return VoteConfirmSubmissionState(
            status = status,
            roundTitle = stringRes(round.title),
            votingWeightZEC =
                recovery?.eligibleWeight?.toVotingWeightLabel()?.let(::stringRes)
                    ?: stringRes(R.string.vote_confirm_preparing),
            hotkeyAddress = recovery?.hotkeyAddress?.let(::stringRes) ?: stringRes(R.string.vote_confirm_preparing),
            isKeystoneUser = isKeystone,
            includesAuthorizationProgress = includesAuthorizationProgress,
            memo = memo,
            ctaButton =
                buildButtonState(
                    isPrepared = isPrepared,
                    isKeystone = isKeystone,
                    keystoneSignedBundles = keystoneSignedBundles,
                    preparedBundleCount = preparedBundleCount,
                    hasPendingKeystoneRequest = hasPendingKeystoneRequest,
                    isSubmitting = isSubmitting,
                    status = status
                ),
            errorSheet =
                buildFailureSheet(
                    status = status,
                    isVisible = showFailureSheet,
                    canRetry = isPrepared && draftChoices.isNotEmpty(),
                    retryAction =
                        if (isKeystone && keystoneSignedBundles < preparedBundleCount) {
                            ::onStartKeystoneSigning
                        } else {
                            ::onSubmit
                        }
                ),
            onBack = ::onBack
        )
    }

    private fun buildMemo(
        round: VotingRound,
        recovery: VotingRecoverySnapshot?,
        isKeystone: Boolean,
        isPrepared: Boolean,
        allKeystoneBundlesSigned: Boolean,
        hasPendingKeystoneRequest: Boolean
    ) = when {
        isKeystone && !allKeystoneBundlesSigned -> {
            if (hasPendingKeystoneRequest) {
                stringRes(R.string.vote_confirm_memo_resume_keystone)
            } else {
                stringRes(R.string.vote_confirm_memo_sign_keystone)
            }
        }

        isPrepared -> {
            stringRes(
                R.string.vote_confirm_memo_authorize,
                round.title,
                requireNotNull(recovery?.eligibleWeight).toVotingWeightLabel()
            )
        }

        else -> {
            stringRes(R.string.vote_confirm_memo_preparing)
        }
    }

    private fun buildButtonState(
        isPrepared: Boolean,
        isKeystone: Boolean,
        keystoneSignedBundles: Int,
        preparedBundleCount: Int,
        hasPendingKeystoneRequest: Boolean,
        isSubmitting: Boolean,
        status: VoteSubmissionStatus
    ) = when {
        status is VoteSubmissionStatus.Completed -> {
            ButtonState(
                text = stringRes(R.string.vote_done),
                style = ButtonStyle.PRIMARY,
                onClick = ::onDone
            )
        }

        status.isFailure() -> {
            ButtonState(
                text = stringRes(R.string.vote_retry),
                style = ButtonStyle.PRIMARY,
                isEnabled = isPrepared && draftChoices.isNotEmpty(),
                onClick = if (isKeystone && keystoneSignedBundles < preparedBundleCount) ::onStartKeystoneSigning else ::onSubmit
            )
        }

        else -> {
            ButtonState(
                text =
                    when {
                        !isPrepared -> {
                            stringRes(R.string.vote_confirm_cta_preparing)
                        }

                        isKeystone && keystoneSignedBundles < preparedBundleCount -> {
                            if (hasPendingKeystoneRequest) {
                                stringRes(R.string.vote_confirm_cta_resume_keystone_signing)
                            } else if (keystoneSignedBundles == 0) {
                                stringRes(R.string.vote_confirm_cta_keystone)
                            } else {
                                stringRes(
                                    R.string.vote_confirm_cta_sign_bundle,
                                    keystoneSignedBundles + 1,
                                    preparedBundleCount
                                )
                            }
                        }

                        isSubmitting -> {
                            stringRes(R.string.vote_confirm_cta_submitting_generic)
                        }

                        else -> {
                            stringRes(R.string.vote_confirm_cta_submit_votes)
                        }
                    },
                style = ButtonStyle.PRIMARY,
                isEnabled = isPrepared && !isSubmitting && draftChoices.isNotEmpty(),
                onClick = if (isKeystone && keystoneSignedBundles < preparedBundleCount) ::onStartKeystoneSigning else ::onSubmit
            )
        }
    }

    private fun onStartKeystoneSigning() {
        navigationRouter.forward(SignKeystoneVotingArgs(args.roundIdHex))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun onSubmit() {
        if (draftChoices.isEmpty()) {
            setFailureStatus(
                VoteSubmissionStatus.SubmissionFailed(
                    error = null,
                    defaultError = stringRes(R.string.vote_confirm_error_no_choices)
                )
            )
            return
        }
        val previousStatus = statusFlow.value
        if (previousStatus.isInFlight()) {
            return
        }

        isFailureSheetVisible.value = false
        activeSubmissionIncludesAuthorizationProgress.value = recovery.value.includesAuthorizationProgress()
        statusFlow.value = VoteSubmissionStatus.LocalAuthorizing
        viewModelScope.launch {
            val authorizationResult =
                try {
                    authorizeVotingSubmission(isKeystone = isKeystoneAccount.value)
                } catch (throwable: CancellationException) {
                    throw throwable
                } catch (throwable: Exception) {
                    Log.e(
                        "VoteConfirmSubmission",
                        "Failed to authorize vote submission for round ${args.roundIdHex}",
                        throwable
                    )
                    setFailureStatus(
                        VoteSubmissionStatus.LocalAuthFailed(
                            throwable.message
                        )
                    )
                    return@launch
                }

            when (authorizationResult) {
                VotingSubmissionAuthorizationResult.Authorized -> {
                    Unit
                }

                VotingSubmissionAuthorizationResult.Cancelled -> {
                    statusFlow.value = previousStatus
                    activeSubmissionIncludesAuthorizationProgress.value = null
                    return@launch
                }

                VotingSubmissionAuthorizationResult.Failed -> {
                    setFailureStatus(
                        VoteSubmissionStatus.LocalAuthFailed(
                            null
                        )
                    )
                    return@launch
                }
            }

            statusFlow.value = VoteSubmissionStatus.Authorizing(progress = 0f)
            try {
                submitVotes(args.roundIdHex, draftChoices, ::onSubmissionProgress)
                statusFlow.value = VoteSubmissionStatus.Completed
                activeSubmissionIncludesAuthorizationProgress.value = null
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                Log.e(
                    "VoteConfirmSubmission",
                    "Failed to submit votes for round ${args.roundIdHex}",
                    throwable
                )
                val error = throwable.message ?: "Vote submission failed."
                setFailureStatus(
                    when (throwable) {
                        is VotingAuthorizationException -> VoteSubmissionStatus.ProtocolAuthFailed(error)
                        else -> VoteSubmissionStatus.SubmissionFailed(error)
                    }
                )
            }
        }
    }

    private fun onSubmissionProgress(progress: VotingSubmissionProgress) {
        statusFlow.value =
            when (progress) {
                is VotingSubmissionProgress.Authorizing -> {
                    VoteSubmissionStatus.Authorizing(progress.progress)
                }

                is VotingSubmissionProgress.Submitting -> {
                    VoteSubmissionStatus.Submitting(
                        current = progress.current,
                        total = progress.total,
                        progress = progress.progress
                    )
                }
            }
    }

    private suspend fun persistDraftChoices(round: VotingRound) {
        val accountUuid = selectedAccountUuid.value ?: return
        val persistedDraftChoices =
            draftChoices
                .filterKeys { proposalId -> round.proposals.any { proposal -> proposal.id == proposalId } }
        if (persistedDraftChoices.isNotEmpty()) {
            votingRecoveryRepository.storeDraftChoices(accountUuid, args.roundIdHex, persistedDraftChoices)
        }
    }

    private fun onDone() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            val recovery = votingRecoveryRepository.get(accountUuid, args.roundIdHex)
            val persistedDraftChoices = recovery?.draftChoices?.ifEmpty { draftChoices } ?: draftChoices
            val submittedChoices =
                recovery
                    ?.proposalSelections
                    ?.mapValues { (_, selection) -> selection.choiceId }
                    .orEmpty()
            val persistedChoices = persistedDraftChoices + submittedChoices
            if (persistedChoices.isNotEmpty()) {
                votingSessionStore.restoreDraftVotes(accountUuid, args.roundIdHex, persistedChoices)
            }
            navigationRouter.replace(
                VoteProposalListArgs(
                    roundId = args.roundIdHex,
                    mode = VoteProposalListMode.VOTED
                )
            )
        }
    }

    private fun onBack() {
        when {
            statusFlow.value.isInFlight() -> Unit
            else -> navigationRouter.back()
        }
    }

    private fun buildFailureSheet(
        status: VoteSubmissionStatus,
        isVisible: Boolean,
        canRetry: Boolean,
        retryAction: () -> Unit,
    ): ZashiConfirmationState? {
        if (!isVisible || !status.isFailure()) {
            return null
        }
        return ZashiConfirmationState.error(
            title = failureTitle(status),
            message = failureMessage(status),
            primaryText = stringRes(if (canRetry) R.string.vote_retry else R.string.vote_dismiss),
            secondaryText = stringRes(R.string.vote_dismiss),
            primaryStyle = ButtonStyle.PRIMARY,
            onPrimary = {
                isFailureSheetVisible.value = false
                if (canRetry) {
                    retryAction()
                }
            },
            onSecondary = ::dismissFailureSheet,
            onBack = ::dismissFailureSheet,
        )
    }

    private fun failureTitle(status: VoteSubmissionStatus) =
        when (status) {
            is VoteSubmissionStatus.LocalAuthFailed -> stringRes(R.string.vote_confirm_title_auth_failed)
            is VoteSubmissionStatus.ProtocolAuthFailed -> stringRes(R.string.vote_error_authorization_failed_title)
            is VoteSubmissionStatus.SubmissionFailed -> stringRes(R.string.vote_confirm_title_failed)
            else -> stringRes(R.string.vote_error_something_went_wrong)
        }

    private fun failureMessage(status: VoteSubmissionStatus) =
        when (status) {
            is VoteSubmissionStatus.LocalAuthFailed -> {
                stringRes(R.string.vote_error_authorization_failed_message)
            }

            is VoteSubmissionStatus.ProtocolAuthFailed -> {
                stringRes(R.string.vote_error_authorization_failed_message)
            }

            is VoteSubmissionStatus.SubmissionFailed -> {
                status.error.toErrorMessageOrDefault(
                    status.defaultError ?: stringRes(R.string.vote_confirm_error_submission)
                )
            }

            else -> {
                stringRes(R.string.vote_error_something_went_wrong_message)
            }
        }

    private fun setFailureStatus(status: VoteSubmissionStatus) {
        statusFlow.value = status
        activeSubmissionIncludesAuthorizationProgress.value = null
        isFailureSheetVisible.value = true
    }

    private fun dismissFailureSheet() {
        isFailureSheetVisible.value = false
    }
}

private fun VotingRecoverySnapshot?.includesAuthorizationProgress(): Boolean =
    this?.phase?.includesAuthorizationProgress() ?: true

private fun VotingRecoveryPhase.includesAuthorizationProgress(): Boolean =
    when (this) {
        VotingRecoveryPhase.INITIALIZED,
        VotingRecoveryPhase.BUNDLES_PREPARED,
        VotingRecoveryPhase.HOTKEY_READY,
        VotingRecoveryPhase.DELEGATION_PROVED -> true

        VotingRecoveryPhase.DELEGATION_SUBMITTED,
        VotingRecoveryPhase.VOTES_SUBMITTED,
        VotingRecoveryPhase.SHARES_SUBMITTED -> false
    }

/**
 * Recovery phases that mean: SubmitVotesUseCase was running when the process
 * was killed. Phases up to HOTKEY_READY can be reached purely by preparation
 * (no user CTA yet), so they're treated as "fresh" here and require the user
 * to tap. SHARES_SUBMITTED is fully complete — no resume needed. Anything in
 * between is in-flight and safe to auto-resume; the use-case's resume branches
 * (cached delegation tx hashes, `submittedProposalIds`, etc.) skip already-
 * completed work idempotently.
 */
private fun VotingRecoveryPhase.indicatesInterruptedSubmission(): Boolean =
    when (this) {
        VotingRecoveryPhase.INITIALIZED,
        VotingRecoveryPhase.BUNDLES_PREPARED,
        VotingRecoveryPhase.HOTKEY_READY,
        VotingRecoveryPhase.SHARES_SUBMITTED -> false

        VotingRecoveryPhase.DELEGATION_PROVED,
        VotingRecoveryPhase.DELEGATION_SUBMITTED,
        VotingRecoveryPhase.VOTES_SUBMITTED -> true
    }

private data class SubmissionUiState(
    val status: VoteSubmissionStatus,
    val isFailureSheetVisible: Boolean,
    val activeIncludesAuthorizationProgress: Boolean?
)

private fun Long.toVotingWeightLabel() = "%.4f ZEC".format(this / 100_000_000.0)

private fun String?.toErrorMessageOrDefault(default: StringResource): StringResource =
    if (isNullOrBlank()) {
        default
    } else {
        VotingErrorMapper.toUserFriendlyMessage(this)
    }

private fun String.toDraftChoices(): Map<Int, Int> {
    val json = JSONObject(this)
    return buildMap {
        json.keys().forEach { key ->
            put(key.toInt(), json.getInt(key))
        }
    }
}
