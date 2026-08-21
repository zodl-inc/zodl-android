package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationBlocker
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationHomeMessageSource
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_DUST_THRESHOLD_ZATOSHI
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_RESIDUAL_MIN_ZATOSHI
import co.electriccoin.zcash.ui.common.model.migration.MigrationAttentionKind
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferBlocker
import co.electriccoin.zcash.ui.common.model.migration.affectedTransferIndices
import co.electriccoin.zcash.ui.common.model.migration.toMigrationRangeText
import co.electriccoin.zcash.ui.common.model.migration.toUiKind
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.HasSeenMigrationCompleteStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsBackgroundExecutionAvailableProvider
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessage
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessageData
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import co.electriccoin.zcash.ui.screen.home.migration.MigrationBannerPhase
import co.electriccoin.zcash.ui.screen.home.migration.MigrationMessageState
import co.electriccoin.zcash.ui.screen.migration.complete.MigrationCompleteArgs
import co.electriccoin.zcash.ui.screen.migration.invalid.MigrationTransferInvalidArgs
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlin.time.Instant
import co.electriccoin.zcash.ui.design.R as DesignR

private const val PERCENT_MULTIPLIER = 100

/** The banner phase + title/subtitle override for one [MigrationMessageState] — replaces an
 * anonymous [Triple] so each field is named at the call site instead of positional. */
private data class BannerCopy(
    val phase: MigrationBannerPhase,
    val title: String?,
    val subtitle: String?,
)

/**
 * The home-banner source, fully LIVE off the engine — no plan cache anywhere (see
 * `spec/2026-07-30-plan-cache-elimination-proposal.md`): counts come from the engine's transfer
 * states (crossings-only, sent = done), phase decisions from the engine's [MigrationState] × the
 * live Orchard balance. The only persisted inputs are genuine UX flags (hasSeenComplete).
 */
