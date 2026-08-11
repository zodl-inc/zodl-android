package co.electriccoin.zcash.ui.screen.migration.review

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.migration.migrationLog
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.ZashiSpendingKeyDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.migration.MigrationKeystoneRound
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationDetails
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationStepDetail
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState
import co.electriccoin.zcash.ui.common.model.migration.estimatedSecondsBetweenHeights
import co.electriccoin.zcash.ui.common.model.migration.formatMigrationDuration
import co.electriccoin.zcash.ui.common.model.migration.migrationFailureMessage
import co.electriccoin.zcash.ui.common.model.migration.preparationStepStatus
import co.electriccoin.zcash.ui.common.model.migration.preparationStepTimeLabel
import co.electriccoin.zcash.ui.common.model.migration.preparationStepTitle
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignArgs
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledArgs
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import co.electriccoin.zcash.ui.design.R as DesignR

// Protocol target block interval in seconds, used as the fallback until a measured rate is
// captured at propose time.
private const val DEFAULT_SECONDS_PER_BLOCK = 75L

class MigrationReviewVM(
    private val args: MigrationReviewArgs,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val pendingMigrationScheduleRepository: PendingMigrationScheduleRepository,
    private val restartMigrationScheduleRepository: RestartMigrationScheduleRepository,
    private val finalizeMigrationSchedule: FinalizeMigrationScheduleUseCase,
    private val navigationRouter: NavigationRouter,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getOrchardBalance: GetOrchardBalanceUseCase,
    private val errorStateMapper: ErrorMapperUseCase,
    private val zashiSpendingKeyDataSource: ZashiSpendingKeyDataSource,
    private val biometricRepository: BiometricRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val submitProposal: SubmitProposalUseCase,
    private val synchronizerProvider: SynchronizerProvider,
) : ViewModel() {
    // proposeImmediateMigration() now returns an ordinary send-max Proposal (bypassing the
    // migration engine entirely — see OrchardMigrationSdk's kdoc), which carries no amount or
    // destination of its own (only totalFeeRequired()/transactionCount()); the amount shown is
    // this account's Orchard balance at propose time (the whole point of a send-max sweep).
    private sealed class ReviewProposal {
        data class Automatic(
            val schedule: MigrationSchedule,
            val keystoneRunCount: Int?
        ) : ReviewProposal()

        data class Immediate(
            val proposal: Proposal,
            val amountZatoshi: Zatoshi
        ) : ReviewProposal()
    }

    // Measured block rate captured at propose time; 75s until then. Drives every
    // height-to-time label on this screen (bursty testnet vs the protocol constant).
    @Volatile
    private var secondsPerBlock: Long = DEFAULT_SECONDS_PER_BLOCK

    private val proposeLce = mutableLce<ReviewProposal>()
    private val confirmLce = mutableLce<Unit>()
    private val isKeystoneAccount = getSelectedWalletAccount.observe().map { it is KeystoneAccount }
    private val failure = MutableStateFlow<TransferResult?>(null)

    init {
        proposeLce.execute {
            // Timed, bracketed logging (2026-08-07 Review-screen slow-load investigation): pairs
            // with the MIGRATION_DB_ACCESS_MUTEX queued/acquired/released lines OrchardMigrationSdkImpl.logged()
            // now emits, so a slow Review load is attributable to a SPECIFIC call, and — via the
            // matching SDK-side lines — to either real mutex contention (queued behind a named
            // operation) or the Rust computation itself simply taking that long.
            val initStartMs = SystemClock.elapsedRealtime()
            migrationLog("MIGRATION_DIAG ReviewVM: propose start (mode=${args.mode})")
            val sdk = getOrchardMigrationSdk()
            val result =
                when (args.mode) {
                    MigrationMode.IMMEDIATE -> {
                        val amount = getOrchardBalance()
                        val t0 = SystemClock.elapsedRealtime()
                        migrationLog("MIGRATION_DIAG ReviewVM: proposeImmediateMigration start")
                        val proposal = sdk.proposeImmediateMigration()
                        migrationLog(
                            "MIGRATION_DIAG ReviewVM: proposeImmediateMigration done in " +
                                "${SystemClock.elapsedRealtime() - t0}ms"
                        )
                        ReviewProposal.Immediate(proposal, amount)
                    }

                    MigrationMode.AUTOMATIC -> {
                        // If MigrationTransferInvalidVM.onContinue() already obtained a fresh schedule
                        // via restartCurrentMigrationStep() — whose own doc requires that returned
                        // schedule to go through this normal confirmation flow rather than being
                        // silently re-proposed — reuse that exact schedule instead of calling
                        // proposeMigrationTransfers() again (see RestartMigrationScheduleRepository's
                        // doc: the two calls compute independent guesses over the same balance that
                        // aren't guaranteed to agree). Falls back to a fresh proposal for every
                        // ordinary, non-recovery entry into this screen.
                        val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
                        val reused = restartMigrationScheduleRepository.consume(accountKeyId)
                        val schedule =
                            reused ?: run {
                                val t0 = SystemClock.elapsedRealtime()
                                migrationLog("MIGRATION_DIAG ReviewVM: proposeMigrationTransfers start")
                                val s = sdk.proposeMigrationTransfers()
                                migrationLog(
                                    "MIGRATION_DIAG ReviewVM: proposeMigrationTransfers done in " +
                                        "${SystemClock.elapsedRealtime() - t0}ms"
                                )
                                s
                            }
                        // IMMEDIATE has no Keystone branch at all (a documented pre-existing gap —
                        // see MigrationReviewVM.confirmAutomatic()'s Keystone check below), so round
                        // display is AUTOMATIC-only. Stateless preview, called fresh on every Review
                        // entry — never cached.
                        val keystoneRunCount =
                            if (getSelectedWalletAccount() is KeystoneAccount) {
                                val t0 = SystemClock.elapsedRealtime()
                                migrationLog("MIGRATION_DIAG ReviewVM: estimateMigrationRunCount start")
                                val count = sdk.estimateMigrationRunCount()
                                migrationLog(
                                    "MIGRATION_DIAG ReviewVM: estimateMigrationRunCount done in " +
                                        "${SystemClock.elapsedRealtime() - t0}ms"
                                )
                                count
                            } else {
                                null
                            }
                        val t0 = SystemClock.elapsedRealtime()
                        secondsPerBlock = sdk.estimatedSecondsPerBlock()
                        migrationLog(
                            "MIGRATION_DIAG ReviewVM: estimatedSecondsPerBlock done in " +
                                "${SystemClock.elapsedRealtime() - t0}ms"
                        )
                        logProposedPlan(schedule)
                        ReviewProposal.Automatic(schedule, keystoneRunCount)
                    }
                }
            migrationLog(
                "MIGRATION_DIAG ReviewVM: propose done in ${SystemClock.elapsedRealtime() - initStartMs}ms total"
            )
            result
        }
    }

    val state: StateFlow<LceState<MigrationReviewState>> =
        combine(
            proposeLce.state,
            exchangeRateRepository.state,
            isKeystoneAccount,
            failure,
            confirmLce.state
        ) { lce, rate, isKeystone, f, confirmState ->
            lce.success?.let { proposal -> createState(proposal, confirmState.loading, rate, isKeystone, f) }
        }.withLce(groupLce(proposeLce, confirmLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        proposal: ReviewProposal,
        isConfirming: Boolean,
        exchangeRateState: ExchangeRateState,
        isKeystone: Boolean,
        failureResult: TransferResult?,
    ): MigrationReviewState =
        when (proposal) {
            is ReviewProposal.Automatic -> {
                createAutomaticState(proposal, isConfirming, exchangeRateState, isKeystone, failureResult)
            }

            is ReviewProposal.Immediate -> {
                createImmediateState(proposal, isConfirming, exchangeRateState)
            }
        }

    private fun createAutomaticState(
        proposal: ReviewProposal.Automatic,
        isConfirming: Boolean,
        exchangeRateState: ExchangeRateState,
        isKeystone: Boolean,
        failureResult: TransferResult?,
    ): MigrationReviewState {
        val sched = proposal.schedule
        val total = sched.transfers.sumOf { it.amountZatoshi }
        // From the plan's "now" reference (anchorHeight — every transfer shares the same plan-time
        // tip) to the LAST transfer's height, matching scheduledLabel()'s per-transfer calculation
        // below and MigrationScheduledVM/MigrationProgressVM's createdAt-to-last-scheduled span —
        // NOT firstAtHeight-to-lastAtHeight, which omits the wait before the first transfer and
        // previously made this summary disagree with the per-transfer rows and the other two
        // migration screens (confirmed live: header claimed a shorter span than the last
        // transfer's own "due in ~Nh" label showed).
        val anchorHeight = sched.transfers.minOfOrNull { it.anchorHeight } ?: 0L
        val lastAtHeight = sched.transfers.maxOfOrNull { it.nextExecutableAfterHeight } ?: 0L
        val spanSeconds = estimatedSecondsBetweenHeights(anchorHeight, lastAtHeight, secondsPerBlock)
        val totalAmount = stringRes(Zatoshi(total))
        return MigrationReviewState(
            mode = args.mode,
            totalAmount = totalAmount,
            totalFiatAmount = fiatAmount(Zatoshi(total), exchangeRateState),
            estimatedDuration = stringRes(formatMigrationDuration(spanSeconds)),
            preparations =
                if (sched.preparations.size > 1) {
                    emptyList()
                } else {
                    sched.preparations.mapIndexed { i, p ->
                        MigrationReviewPreparationState(
                            number = i + 1,
                            scheduledLabel = scheduledLabelForPrep(p, sched)
                        )
                    }
                },
            preparationsSummarySubtitle = preparationsSummarySubtitle(sched),
            preparationDetails = preparationDetails(sched, totalAmount),
            transfers =
                sched.transfers.mapIndexed { i, t ->
                    MigrationReviewTransferState(
                        index = i + 1,
                        totalCount = sched.transfers.size,
                        amount = stringRes(Zatoshi(t.amountZatoshi)),
                        fiatAmount = fiatAmount(Zatoshi(t.amountZatoshi), exchangeRateState),
                        scheduledLabel = scheduledLabel(t),
                    )
                },
            isKeystone = isKeystone,
            keystoneRound =
                proposal.keystoneRunCount?.takeIf { it > 1 }?.let { MigrationKeystoneRound(current = 1, total = it) },
            isConfirming = isConfirming,
            onConfirm = { proposeLce.guardLoading { onConfirmAutomatic(sched) } },
            onBack = ::onBack,
            failureSheet =
                failureResult?.let {
                    MigrationTransferFailureState(
                        message = migrationFailureMessage(it),
                        onRetry = {
                            failure.value = null
                            proposeLce.guardLoading { onConfirmAutomatic(sched) }
                        },
                        onDismiss = { failure.value = null },
                    )
                },
        )
    }

    // proposeImmediateMigration()'s raw send-max Proposal carries no destination-facing
    // "list of transfers" the way a MigrationSchedule does — this renders it as a single
    // synthetic row so the (shared) review layout still has something to show, using the real
    // fee from Proposal.totalFeeRequired() instead of AUTOMATIC's placeholder.
    private fun createImmediateState(
        proposal: ReviewProposal.Immediate,
        isConfirming: Boolean,
        exchangeRateState: ExchangeRateState,
    ): MigrationReviewState {
        val fee = proposal.proposal.totalFeeRequired()
        return MigrationReviewState(
            mode = args.mode,
            totalAmount = stringRes(proposal.amountZatoshi),
            totalFiatAmount = fiatAmount(proposal.amountZatoshi, exchangeRateState),
            estimatedDuration = stringRes(formatMigrationDuration(0L)),
            transfers =
                listOf(
                    MigrationReviewTransferState(
                        index = 1,
                        totalCount = 1,
                        amount = stringRes(proposal.amountZatoshi),
                        fiatAmount = fiatAmount(proposal.amountZatoshi, exchangeRateState),
                        scheduledLabel = stringRes(DesignR.string.migrationReview_sendImmediately),
                    )
                ),
            fee = stringRes(fee),
            isConfirming = isConfirming,
            onConfirm = { onConfirmImmediate(proposal.proposal, proposal.amountZatoshi) },
            onBack = ::onBack,
            // Submit failures now surface on the Sending screen (which owns the broadcast) rather
            // than here — this screen only hands the signed proposal off after biometric auth.
            failureSheet = null,
        )
    }

    private fun fiatAmount(zatoshi: Zatoshi, exchangeRateState: ExchangeRateState): StringResource? {
        val data = exchangeRateState as? ExchangeRateState.Data ?: return null
        val conversion = data.currencyConversion ?: return null
        return stringResByDynamicCurrencyNumber(
            amount =
                zatoshi
                    .convertZatoshiToZec()
                    .multiply(BigDecimal(conversion.priceOfZec), MathContext.DECIMAL128),
            ticker = data.expectedCurrency.symbol,
        )
    }

    private fun onConfirmAutomatic(sched: MigrationSchedule) =
        confirmLce.execute {
            try {
                biometricRepository.requestBiometrics(
                    request =
                        BiometricRequest(
                            message =
                                stringRes(
                                    R.string.authentication_system_ui_subtitle,
                                    stringRes(R.string.authentication_use_case_send_funds)
                                )
                        )
                )
            } catch (_: BiometricsFailureException) {
                return@execute
            } catch (_: BiometricsCancelledException) {
                return@execute
            }
            confirmAutomatic(sched)
        }

    private suspend fun confirmAutomatic(sched: MigrationSchedule) {
        if (getSelectedWalletAccount() is KeystoneAccount) {
            // Keystone can't sign in-process — hand the unsigned schedule off to the QR
            // sign/scan detour; FinalizeMigrationScheduleUseCase runs after a successful scan
            // instead (MigrationKeystoneScanVM), not here.
            val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
            pendingMigrationScheduleRepository.set(accountKeyId, sched)
            navigationRouter.forward(MigrationKeystoneSignArgs(mode = args.mode))
            return
        }
        val sdk = getOrchardMigrationSdk()
        // Note-split is the first step of this confirm action (design spec §7) — a schedule with
        // more than one denomination proposed against raw, unsplit notes exhausts the wallet's
        // balance on the first transfer, leaving every subsequent transfer InsufficientFunds. Per
        // spec §3 the split is a fully shielded self-send and needs no sync-decoupling delay, so
        // proceeding straight to signAndStoreMigrationSchedule below is safe. Under the crate's
        // sign-now/prove-later pipeline that call now signs successfully immediately even though
        // the split's own output isn't mined/witnessed yet.
        //
        // `sched` was proposed at screen init, before any split — proposeMigrationTransfers()'s
        // denomination guess and prepareNoteSplit()'s own (independent) guess over the same
        // balance are not guaranteed to agree. Reusing the stale `sched` here could schedule a
        // transfer for a denomination the split never actually mints, which then silently falls
        // back to an unrelated already-existing note — one the split's own "sweep everything"
        // construction may already be consuming as one of its own inputs (a real double-spend
        // found live on testnet). Re-deriving the schedule from the split's own realized output
        // plan makes every crossing value provably match a note this split actually produces.
        val scheduleToSign =
            if (sdk.isNoteSplitNeeded()) {
                val proposal = sdk.prepareNoteSplit()
                // Derive the from-split schedule BEFORE submitting the split, not after: submitNoteSplit()
                // signs the split through the SDK's commit_or_reuse, which clears the in-memory
                // migration-plan cache that `proposal.proposalHandle` points at once it commits. Calling
                // proposeMigrationTransfersFromSplit() afterwards then throws "No pending migration
                // proposal for this account — call propose/prepare first", because the plan the handle
                // identifies is already gone. Reading the schedule first (the cache is still populated by
                // prepareNoteSplit()) mirrors MigrationKeystoneSignVM, which likewise derives the
                // from-split schedule before its first commit.
                val scheduleFromSplit = sdk.proposeMigrationTransfersFromSplit(proposal)
                // No write-ahead persistence needed anymore: the engine's own committed state IS
                // the recovery signal — if the app dies between the commit below and finalize,
                // re-entry sees engine InProgress and every screen renders live from it.
                val splitResult = sdk.submitNoteSplit(proposal, zashiSpendingKeyDataSource.getZashiSpendingKey())
                if (splitResult !is TransferResult.Success) {
                    failure.value = splitResult
                    return
                }
                // Classify zip318_kind (PREPARATION) immediately — this broadcast's raw bytes are
                // already stored locally, so without this the normal enhancement queue would skip
                // it forever and the Activity row would stay "Sent" instead of "Note split"/
                // "Migrated". See MigrationDriveOnce.handleExecuted's identical call for transfers.
                synchronizerProvider.getSynchronizerOrNull()?.enhanceTransaction(splitResult.txId)
                scheduleFromSplit
            } else {
                sched
            }
        try {
            sdk.signAndStoreMigrationSchedule(scheduleToSign, zashiSpendingKeyDataSource.getZashiSpendingKey())
            // startLiveDriverImmediately=false (MOB-1669): matches iOS's commitSoftware, which
            // also never eagerly starts a drive loop right after signAndStoreMigrationSchedule —
            // see FinalizeMigrationScheduleUseCase's doc.
            finalizeMigrationSchedule(scheduleToSign, args.mode, startLiveDriverImmediately = false)
            navigationRouter.forward(MigrationScheduledArgs)
        } catch (e: RuntimeException) {
            val retryable =
                e.message?.contains("StalePlan") == true ||
                    e.message?.contains("BoundaryCheckpointMissing") == true
            if (!retryable) throw e
            // StalePlan: the plan is a planning-time snapshot of wallet note indices; any note
            // received or changed between this screen's propose and the commit (the bursty
            // testnet syncs continuously) shifts them and the engine correctly refuses with
            // "must be re-planned". Same balance, fresh draw — re-propose once and commit that.
            // Retrying the SAME cached schedule can never succeed (observed live: six identical
            // StalePlan failures from the retry button). BoundaryCheckpointMissing: the commit
            // drew an anchor boundary onto a grid height with no retained checkpoint (pre-
            // always-on-retention scan history) — a fresh draw lands on retained boundaries.
            migrationLog("MigrationReview: StalePlan on commit — re-proposing once and retrying")
            val fresh = sdk.proposeMigrationTransfers()
            sdk.signAndStoreMigrationSchedule(fresh, zashiSpendingKeyDataSource.getZashiSpendingKey())
            finalizeMigrationSchedule(fresh, args.mode, startLiveDriverImmediately = false)
            navigationRouter.forward(MigrationScheduledArgs)
        }
    }

    // The IMMEDIATE send-max sweep is, from the wallet's point of view, an ordinary send — so it
    // reuses the exact same submit pipeline every other send does: adopt the proposal as the current
    // MigrationSweepTransactionProposal, then hand off to SubmitProposalUseCase (biometrics + async
    // broadcast + Transaction Progress screen, whose sending/success states already render the
    // migration-sweep "…migrated to Ironwood" copy). No migration-specific screen or handoff.
    private fun onConfirmImmediate(proposal: Proposal, amountZatoshi: Zatoshi) =
        confirmLce.execute {
            if (getSelectedWalletAccount() is KeystoneAccount) {
                // Keystone can't sign in-process — adopt the already-built send-max proposal into the
                // app's existing generic external-signer pipeline exactly as an ordinary Keystone
                // send does (one ordinary PCZT, same as any regular Keystone send). Biometrics are
                // requested here because the Keystone branch skips SubmitProposalUseCase (which owns
                // biometrics for the Zashi path) in favour of the QR sign/scan detour.
                try {
                    biometricRepository.requestBiometrics(
                        request =
                            BiometricRequest(
                                message =
                                    stringRes(
                                        R.string.authentication_system_ui_subtitle,
                                        stringRes(R.string.authentication_use_case_send_funds)
                                    )
                            )
                    )
                } catch (_: BiometricsFailureException) {
                    return@execute
                } catch (_: BiometricsCancelledException) {
                    return@execute
                }
                keystoneProposalRepository.setMigrationSweepProposal(proposal, amountZatoshi)
                // Required before navigating — SignKeystoneTransactionVM's QR encoder is built from
                // the already-created PCZT (createPCZTEncoder() reads KeystoneProposalRepository's
                // cached proposalPczt); it never calls createPCZTFromProposal() itself.
                keystoneProposalRepository.createPCZTFromProposal()
                navigationRouter.forward(SignKeystoneTransactionArgs)
            } else {
                zashiProposalRepository.setMigrationSweepProposal(proposal, amountZatoshi)
                submitProposal()
            }
        }

    private fun onBack() = proposeLce.guardLoading { navigationRouter.back() }

    // Public, always-reachable fallback for the screen's hoisted BackHandler — unlike onBack()
    // above, this is NOT gated behind proposeLce.guardLoading. onBack() is only reachable via
    // state.content (the LCE success case); when propose fails permanently (e.g. NothingToMigrate
    // once migration is already complete — see the 2026-08-02 stale-banner/dead-end bug) content
    // stays null forever and there is no in-flight propose operation left to guard, so this must
    // navigate back unconditionally.
    fun navigateBack() = navigationRouter.back()

    // Only ever called for AUTOMATIC (createImmediateState hardcodes its own single-row label
    // instead — a raw send-max Proposal carries no per-transfer schedule to derive one from).

    /** One-shot plan dump at propose time: absolute heights + wall-clock estimates. */
    private fun logProposedPlan(sched: MigrationSchedule) {
        // `anchorHeight` on a PROPOSED transfer is NOT a real commitment-tree anchor — the engine
        // draws anchor boundaries only at COMMIT (commit_preparation), so a proposal carries none.
        // The field holds the plan-time tip as a "now" reference for the height→time estimates
        // below; it is deliberately NOT logged per-transfer as "anchor=" (that read as a real
        // boundary and was misleading). The real per-transfer boundaries are logged post-commit by
        // the Rust `committedPlan:` dump (boundary=Some(...)).
        val referenceTip = sched.transfers.minOfOrNull { it.anchorHeight } ?: return
        migrationLog(
            buildString {
                appendLine(
                    "Plan: ${sched.transfers.size} transfer(s), referenceTip=$referenceTip " +
                        "(anchors are drawn at commit — see committedPlan; times estimated at measured " +
                        "${secondsPerBlock}s/block from the reference tip)"
                )
                var prev = referenceTip
                sched.transfers.forEachIndexed { i, t ->
                    val fromNow =
                        estimatedSecondsBetweenHeights(referenceTip, t.nextExecutableAfterHeight, secondsPerBlock)
                    val gap = estimatedSecondsBetweenHeights(prev, t.nextExecutableAfterHeight, secondsPerBlock)
                    prev = t.nextExecutableAfterHeight
                    appendLine(
                        "MIGRATION_DIAG Plan: transfer[${i + 1}] " +
                            "send=${t.nextExecutableAfterHeight} expiry=${t.expiryHeight} " +
                            "dueIn=${formatMigrationDuration(fromNow, fineGrained = true)} " +
                            "gapFromPrev=${formatMigrationDuration(gap, fineGrained = true)}"
                    )
                }
            }.trimEnd()
        )
    }

    private fun scheduledLabel(t: TransferProposal): StringResource {
        val secondsUntil = estimatedSecondsBetweenHeights(t.anchorHeight, t.nextExecutableAfterHeight, secondsPerBlock)
        return when {
            secondsUntil <= 0 -> stringRes("Ready now")

            // Shares formatMigrationDuration's resolution rules (minute-level on testnet).
            else -> stringRes(formatMigrationDuration(secondsUntil))
        }
    }

    // Preparations carry no per-item anchorHeight; use the transfers' commit-tip baseline
    // (same origin the transfer labels use).
    private fun scheduledLabelForPrep(
        p: cash.z.ecc.android.sdk.PreparationStep,
        sched: MigrationSchedule,
    ): StringResource {
        val baseline = sched.transfers.minOfOrNull { it.anchorHeight } ?: p.broadcastHeight
        val secondsUntil = estimatedSecondsBetweenHeights(baseline, p.broadcastHeight, secondsPerBlock)
        return if (secondsUntil <= 0) stringRes("Ready now") else stringRes(formatMigrationDuration(secondsUntil))
    }

    // "in ~X hours · N steps" — the collapsed "Split Balance" row's subtitle (Figma "PR App
    // Designs Q3'26" node 5207:16023, 2026-08-03). Only non-null with more than one preparation;
    // unlike scheduledLabelForPrep, this never says "Ready now" — the sheet's own per-row time
    // column (see preparationStepTimeLabel) always reads a relative estimate, never a status word.
    private fun preparationsSummarySubtitle(sched: MigrationSchedule): StringResource? {
        val preparations = sched.preparations
        if (preparations.size <= 1) return null
        val first = preparations.minByOrNull { it.broadcastHeight } ?: return null
        val baseline = sched.transfers.minOfOrNull { it.anchorHeight } ?: first.broadcastHeight
        val secondsUntil = estimatedSecondsBetweenHeights(baseline, first.broadcastHeight, secondsPerBlock)
        return preparationStepTimeLabel(secondsUntil) + stringRes(" · ${preparations.size} steps")
    }

    // The "Show details" sheet's full per-step breakdown — see MigrationPreparationDetails' doc.
    // Only non-null alongside [preparationsSummarySubtitle] (more than one preparation). Nothing
    // has been sent yet at this pre-confirm stage, so isSent/isAwaitingSignature are always false —
    // only a step's own dependencies or timing can produce a non-"Ready to send" status here.
    private fun preparationDetails(
        sched: MigrationSchedule,
        totalAmount: StringResource,
    ): MigrationPreparationDetails? {
        val preparations = sched.preparations
        if (preparations.size <= 1) return null
        val baseline = sched.transfers.minOfOrNull { it.anchorHeight } ?: return null
        val numberById = preparations.mapIndexed { i, p -> p.id to (i + 1) }.toMap()
        return MigrationPreparationDetails(
            stepCount = preparations.size,
            totalAmount = totalAmount,
            steps =
                preparations.mapIndexed { i, p ->
                    val dependsOnNumbers = p.dependsOn.mapNotNull { numberById[it] }.sorted()
                    val secondsUntil = estimatedSecondsBetweenHeights(baseline, p.broadcastHeight, secondsPerBlock)
                    MigrationPreparationStepDetail(
                        title = preparationStepTitle(i + 1, preparations.size),
                        timeLabel = preparationStepTimeLabel(secondsUntil),
                        statusLabel =
                            preparationStepStatus(
                                isSent = false,
                                isAwaitingSignature = false,
                                dependsOnNumbers = dependsOnNumbers,
                                isDueNow = secondsUntil <= 0,
                            ),
                    )
                },
            onDismiss = {},
        )
    }
}
