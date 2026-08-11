package co.electriccoin.zcash.ui.screen.migration.progress

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationPreparation
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationTransfer
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationDetails
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationStepDetail
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferBlocker
import co.electriccoin.zcash.ui.common.model.migration.formatMigrationDuration
import co.electriccoin.zcash.ui.common.model.migration.isTestnetBuildFlavor
import co.electriccoin.zcash.ui.common.model.migration.preparationStepStatus
import co.electriccoin.zcash.ui.common.model.migration.preparationStepTimeLabel
import co.electriccoin.zcash.ui.common.model.migration.preparationStepTitle
import co.electriccoin.zcash.ui.common.model.migration.toSnapshot
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.MigrationLiveReadout
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveMigrationLiveReadoutUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import java.math.MathContext
import kotlin.time.Clock
import kotlin.time.Instant
import co.electriccoin.zcash.ui.design.R as DesignR

class MigrationProgressVM(
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val observeMigrationLiveReadout: ObserveMigrationLiveReadoutUseCase,
    private val navigationRouter: NavigationRouter,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val sendLce = mutableLce<Unit>()

    val state: StateFlow<LceState<MigrationProgressState>> =
        combine(
            exchangeRateRepository.state,
            // liveReadoutFlow() itself re-emits on every internal ticker beat now (folded inside
            // ObserveMigrationLiveReadoutUseCase — closes a gap where this screen's cold-start
            // fallback only ever fired once per repository-null period instead of re-polling like
            // Home's equivalent fallback does), so wall-clock-dependent display (the "in ~X minutes"
            // row labels, migrationProgressSubtitle) keeps refreshing without a separate ticker
            // element here.
            liveReadoutFlow(),
        ) { rate, readout ->
            // Everything on this screen derives LIVE from the engine's persisted states — no plan
            // cache to diverge, and no app-side "overdue"/countdown: each row renders purely from
            // the engine's per-transaction status (decision with Dominik 2026-07-31). The measured
            // block rate is still used for the rough total-duration estimate in the header only.
            // Both come from the SAME readout as the states below — one atomic read, never a fresh
            // states paired with a stale/independently-read tip estimate or vice versa.
            val liveStates = readout?.states
            val secondsPerBlock = readout?.estimatedSecondsPerBlock ?: 0L
            val est = readout?.estimatedTip ?: -1L
            liveStates
                ?.toSnapshot(
                    estimatedTip = if (est >= 0) est else liveStates.tipHeight,
                    secondsPerBlock = secondsPerBlock,
                    nowEpochSeconds = Clock.System.now().epochSeconds,
                )?.let { createState(it, rate) }
        }.withLce(sendLce, errorStateMapper::mapToState)
            .stateIn(this)

    // MigrationPlanRepository's per-transfer status/scheduledAt is a display cache, written once
    // at propose/commit time. Reading the engine's own persisted state directly keeps the displayed
    // schedule true to the engine — the single source of truth for the plan — regardless of what
    // the cache last recorded.
    //
    // The cache-replay/ticker/mutex-free-SDK-fallback logic itself now lives in
    // ObserveMigrationLiveReadoutUseCase (folded inside, 2026-08-07 dedup extraction — matches
    // MigrationHomeMessageSourceImpl's equivalent flow exactly, sharing the one implementation
    // instead of two near-identical private copies; see that use case's own kdoc for the full
    // rationale). This wrapper only resolves the account-scoped key each time the flow is
    // (re)collected.
    private fun liveReadoutFlow(): Flow<MigrationLiveReadout?> =
        flow {
            val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
            emitAll(observeMigrationLiveReadout(accountKeyId))
        }

    fun navigateBack() = navigationRouter.back()

    private fun createState(
        snapshot: LiveMigrationSnapshot,
        exchangeRateState: ExchangeRateState,
    ): MigrationProgressState {
        val now = Clock.System.now()
        val subtitle = migrationProgressSubtitle(snapshot, now)

        val totalZatoshi = snapshot.transfers.sumOf { it.amountZatoshi }
        val totalAmount = stringRes(Zatoshi(totalZatoshi))
        return MigrationProgressState(
            title = stringRes(DesignR.string.migration_common_progressTitle),
            subtitle = stringRes(subtitle),
            totalAmount = totalAmount,
            totalFiatAmount = fiatAmount(Zatoshi(totalZatoshi), exchangeRateState),
            // Figma "PR App Designs Q3'26" node 3480:7638: a balance-tracker card showing the
            // live Orchard (source) → Ironwood (destination) split, so the user sees the
            // migration's real-time progress at a glance, not just the transfer list below it.
            //
            // Derived ENTIRELY from the engine's own live transfer states (same source as the row
            // list below, already fetched — no extra SDK call), not from GetBalancePoolsUseCase's
            // wallet balance. Two reasons wallet balance doesn't work for this card:
            // (1) `.total` excludes notes locked/spent by the migration's OWN not-yet-mined
            // transfers, so it under-reports how much value still physically sits in Orchard —
            // those notes are still ON CHAIN until the transfer actually mines.
            // (2) `synchronizer.walletBalances` is driven by the same Slipstream engine polling
            // loop as the Activity list, which the migration write path never pokes via
            // `notifyTxChange()` — so it can be STALE by an unpredictable amount, sometimes already
            // reflecting a lock and sometimes not. Combining it with a live-computed adjustment
            // (as an earlier version of this fix did) double-counted unpredictably depending on
            // that staleness (observed live: Orchard 9.5056 + Ironwood 7.740 = 17.25 ZEC shown
            // against a real ~10 ZEC balance).
            // ironwoodZatoshi + orchardRemainingZatoshi always sums to exactly totalZatoshi — pure
            // conservation, no dependency on any wallet-balance read at all.
            balanceTracker =
                run {
                    val ironwood = Zatoshi(ironwoodCrossedZatoshi(snapshot.transfers))
                    val orchardRemaining = orchardRemainingZatoshi(totalZatoshi, snapshot.transfers)
                    MigrationProgressBalanceTracker(
                        orchardAmount = stringRes(orchardRemaining),
                        orchardFiatAmount = fiatAmount(orchardRemaining, exchangeRateState),
                        ironwoodAmount = stringRes(ironwood),
                        ironwoodFiatAmount = fiatAmount(ironwood, exchangeRateState),
                    )
                },
            preparations =
                if (snapshot.preparations.size > 1) {
                    emptyList()
                } else {
                    snapshot.preparations.mapIndexed { i, p ->
                        // The last row overall (across the combined preparations+transfers
                        // timeline) gets the "in ~X" phrasing; every earlier not-yet-due row gets
                        // bare "~X". Preparations always schedule before the transfers that depend
                        // on them, so this display order (preparations, then transfers) already
                        // matches schedule order — the last preparation is only the overall-last
                        // row when there are no transfers at all.
                        val isLastRowOverall = i == snapshot.preparations.lastIndex && snapshot.transfers.isEmpty()
                        val display = preparationRowDisplay(p, now, isLastRowOverall)
                        MigrationProgressPreparationState(
                            number = i + 1,
                            statusLabel = display.label,
                            isReadyNow = display.isReadyNow,
                            isSent = p.isSent,
                        )
                    }
                },
            preparationsSummary = preparationsSummary(snapshot, now),
            preparationDetails = preparationDetails(snapshot, now, totalAmount),
            transfers =
                snapshot.transfers.mapIndexed { i, t ->
                    val isLastRowOverall = i == snapshot.transfers.lastIndex
                    val display = transferRowDisplay(t, now, isLastRowOverall)
                    MigrationProgressTransferState(
                        index = t.index + 1,
                        amount = stringRes(Zatoshi(t.amountZatoshi)),
                        fiatAmount = fiatAmount(Zatoshi(t.amountZatoshi), exchangeRateState),
                        statusLabel = display.label,
                        isReadyNow = display.isReadyNow,
                        // Attention paint (orange) ONLY for genuine, cannot-heal-on-its-own states —
                        // never for a merely-late-but-healthy transfer (the old "overdue" false
                        // alarm). Expired, the synthetic unprovable-anchor, and the engine-native
                        // unsatisfiable verdict (2026-08-05: same "can never mine" category).
                        isAttention =
                            t.blocker == MigrationTransferBlocker.UNPROVABLE_ANCHOR ||
                                t.blocker == MigrationTransferBlocker.EXPIRED ||
                                t.blocker == MigrationTransferBlocker.UNSATISFIABLE,
                        isSent = t.isSent,
                    )
                },
            isComplete = snapshot.isComplete,
            onBack = ::onBack,
            onDone = if (snapshot.isComplete) ::onDone else null,
        )
    }

    // Only non-null with more than one preparation (mirrors MigrationReviewScreen's identical
    // collapse threshold) — rolls up to the single active (first not-yet-sent) preparation's own
    // row state, or "Done" once every preparation has broadcast.
    private fun preparationsSummary(
        snapshot: LiveMigrationSnapshot,
        now: Instant,
    ): MigrationProgressPreparationSummary? {
        val preparations = snapshot.preparations
        if (preparations.size <= 1) return null
        val active = preparations.firstOrNull { !it.isSent }
        return if (active == null) {
            MigrationProgressPreparationSummary(statusLabel = stringRes("Done"), isReadyNow = false, isSent = true)
        } else {
            val isLastRowOverall = active.id == preparations.last().id && snapshot.transfers.isEmpty()
            val display = preparationRowDisplay(active, now, isLastRowOverall)
            MigrationProgressPreparationSummary(
                statusLabel = display.label,
                isReadyNow = display.isReadyNow,
                isSent = false
            )
        }
    }

    // The "Show details" sheet's full per-step breakdown — see MigrationPreparationDetails' doc.
    // Only non-null alongside [preparationsSummary] (more than one preparation).
    private fun preparationDetails(
        snapshot: LiveMigrationSnapshot,
        now: Instant,
        totalAmount: StringResource,
    ): MigrationPreparationDetails? {
        val preparations = snapshot.preparations
        if (preparations.size <= 1) return null
        val numberById = preparations.mapIndexed { i, p -> p.id to (i + 1) }.toMap()
        return MigrationPreparationDetails(
            stepCount = preparations.size,
            totalAmount = totalAmount,
            steps =
                preparations.mapIndexed { i, p ->
                    val dependsOnNumbers = p.dependsOn.mapNotNull { numberById[it] }.sorted()
                    val secondsUntil = (p.scheduledAt - now).inWholeSeconds
                    MigrationPreparationStepDetail(
                        title = preparationStepTitle(i + 1, preparations.size),
                        // A sent step's own scheduledAt is in the past by the time it's actually
                        // broadcast, so a forward-looking "in ~X" here would floor to a misleading
                        // "in ~10 min"/"in ~1 hour" (formatMigrationDuration's privacy floor) even
                        // though the step already happened — "Done" alone (statusLabel) already
                        // says everything this row needs to.
                        timeLabel = if (p.isSent) stringRes("") else preparationStepTimeLabel(secondsUntil),
                        statusLabel =
                            preparationStepStatus(
                                isSent = p.isSent,
                                isAwaitingSignature = p.blocker == MigrationTransferBlocker.SIGNATURE,
                                dependsOnNumbers = dependsOnNumbers,
                                isDueNow = p.scheduledAt <= now,
                            ),
                        isDone = p.isSent,
                    )
                },
            onDismiss = {},
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

    private fun onBack() = sendLce.guardLoading { navigationRouter.back() }

    // "Reschedule" no longer mutates the plan — a missed-but-unexpired transfer needs NO plan
    // change by design (ZIP 374: the signature does not cover the anchor, so it proves late
    // against its committed boundary and broadcasts late; the engine is the single source of
    private fun onDone() = navigationRouter.backToRoot()
}

/**
 * How much of the migration's total crossing amount has actually landed in Ironwood: the sum of
 * every transfer's [LiveMigrationTransfer.amountZatoshi] whose [LiveMigrationTransfer.minedHeight]
 * is set. Only a MINED transfer counts as landed — a merely proved or even broadcast-but-unmined
 * transfer's crossing could still expire and its value return to Orchard, so `isSent` alone is not
 * enough here (unlike the row labels, which treat "Sent" as the terminal display state).
 *
 * Top-level and internal for unit-testability without Android, Koin, or a live SDK/ViewModel.
 */
internal fun ironwoodCrossedZatoshi(transfers: List<LiveMigrationTransfer>): Long =
    transfers.filter { it.minedHeight != null }.sumOf { it.amountZatoshi }

/**
 * The true chain-confirmed remaining Orchard balance for the balance-tracker card: [totalZatoshi]
 * (the migration's full committed crossing amount) minus whatever has actually mined into Ironwood
 * ([ironwoodCrossedZatoshi]). A not-yet-mined transfer's notes are still on chain in Orchard —
 * merely proving or even broadcasting a transfer doesn't move the value until it mines — so this
 * always sums with [ironwoodCrossedZatoshi] to exactly [totalZatoshi]: pure conservation, no
 * dependency on any (potentially stale) wallet-balance read.
 *
 * Top-level and internal for unit-testability without Android, Koin, or a live SDK/ViewModel.
 */
internal fun orchardRemainingZatoshi(
    totalZatoshi: Long,
    transfers: List<LiveMigrationTransfer>,
): Zatoshi = Zatoshi(totalZatoshi - ironwoodCrossedZatoshi(transfers))

/**
 * Header subtitle for the Migration Progress screen.
 *
 * Before any transfer has completed ([LiveMigrationSnapshot.completedCount] == 0), keeps the
 * existing static total-span framing unchanged: "over ~X" spanning the earliest to the latest
 * scheduled moment across preparations AND transfers.
 *
 * Once in progress (`completedCount > 0` — a concrete, checkable condition, decision with Dominik
 * 2026-08-01), the header instead counts down the REMAINING time to the last scheduled moment.
 * This MUST branch explicitly on `remaining > 0` vs `remaining <= 0`: [formatMigrationDuration]
 * floors its input at a network-dependent privacy floor (10 min testnet / 1 hour mainnet, decision
 * 2026-08-03), so once the migration runs late (`now >= lastScheduled` — a normal, expected state
 * for this engine, not stuck/broken), a naive `formatMigrationDuration(remaining)` call on a
 * negative/zero span would silently floor to a permanently-lying "~10 min"/"~1 hour" forever. That
 * is exactly the "healthy-but-late state painted as broken" bug class commit `33cff6883` fixed for
 * the per-row labels — this header must not reintroduce it via a different code path, so the
 * `remaining <= 0` branch switches to non-time copy that never calls [formatMigrationDuration] on
 * that value.
 *
 * Top-level and internal for unit-testability without Android, Koin, or a live SDK/ViewModel.
 */
internal fun migrationProgressSubtitle(
    snapshot: LiveMigrationSnapshot,
    now: Instant,
    fineGrained: Boolean = isTestnetBuildFlavor(),
): String {
    // Scheduled moments across preparations AND transfers, used for both the pre-start static
    // total-span estimate and the in-progress remaining-time countdown below.
    val allScheduled = (snapshot.transfers.map { it.scheduledAt } + snapshot.preparations.map { it.scheduledAt })
    val firstScheduled = allScheduled.minOrNull() ?: now
    val lastScheduled = allScheduled.maxOrNull() ?: now
    val remainingCount = snapshot.totalCount - snapshot.completedCount
    return when {
        snapshot.isComplete -> {
            "All ${snapshot.totalCount} transfers are complete."
        }

        // Not yet started (no transfer has completed): keep today's existing static total-span
        // framing unchanged — this task only changes the in-progress copy.
        snapshot.completedCount <= 0 -> {
            val span = (lastScheduled - firstScheduled).inWholeSeconds
            "Your balance splits into ${snapshot.totalCount} transfers over " +
                "${formatMigrationDuration(span, fineGrained = fineGrained)}. There are " +
                "$remainingCount remaining transfers."
        }

        // In progress: the header now counts down remaining time instead of showing a static total.
        else -> {
            val remaining = (lastScheduled - now).inWholeSeconds
            if (remaining > 0) {
                "Your balance splits into ${snapshot.totalCount} transfers. About " +
                    "${formatMigrationDuration(remaining, fineGrained = fineGrained)} remaining. There are " +
                    "$remainingCount remaining transfers."
            } else {
                // Running late but healthy — never claim a duration here (see doc above).
                "Your balance splits into ${snapshot.totalCount} transfers. Finishing up… " +
                    "There are $remainingCount remaining transfers."
            }
        }
    }
}

/**
 * The finalized, production-only render for one timeline row (2026-08-03): [label] is the only
 * text shown, [isReadyNow] flags the one state Figma paints in the primary text color instead of
 * the muted gray every other row uses. No debug-only raw engine word is surfaced anywhere anymore.
 */
internal data class MigrationRowDisplay(
    val label: StringResource,
    val isReadyNow: Boolean,
)

/**
 * Row state for a crossing transfer, in Figma's priority order (2026-08-03 finalization, decision
 * with Dominik — replaces the old debug/primary split from 2026-08-01):
 *
 * 1. A genuinely blocked row (`EXPIRED`/`UNPROVABLE_ANCHOR`/`SIGNATURE`/`UNSATISFIABLE` — the ones
 *    that can never self-resolve) keeps its own explicit copy, regardless of schedule time.
 *    (2026-08-05: `UNSATISFIABLE` added — same "can never mine" shape as `UNPROVABLE_ANCHOR`,
 *    reusing its copy pending a real design pass. `EXPIRY_IMMINENT`/`AWAITING_REEVALUATION` are
 *    deliberately NOT given a branch here — per their SDK doc comments both are transient/
 *    self-resolving [MigrationBlocker] readings, so falling through to the schedule-based
 *    "Ready now"/relative-estimate display is an acceptable placeholder, not a bug.)
 * 2. [sentRowDisplay] — broadcast (`isSent`), Figma's "Confirmed" vs "Sent" split collapses into
 *    one state.
 * 3. "Sending now" has no real backing signal in this passively-polled snapshot (see
 *    `spec/2026-08-03-progress-screen-row-states-plan.md`) and folds into state 4.
 * 4. "Ready now" — not sent, no blocker or only a self-resolving one (`DEPENDENCIES`,
 *    `ANCHOR_BOUNDARY`, `SCHEDULE`), and [LiveMigrationTransfer.scheduledAt] has already passed.
 *    Figma renders this in the primary text color, not muted — [MigrationRowDisplay.isReadyNow].
 * 5/6. Future: a relative estimate via [formatMigrationDuration] — bare "~X" for every row except
 *    [isLastRowOverall], which gets "in ~X" (Figma is consistent: only the last row on screen ever
 *    gets the "in" prefix).
 *
 * Never reintroduces "Overdue" or a countdown-to-deadline (decision with Dominik 2026-07-31,
 * reaffirmed 2026-08-03) — a late-but-healthy transfer just reads "Ready now".
 *
 * Top-level and internal for unit-testability without Android or Koin.
 */
internal fun transferRowDisplay(
    t: LiveMigrationTransfer,
    now: Instant,
    isLastRowOverall: Boolean,
    fineGrained: Boolean = isTestnetBuildFlavor(),
): MigrationRowDisplay =
    when {
        t.blocker == MigrationTransferBlocker.EXPIRED -> {
            MigrationRowDisplay(stringRes("Expired"), isReadyNow = false)
        }

        t.blocker == MigrationTransferBlocker.UNPROVABLE_ANCHOR -> {
            MigrationRowDisplay(stringRes("Needs reschedule"), isReadyNow = false)
        }

        t.blocker == MigrationTransferBlocker.UNSATISFIABLE -> {
            MigrationRowDisplay(stringRes("Needs reschedule"), isReadyNow = false)
        }

        t.blocker == MigrationTransferBlocker.SIGNATURE -> {
            MigrationRowDisplay(stringRes("Awaiting signature"), isReadyNow = false)
        }

        t.isSent -> {
            sentRowDisplay(t.minedAt, now, fineGrained)
        }

        t.scheduledAt <= now -> {
            MigrationRowDisplay(stringRes("Ready now"), isReadyNow = true)
        }

        else -> {
            futureRowDisplay(t.scheduledAt, now, isLastRowOverall, fineGrained)
        }
    }

/**
 * Row state for a preparation (note-split) row — same priority shape as [transferRowDisplay], but
 * restricted to the blockers the engine actually produces for preparations (`SIGNATURE` /
 * `DEPENDENCIES` — preparations never surface an attention state or the other blockers), and
 * "Done" instead of "Sent"/"Confirmed" for the terminal state, matching Figma's "Split Balance"
 * summary row copy. No mined-time relative label: preparations carry no mined height at all.
 */
internal fun preparationRowDisplay(
    p: LiveMigrationPreparation,
    now: Instant,
    isLastRowOverall: Boolean,
    fineGrained: Boolean = isTestnetBuildFlavor(),
): MigrationRowDisplay =
    when {
        p.isSent -> {
            MigrationRowDisplay(stringRes("Done"), isReadyNow = false)
        }

        p.blocker == MigrationTransferBlocker.SIGNATURE -> {
            MigrationRowDisplay(stringRes("Awaiting signature"), isReadyNow = false)
        }

        p.blocker == MigrationTransferBlocker.DEPENDENCIES -> {
            MigrationRowDisplay(stringRes("Waiting for previous split"), isReadyNow = false)
        }

        p.scheduledAt <= now -> {
            MigrationRowDisplay(stringRes("Ready now"), isReadyNow = true)
        }

        else -> {
            futureRowDisplay(p.scheduledAt, now, isLastRowOverall, fineGrained)
        }
    }

/**
 * Collapses Figma's "Confirmed"/"Sent" split into a single broadcast state (2026-08-03, decision
 * with Dominik): [LiveMigrationTransfer] carries no broadcast timestamp, only an optional mined
 * height/time, so "Sent {relative} ago" is only shown once [minedAt] is known; until then this
 * reads plain "Sent" rather than inventing a broadcast time the model doesn't have.
 */
internal fun sentRowDisplay(
    minedAt: Instant?,
    now: Instant,
    fineGrained: Boolean = isTestnetBuildFlavor(),
): MigrationRowDisplay =
    if (minedAt != null) {
        val secondsAgo = (now - minedAt).inWholeSeconds.coerceAtLeast(0L)
        // No privacy floor here (2026-08-06 revised decision) — see formatMigrationDuration's own
        // kdoc: an already-mined transfer's exact timing is already public on-chain, so flooring
        // this specific label has no privacy benefit left, only less-informative copy.
        MigrationRowDisplay(
            stringRes(
                "Sent ${
                    formatMigrationDuration(secondsAgo, fineGrained = fineGrained, applyPrivacyFloor = false)
                } ago"
            ),
            isReadyNow = false
        )
    } else {
        MigrationRowDisplay(stringRes("Sent"), isReadyNow = false)
    }

/**
 * The not-yet-due relative estimate shared by [transferRowDisplay]/[preparationRowDisplay]: bare
 * "~X" normally, "in ~X" only for [isLastRowOverall] (Figma's consistent convention across all 4
 * reference screens).
 */
private fun futureRowDisplay(
    scheduledAt: Instant,
    now: Instant,
    isLastRowOverall: Boolean,
    fineGrained: Boolean = isTestnetBuildFlavor(),
): MigrationRowDisplay {
    val secondsLeft = (scheduledAt - now).inWholeSeconds
    val relative = formatMigrationDuration(secondsLeft, fineGrained = fineGrained)
    val label = if (isLastRowOverall) "in $relative" else relative
    return MigrationRowDisplay(stringRes(label), isReadyNow = false)
}