class MigrationHomeMessageSourceImpl(
    private val context: Context,
    private val accountDataSource: AccountDataSource,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val observeMigrationLiveReadout: ObserveMigrationLiveReadoutUseCase,
    private val getOrchardBalance: GetOrchardBalanceUseCase,
    private val hasSeenMigrationCompleteStorageProvider: HasSeenMigrationCompleteStorageProvider,
    private val isBackgroundExecutionAvailableProvider: IsBackgroundExecutionAvailableProvider,
    private val navigationRouter: NavigationRouter,
) : MigrationHomeMessageSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<MigrationHomeMessage?> =
        accountDataSource.selectedAccount.flatMapLatest { account ->
            if (account == null) return@flatMapLatest flowOf(null)
            val accountKeyId = account.sdkAccount.accountUuid.toStorageKeyId()
            combine(
                hasSeenMigrationCompleteStorageProvider.observe(),
                // Published by MigrationLiveDriverImpl's own loop (2026-08-07) instead of this
                // source independently calling getMigrationState()/getMigrationTransferStates()/
                // hasOverdueTransfers() on its own 15s poll — those three are on the SDK's
                // mutex-gated `logged` lane or otherwise share its single-threaded DB I/O executor
                // with the live driver's own prove/broadcast work, which produced a live-reproduced
                // ~10-16s Home-banner load delay (2026-08-06/07 investigation). See the
                // repository's own kdoc.
                observeMigrationLiveReadout(accountKeyId),
                // Observed, not one-shot: after an IMMEDIATE migration (a plain send-max sweep that
                // never touches the engine) the balance is the only input that can hide the
                // "Migrate required" banner once the Orchard funds are spent.
                getOrchardBalance.observe(),
            ) { hasSeenComplete, readout, orchardBalance ->
                val sdkState = readout?.migrationState
                val states = readout?.states
                val snapshot =
                    states?.let {
                        migrationSnapshotFrom(
                            states = it,
                            estimatedTip = if (readout.estimatedTip >= 0) readout.estimatedTip else it.tipHeight,
                            secondsPerBlock = readout.estimatedSecondsPerBlock,
                        )
                    }
                // UNPROVABLE ANCHOR → immediate, deterministic "Update migration plan" attention
                // banner (decision 2026-07-30: user-driven — the user must SEE the bad state as
                // soon as it is known). The SDK synthesizes MigrationBlocker.UNPROVABLE_ANCHOR
                // from the engine's late-dependency guard.
                // TODO [#0]: remove this once the engine's UnprovableAnchor synthesis is retired.
                val hasUnprovable = states?.transfers?.any { it.blocker == MigrationBlocker.UNPROVABLE_ANCHOR } == true
                if (snapshot != null && hasUnprovable) {
                    return@combine MigrationHomeMessageData(
                        isRunActive = true,
                        completedCount = snapshot.completedCount,
                        totalCount = snapshot.totalCount,
                        attentionKind = MigrationAttentionKind.PLAN_UPDATE,
                    )
                }
                // Only computed when actually needed (RequiresAttention) — an extra snapshot
                // read on every other state would be wasted work.
                val (attentionKind, attentionRangeText) =
                    (sdkState as? MigrationState.RequiresAttention)?.let { requiresAttention ->
                        attentionInfoFor(requiresAttention.reason, snapshot)
                    } ?: (null to null)
                // Effectively constant for a plan's lifetime — not worth coupling to the hot
                // readout above (MigrationTransferStateRepository's own kdoc) — but still a real
                // SDK call, guarded the same way as everything else here.
                val dustThreshold =
                    runCatching { getOrchardMigrationSdk().migrationDustThresholdZatoshi() }
                        .getOrDefault(MIGRATION_DUST_THRESHOLD_ZATOSHI)
                migrationMessageFor(
                    sdkState = sdkState,
                    snapshot = snapshot,
                    hasSeenComplete = hasSeenComplete,
                    orchardBalanceZatoshi = orchardBalance?.value ?: 0L,
                    dustThresholdZatoshi = dustThreshold,
                    isBackgroundExecutionAvailable = isBackgroundExecutionAvailableProvider.isAvailable(),
                    hasOverdueTransfers = readout?.hasOverdueTransfers ?: false,
                    attentionKind = attentionKind,
                    attentionRangeText = attentionRangeText,
                )
            }
        }

    override fun createMessageState(data: MigrationHomeMessage): HomeMessageState {
        data as MigrationHomeMessageData
        val percent =
            if (data.totalCount > 0) {
                (data.completedCount * PERCENT_MULTIPLIER) / data.totalCount
            } else {
                0
            }
        // Spec §6.2/§6.3 — takes priority over the ordinary phases below: a plan needing
        // re-confirmation is more actionable than its last-known progress/completion state.
        val bannerCopy =
            when (data.attentionKind) {
                MigrationAttentionKind.PLAN_UPDATE -> {
                    BannerCopy(MigrationBannerPhase.ATTENTION, "Update migration plan", "Tap to review the details")
                }

                MigrationAttentionKind.TRANSFER_EXPIRED -> {
                    val range = data.attentionRangeText
                    BannerCopy(
                        phase = MigrationBannerPhase.ATTENTION,
                        title = if (range != null) "Transfer $range expired" else "A transfer expired",
                        subtitle = "Tap to review the details",
                    )
                }

                null -> {
                    when {
                        // MOB-1750: a small leftover Orchard balance not tied to an unseen in-app
                        // migration celebration gets its own "X ZEC left in Orchard" copy instead of
                        // the "Migration complete" celebration title.
                        data.isComplete && data.isResidueOnly -> {
                            val amount = stringRes(Zatoshi(data.residualBalanceZatoshi)).getString(context)
                            BannerCopy(
                                phase = MigrationBannerPhase.COMPLETE,
                                title = stringRes(DesignR.string.migrationHome_residueTitle, amount).getString(context),
                                subtitle = stringRes(DesignR.string.migrationHome_residueSubtitle).getString(context),
                            )
                        }

                        data.isComplete -> {
                            BannerCopy(MigrationBannerPhase.COMPLETE, null, "Tap to review the details")
                        }

                        // Spec §6.4: numbered per the due transfer, matching the convention used
                        // elsewhere (e.g. Progress's "Transfer ${completedCount + 1}").
                        data.isReadyToSend -> {
                            BannerCopy(
                                phase = MigrationBannerPhase.READY_TO_SEND,
                                title = null,
                                subtitle = "Transfer ${data.completedCount + 1} is ready to send",
                            )
                        }

                        !data.isRunActive -> {
                            BannerCopy(MigrationBannerPhase.REQUIRED, null, null)
                        }

                        // MOB-1620: always the numeric count, including the 0-of-N case right
                        // after the first transfer starts sending — the earlier "First transfer
                        // sending…" copy read as vaguer/stuck to Harry, and 0-based counts render
                        // fine ("0 of 5 transfers done ~0% complete").
                        else -> {
                            BannerCopy(
                                phase = MigrationBannerPhase.IN_PROGRESS,
                                title = null,
                                subtitle =
                                    "${data.completedCount} of ${data.totalCount} transfers done" +
                                        " ~ $percent% complete",
                            )
                        }
                    }
                }
            }
        return MigrationMessageState(
            phase = bannerCopy.phase,
            title = bannerCopy.title,
            progressLabel = bannerCopy.subtitle,
            progressPercent = percent.toFloat(),
            onClick = {
                onMigrationMessageClick(
                    isRunActive = data.isRunActive,
                    isComplete = data.isComplete,
                    isReadyToSend = data.isReadyToSend,
                    hasAttention = data.attentionKind != null,
                    isResidueOnly = data.isResidueOnly,
                )
            },
            onButtonClick = {
                onMigrationMessageClick(
                    isRunActive = data.isRunActive,
                    isComplete = data.isComplete,
                    isReadyToSend = data.isReadyToSend,
                    hasAttention = data.attentionKind != null,
                    isResidueOnly = data.isResidueOnly,
                )
            },
        )
    }

    private fun onMigrationMessageClick(
        isRunActive: Boolean,
        isComplete: Boolean,
        isReadyToSend: Boolean = false,
        hasAttention: Boolean = false,
        isResidueOnly: Boolean = false,
    ) {
        when {
            // A plan needing re-confirmation (spec §6.2/§6.3) always routes to the Transfer Invalid
            // info screen, regardless of its last-known progress/completion state.
            hasAttention -> navigationRouter.forward(MigrationTransferInvalidArgs)

            // Tapping the widget just opens the celebration screen — MigrationCompleteVM.onDone()
            // owns the seen-flag decision. MOB-1750: isResidueOnly threads which copy/summary
            // variant MigrationCompleteScreen should render.
            isComplete -> navigationRouter.forward(MigrationCompleteArgs(isResidueOnly = isResidueOnly))

            // Ready-to-send routes to Progress too — everything is pre-signed, there is nothing
            // to review; Progress's foreground broadcast loop executes the step silently while
            // the user watches (TransferReview screen deleted 2026-07-30).
            isReadyToSend -> navigationRouter.forward(MigrationProgressArgs)

            isRunActive -> navigationRouter.forward(MigrationProgressArgs)

            else -> navigationRouter.forward(MigrationSetupArgs)
        }
    }

    // Spec §6.2/§6.3 home-banner support — the affected transfers correlate by stable engine id
    // ON the live snapshot (display index and engine id live on the same row now).
    private fun attentionInfoFor(
        reason: AttentionReason,
        snapshot: LiveMigrationSnapshot?,
    ): Pair<MigrationAttentionKind, String?> {
        val kind = reason.toUiKind()
        if (snapshot == null) return kind to null
        val rangeText =
            reason
                .affectedTransferIndices(snapshot, Clock.System.now())
                .toMigrationRangeText()
        return kind to rangeText
    }
}

/**
 * The migration home-banner decision — a pure function over LIVE inputs only (engine state,
 * engine-derived snapshot, live balance, UX seen-flag). The old plan-cache inputs are gone; every
 * branch maps onto the live-conditions table in
 * `spec/2026-07-30-plan-cache-elimination-proposal.md` §3:
 *
 * - round running                → engine InProgress (counts from [snapshot])
 * - celebration (campaign done)  → engine Complete && balance < RESIDUAL_MIN && !hasSeenComplete
 * - residue (lock/migrate-anyway)→ dust < balance < RESIDUAL_MIN && not running
 * - next round / never migrated  → balance ≥ RESIDUAL_MIN && not running → "Migrate now"
 *
 * The "Complete && balance ≥ min ⇒ Migrate now" mapping is deliberately identical whether the
 * balance is a next Keystone round's residual or newly received funds — both need a migration, so
 * the ambiguity the old cleared-plan marker guarded against does not exist.
 */
@Suppress("CyclomaticComplexMethod")
internal fun migrationMessageFor(
    sdkState: MigrationState?,
    snapshot: LiveMigrationSnapshot?,
    hasSeenComplete: Boolean,
    orchardBalanceZatoshi: Long,
    dustThresholdZatoshi: Long = MIGRATION_DUST_THRESHOLD_ZATOSHI,
    isBackgroundExecutionAvailable: Boolean = true,
    hasOverdueTransfers: Boolean = false,
    now: Instant = Clock.System.now(),
    attentionKind: MigrationAttentionKind? = null,
    attentionRangeText: String? = null,
): MigrationHomeMessageData? {
    val next = snapshot?.nextPending
    // A RequiresAttention state WITH live run statuses is still a mid-run condition (e.g. the
    // transient SyncRequiredBeforeNext) — the balance-driven residue/required branches below must
    // not fire over it (the old plan-cache equivalent was their `plan == null` guard). With no
    // statuses (defensive: attention without a run) they stay reachable.
    val midRunAttention = sdkState is MigrationState.RequiresAttention && snapshot != null
    // A committed schedule that never received its external (Keystone) signature can NEVER
    // self-resolve — unlike SCHEDULE/ANCHOR_BOUNDARY/DEPENDENCIES, which clear as blocks mine,
    // SIGNATURE only clears when a human hands the device a QR to sign. There is no "wait longer"
    // that helps here, so its presence alone (no time threshold) is treated as "this plan needs
    // attention" and routed through the exact same flow a brand-new migration uses
    // (isRunActive = false below) rather than the passive "in progress" Progress screen, which has
    // no way to re-trigger signing. This is safe to re-enter: the engine's own commit_or_reuse
    // (zcash_pool_migration engine.rs) returns the SAME already-committed, still-unsigned PCZTs
    // from persisted state whenever a live (non-terminal) migration already exists for the
    // account — it does not attempt a second, conflicting commit. Found live 2026-08-02: a JNI
    // linkage bug (since fixed) could crash the app between commit and the first QR display,
    // leaving a schedule permanently stuck exactly like this.
    val hasSignatureBlock =
        snapshot?.let { s ->
            s.transfers.any { it.blocker == MigrationTransferBlocker.SIGNATURE } ||
                s.preparations.any { it.blocker == MigrationTransferBlocker.SIGNATURE }
        } ?: false
    return when {
        // Spec §6.2/§6.3 — takes priority over InProgress/Complete below.
        // SyncRequiredBeforeNext is explicitly excluded (a transient "keep syncing" condition,
        // not a user-action-required expiry) — falls through to the ordinary branches.
        sdkState is MigrationState.RequiresAttention &&
            sdkState.reason != AttentionReason.SyncRequiredBeforeNext &&
            snapshot != null -> {
            MigrationHomeMessageData(
                isRunActive = true,
                completedCount = snapshot.completedCount,
                totalCount = snapshot.totalCount,
                attentionKind = attentionKind ?: sdkState.reason.toUiKind(),
                attentionRangeText = attentionRangeText,
            )
        }

        sdkState is MigrationState.InProgress &&
            !hasSignatureBlock &&
            next != null &&
            !isBackgroundExecutionAvailable &&
            !hasOverdueTransfers &&
            next.scheduledAt <= now -> {
            MigrationHomeMessageData(
                isRunActive = true,
                completedCount = snapshot.completedCount,
                totalCount = snapshot.totalCount,
                isReadyToSend = true,
            )
        }

        sdkState is MigrationState.InProgress -> {
            MigrationHomeMessageData(
                isRunActive = !hasSignatureBlock,
                completedCount = snapshot?.completedCount ?: 0,
                totalCount = snapshot?.totalCount ?: 0,
            )
        }

        // Celebration: the campaign is genuinely done (nothing migratable left) and the user
        // hasn't engaged with the completion screen yet. Stays visible until MigrationCompleteVM
        // .onDone() marks it seen.
        sdkState == MigrationState.Complete &&
            !hasSeenComplete &&
            orchardBalanceZatoshi < MIGRATION_RESIDUAL_MIN_ZATOSHI -> {
            MigrationHomeMessageData(
                isRunActive = false,
                completedCount = snapshot?.completedCount ?: 0,
                totalCount = snapshot?.totalCount ?: 0,
                isComplete = true,
            )
        }

        // RESIDUE: a leftover Orchard balance above dust but below the migratable minimum. The
        // engine cannot migrate it (proposeMigrationTransfers → NothingToMigrate), so present it
        // as "migration completed" and route to MigrationCompleteScreen, whose residue flow lets
        // the user LOCK it or MIGRATE it anyway. The reported balance is the *spendable* Orchard
        // balance (locked notes excluded), so locking makes this stop firing on its own.
        !midRunAttention &&
            orchardBalanceZatoshi > dustThresholdZatoshi &&
            orchardBalanceZatoshi < MIGRATION_RESIDUAL_MIN_ZATOSHI -> {
            MigrationHomeMessageData(
                isRunActive = false,
                isComplete = true,
                isResidueOnly = true,
                residualBalanceZatoshi = orchardBalanceZatoshi,
            )
        }

        // Migratable balance with no run in progress — covers "never migrated", "a Keystone round
        // finished, residual needs another round" (engine still reports Complete), and "campaign
        // done but new funds arrived". All three correctly say "Migrate now".
        !midRunAttention && orchardBalanceZatoshi >= MIGRATION_RESIDUAL_MIN_ZATOSHI -> {
            MigrationHomeMessageData(isRunActive = false)
        }

        else -> {
            null
        }
    }
}
