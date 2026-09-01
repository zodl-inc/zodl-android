package co.electriccoin.zcash.ui.common.model.migration.sim

import cash.z.ecc.android.sdk.KeystoneBatchDecodeResult
import cash.z.ecc.android.sdk.KeystoneBatchSignedPczts
import cash.z.ecc.android.sdk.MigrationAdvanceResult
import cash.z.ecc.android.sdk.MigrationAdvanceStep
import cash.z.ecc.android.sdk.MigrationBlocker
import cash.z.ecc.android.sdk.MigrationNextAction
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationSummary
import cash.z.ecc.android.sdk.MigrationSyncWakeup
import cash.z.ecc.android.sdk.MigrationTransferState
import cash.z.ecc.android.sdk.MigrationTransferStates
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.NoteSplitProposal
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferAttemptOutcome
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A stateful, in-memory [OrchardMigrationSdk] — the "mock chain" for the full-flow migration
 * simulation harness. Unlike the per-test `mockk(relaxed = true)` stubs scattered across the
 * existing migration tests, this holds a single shared, time-advancing state that the real
 * Lane A (finalize/prove), Lane B (broadcast), the banner observation, and the VMs can all drive
 * against one consistent world.
 *
 * Do not construct or mutate this directly from a test — go through [MigrationSimDriver], which owns
 * the clock ([MigrationSimDriver.advanceTip], [MigrationSimDriver.mine],
 * [MigrationSimDriver.deliverBlocks]) and the plan seeding. The driver reads this SDK's state to
 * make its assertions.
 *
 * Deliberately MINIMAL: only the methods Lane A/B, the banner, and the late-prep scenario actually
 * touch are modelled with real behaviour. Everything else throws [NotImplementedError] with a
 * pointer to add it when a new scenario needs it — see the `// NOTE(sim):` markers.
 *
 * ── The model ─────────────────────────────────────────────────────────────────
 * The chain is a list of [SimTx] plus a current [tip]. A transaction is *provable* when every
 * transaction it [SimTx.dependsOn] is mined at a height ≤ its own [SimTx.anchorBoundary] AND its
 * anchor is already in the past relative to the (estimated) tip — the same ordering constraint the
 * Rust `late_dependency_anchor_tests` exercise: a dependency that mines LATER than the anchor the
 * transfer committed to can never be witnessed under that anchor, so the transfer must never prove.
 */
class FakeOrchardMigrationSdk : OrchardMigrationSdk {
    /**
     * One transaction on the mock chain. [isTransfer] `false` marks a preparation (note-split
     * layer) transaction — the engine schedules those for broadcast exactly like transfers, but
     * display-facing consumers filter them out.
     *
     * [anchorBoundary] is the committed ZIP 318 288-block bucket boundary (null for preparations,
     * which use a natural anchor). [minedHeight] is null until the tx is mined. [dependsOn] lists
     * the ids of earlier txs whose outputs this one spends (empty for layer-0 preparations and for
     * transfers funded by an already-mined note).
     */
    data class SimTx(
        val id: Long,
        val isTransfer: Boolean,
        val layer: Int,
        val scheduledHeight: Long,
        val anchorBoundary: Long?,
        val dependsOn: List<Long> = emptyList(),
        var minedHeight: Long? = null,
        var proved: Boolean = false,
        var sent: Boolean = false,
        /** Crossing value; ignored for preparations (the engine reports null there). */
        val amountZatoshi: Long = 100_000L,
        /** Preparation index within its layer; ignored for transfers. */
        val index: Int = 0,
        /** ZIP 203 expiry height; 0 = never expires (the engine's own sentinel). */
        val expiryHeight: Long = 0L,
    )

    /** The mock chain. Ordered by insertion; correlate by [SimTx.id], never by index. */
    private val txs: MutableList<SimTx> = mutableListOf()

    /** Current chain tip (last block the wallet has "seen"). */
    var tip: Long = 0L
        private set

    /** Seconds-per-block the harness reports; testnet-fast by default to match live tuning. */
    var secondsPerBlock: Long = 28L

    /** Dust threshold — the fixed protocol constant (0.001 ZEC). */
    var dustThresholdZatoshi: Long = 100_000L

    /**
     * The Orchard balance the fake reports as *still migratable* — i.e. the residual left after
     * whatever transfers are already committed to the current run. A multi-round Keystone scenario
     * drives this down between rounds and reads [estimateMigrationRunCount] / [getMigrationState]'s
     * residual off it. Zero means nothing left to migrate.
     */
    var migratableOrchardZatoshi: Long = 0L

    /**
     * The per-run migratable ceiling the engine's note cap imposes — used only to derive
     * [estimateMigrationRunCount] from [migratableOrchardZatoshi] (ceil(balance / perRunCap)).
     * A large default so single-round scenarios report exactly one run.
     */
    var perRunMigratableCapZatoshi: Long = Long.MAX_VALUE

    /** When true, [hasInvalidTransfers] reports true and [getMigrationState] is RequiresAttention. */
    var invalidTransfersPresent: Boolean = false

    /** The reason surfaced when [invalidTransfersPresent]; TransferExpired by default. */
    var attentionReason: cash.z.ecc.android.sdk.AttentionReason =
        cash.z.ecc.android.sdk.AttentionReason.TransferExpired

    /**
     * Set by the driver to make the NEXT [executeNextPendingTransfer] that would otherwise succeed
     * return a broadcast failure instead of executing — the transfer stays unsent. Consumed
     * (cleared to null) on the attempt, so a subsequent call after "the failure clears" broadcasts
     * normally. Models a Tor circuit-bootstrap failure ([TransferResult.NetworkError] with
     * `isTorFailure = true`) or any other injected [TransferResult] variant.
     */
    var nextBroadcastFailure: TransferResult? = null

    /**
     * When false, [executeNextPendingTransfer] marks a transaction SENT without mining it — the
     * "broadcast but not yet mined" window the real chain always has (review M3: instant
     * auto-mining made the sent≠mined and inter-layer-gap states unreachable in simulation).
     * Tests then mine explicitly via the driver.
     */
    var autoMineOnBroadcast: Boolean = true

    private val syncBlocked = MutableStateFlow(false)

    // ── Harness-only accessors (not part of OrchardMigrationSdk) ───────────────

    /** Snapshot of every tx on the chain — for driver seeding and test assertions. */
    fun allTxs(): List<SimTx> = txs.map { it.copy() }

    fun txById(id: Long): SimTx? = txs.firstOrNull { it.id == id }?.copy()

    internal fun addTx(tx: SimTx) {
        require(txs.none { it.id == tx.id }) { "Duplicate sim tx id ${tx.id}" }
        txs += tx
    }

    internal fun setTip(newTip: Long) {
        require(newTip >= tip) { "Tip must not move backward: $tip -> $newTip" }
        tip = newTip
    }

    internal fun mineTx(id: Long, height: Long) {
        val tx =
            txs.firstOrNull { it.id == id }
                ?: error("No sim tx with id $id to mine")
        tx.minedHeight = height
        if (height > tip) tip = height
    }

    internal fun setSyncBlocked(blocked: Boolean) {
        syncBlocked.value = blocked
    }

    // ── Provability ────────────────────────────────────────────────────────────

    /**
     * A tx is provable exactly when every dependency is MINED at a height ≤ this tx's anchor
     * boundary AND the anchor is already in the past (anchor + 1 < tip + 1, i.e. anchor < tip).
     *
     * The dependency-vs-anchor half is the late-dependency bug guard: if a funding note mines at a
     * height GREATER than the anchor the spending transfer committed to, no witness for it exists
     * under that anchor, so the transfer can never prove — [finalizeReadyTransfers] must leave it
     * awaiting proof forever (until the plan is restarted), not crash or loop.
     */
    private fun isProvable(tx: SimTx): Boolean {
        if (tx.proved) return false // already done
        val anchor = tx.anchorBoundary
        // Preparations (natural anchor) prove as soon as their deps are mined and tip has advanced
        // past their scheduled height; transfers gate on their committed anchor boundary.
        val depsMinedInTime =
            tx.dependsOn.all { depId ->
                val dep = txs.firstOrNull { it.id == depId }
                val minedAt = dep?.minedHeight
                minedAt != null && (anchor == null || minedAt <= anchor)
            }
        if (!depsMinedInTime) return false
        val effectiveAnchor = anchor ?: tx.scheduledHeight
        // anchor + 1 < tip + 1  ⇔  anchor < tip
        return effectiveAnchor < tip
    }

    private fun isDue(tx: SimTx): Boolean = tx.scheduledHeight <= tip

    /**
     * The late-dependency guard, mirroring the backend's `is_unprovable_anchor`: a dependency
     * that MINED past this tx's committed anchor boundary can never be witnessed under that
     * anchor — the tx is permanently unprovable (until rebuilt).
     */
    private fun isUnprovableAnchor(tx: SimTx): Boolean {
        val anchor = tx.anchorBoundary ?: return false
        if (tx.proved || tx.sent) return false
        return tx.dependsOn.any { depId ->
            val minedAt = txs.firstOrNull { it.id == depId }?.minedHeight
            minedAt != null && minedAt > anchor
        }
    }

    /**
     * Ready/action/blocker triple for one tx, in parity with the SDK's extended
     * `migrationTransferStatesNative` (which zips the engine's `transaction_statuses` with the
     * synthetic UNPROVABLE_ANCHOR guard override).
     */
    private fun statusFor(tx: SimTx): Triple<Boolean, MigrationNextAction?, MigrationBlocker?> =
        when {
            tx.sent -> {
                Triple(false, null, null)
            }

            isUnprovableAnchor(tx) -> {
                Triple(false, null, MigrationBlocker.UNPROVABLE_ANCHOR)
            }

            !tx.proved -> {
                val depsMined =
                    tx.dependsOn.all { depId -> txs.firstOrNull { it.id == depId }?.minedHeight != null }
                when {
                    !depsMined -> Triple(false, null, MigrationBlocker.DEPENDENCIES)
                    !isProvable(tx) -> Triple(false, null, MigrationBlocker.ANCHOR_BOUNDARY)
                    else -> Triple(true, MigrationNextAction.PROVE, null)
                }
            }

            !isDue(tx) -> {
                Triple(false, null, MigrationBlocker.SCHEDULE)
            }

            else -> {
                Triple(true, MigrationNextAction.BROADCAST, null)
            }
        }

    private fun buildState(): MigrationState {
        if (invalidTransfersPresent) return MigrationState.RequiresAttention(attentionReason)
        if (txs.isEmpty()) return MigrationState.NotStarted
        val transfers = txs.filter { it.isTransfer }
        val completed = transfers.count { it.sent }
        val total = transfers.size
        if (total > 0 && completed == total) return MigrationState.Complete
        val nextReadyHeight = txs.firstOrNull { !it.sent }?.scheduledHeight
        return MigrationState.InProgress(
            MigrationProgress(
                completedTransfers = completed,
                totalTransfers = total,
                nextTransferReadyAtHeight = nextReadyHeight,
            )
        )
    }

    // ── OrchardMigrationSdk: implemented (state) ───────────────────────────────

    override suspend fun getMigrationState(): MigrationState = buildState()

    override suspend fun getMigrationStateUnreconciled(): MigrationState = buildState()

    override suspend fun getMigrationProgress(): MigrationProgress? =
        (buildState() as? MigrationState.InProgress)?.progress

    override suspend fun getMigrationTransferStates(): MigrationTransferStates? {
        if (txs.isEmpty()) return null
        return MigrationTransferStates(
            transfers =
                txs.map {
                    val (ready, action, blocker) = statusFor(it)
                    MigrationTransferState(
                        id = it.id,
                        isTransfer = it.isTransfer,
                        isSent = it.sent,
                        isProved = it.proved,
                        scheduledHeight = it.scheduledHeight,
                        anchorBoundaryHeight = it.anchorBoundary,
                        ready = ready,
                        action = action,
                        blocker = blocker,
                        amountZatoshi = it.amountZatoshi.takeIf { _ -> it.isTransfer },
                        prepLayer = it.layer.takeIf { _ -> !it.isTransfer },
                        prepIndex = it.index.takeIf { _ -> !it.isTransfer },
                        dependsOn = it.dependsOn,
                        expiryHeight = it.expiryHeight.takeIf { h -> h > 0 },
                        minedHeight = it.minedHeight,
                    )
                },
            tipHeight = tip,
        )
    }

    // ── OrchardMigrationSdk: implemented (engine driver surface) ────────────────

    /**
     * Kept in parity with the SDK's `guarded_next_step` (asserted against the Rust golden traces
     * in backend-lib's `state_machine_trace_tests`): terminal → Complete; first provable
     * (guard-filtered) → Prove; earliest broadcastable → Broadcast; all mined → Complete, else
     * Waiting. No expiry model in the sim yet, so Rebuild is never emitted here.
     */
    override suspend fun nextStep(): MigrationAdvanceResult? {
        if (txs.isEmpty()) return null
        // The sim doesn't model the engine's peek-ahead (MigrationPeek) at all — every step
        // below reports next = null (nothing schedulable), a safe default that leaves
        // reArm()'s existing wakeup/due-height sources as the only candidates in tests that
        // drive through this fake.
        if (invalidTransfersPresent) return MigrationAdvanceResult(MigrationAdvanceStep.Waiting, next = null)
        // VEC (id) order for prove, matching the engine's iteration order.
        txs.sortedBy { it.id }.firstOrNull { isProvable(it) && !isUnprovableAnchor(it) }?.let {
            return MigrationAdvanceResult(MigrationAdvanceStep.Prove(it.id), next = null)
        }
        // VEC (id) order among proved+due — the engine's next_broadcastable iterates its
        // transactions vector, NOT the schedule (engine change request §3; golden-trace parity).
        txs
            .filter { it.proved && !it.sent && it.minedHeight == null && isDue(it) }
            .minByOrNull { it.id }
            ?.let { return MigrationAdvanceResult(MigrationAdvanceStep.Broadcast(it.id), next = null) }
        if (txs.all { it.minedHeight != null }) {
            return MigrationAdvanceResult(MigrationAdvanceStep.Complete, next = null)
        }
        return MigrationAdvanceResult(MigrationAdvanceStep.Waiting, next = null)
    }

    /**
     * One wake-up per unproven, unsent tx at the first height past its (effective) anchor
     * boundary. Deliberately INCLUDES unprovable-anchor txs — the real engine keeps emitting
     * wake-ups for them forever (engine change request, GAP 2), and the app layer is the one
     * that must filter them; the fake must not be kinder than the engine.
     */
    override suspend fun syncWakeupSchedule(): List<MigrationSyncWakeup>? {
        if (txs.isEmpty()) return null
        return txs
            .filter { !it.proved && !it.sent }
            .map { MigrationSyncWakeup(height = (it.anchorBoundary ?: it.scheduledHeight) + 1, covers = listOf(it.id)) }
            .sortedBy { it.height }
    }

    override suspend fun applySignature(transferId: Long, signedPczt: Pczt): Boolean =
        notImpl("applySignature — add when a Keystone per-transfer signature scenario needs it")

    /** The engine's real constants (signing_rounds.rs): 96 actions/round, prep=16, transfer=3. */
    override suspend fun keystoneSigningRoundBudget(): cash.z.ecc.android.sdk.KeystoneSigningRoundBudget =
        cash.z.ecc.android.sdk
            .KeystoneSigningRoundBudget(maxActions = 96, preparationActions = 16, transferActions = 3)

    // ── OrchardMigrationSdk: implemented (background execution) ─────────────────

    /**
     * Marks every provable tx proved (see [isProvable]) and returns how many transitioned this
     * call. Idempotent — returns 0 when nothing is newly provable, which is the ordinary steady
     * state while a funding note is still mining (or mined too late to ever prove).
     */
    override suspend fun finalizeReadyTransfers(): Int {
        var proved = 0
        for (tx in txs) {
            if (isProvable(tx)) {
                tx.proved = true
                proved++
            }
        }
        return proved
    }

    /**
     * Broadcasts the earliest proved-unsent-due transfer tx (by [SimTx.scheduledHeight], then id),
     * marks it sent, and mines it a couple of blocks later so a subsequent Lane-A pass sees it
     * confirmed. Mirrors the real `next_due_transfer → broadcast → record_transfer_result`
     * composition; a due tx is returned again on every call until it is actually sent, so retries
     * are safe.
     *
     * Only [SimTx.isTransfer] == true txs are considered — preparations are seeded for provability
     * tracking but are never dispatched via this method (the real SDK uses submitNoteSplit for
     * those).
     *
     * Priority: proved+due transfers are dispatched first (by scheduledHeight, then id). Only when
     * NO proved+due transfer exists does the fake fall back to the earliest due-but-unproved
     * transfer and return [TransferAttemptOutcome.AwaitingProof] for it. This matches the real SDK
     * semantics: a stuck late-dependency tx does NOT block later-scheduled proved transfers from
     * broadcasting.
     */
    override suspend fun executeNextPendingTransfer(
        options: NetworkPrivacyOptions,
        useEstimatedTip: Boolean,
    ): TransferAttemptOutcome {
        val due =
            txs
                // Kind-AGNOSTIC, matching the real engine's next_broadcastable (invariant 4:
                // multi-transaction preparation layers broadcast through the same loop — the fake's
                // former Transfer-only filter was exactly the deadlock-prone iOS filter). Mined
                // transactions are on-chain already and never pending. VEC (id) order — the engine
                // iterates its transactions vector, not the schedule.
                .filter { !it.sent && it.minedHeight == null && isDue(it) }
                .sortedBy { it.id }

        if (due.isEmpty()) return TransferAttemptOutcome.NothingDue

        // Prefer the earliest proved+due transfer; only fall back to AwaitingProof if none exists.
        val candidate =
            due.firstOrNull { it.proved }
                ?: return TransferAttemptOutcome.AwaitingProof(due.first().id)

        // Injected broadcast failure (e.g. a Tor circuit-bootstrap failure): the attempt reached
        // the broadcast stage — the transfer was proved and due — but the network step failed. The
        // transfer stays UNSENT so the next window (after the failure clears) retries it. Consume
        // the injection so exactly one attempt fails.
        nextBroadcastFailure?.let { failure ->
            nextBroadcastFailure = null
            return TransferAttemptOutcome.Executed(failure)
        }

        candidate.sent = true
        if (autoMineOnBroadcast) {
            // Mine it shortly after broadcast so confirmation-dependent state settles.
            val minedAt = maxOf(candidate.scheduledHeight, tip) + 2L
            candidate.minedHeight = minedAt
            if (minedAt > tip) tip = minedAt
        }
        return TransferAttemptOutcome.Executed(
            TransferResult.Success(txId = TransactionId.new("sim-tx-${candidate.id}".toByteArray()))
        )
    }

    // ── OrchardMigrationSdk: implemented (reconciliation / banner) ──────────────

    /**
     * True if any not-yet-sent tx is past its scheduled height. Drives the home banner's
     * "Transfer ready to send" prompt and Lane B's catch-up. [useEstimatedTip] is accepted for
     * signature parity; the sim already advances [tip] explicitly, so estimated == synced here.
     */
    override suspend fun hasOverdueTransfers(useEstimatedTip: Boolean): Boolean =
        txs.any { it.isTransfer && !it.sent && isDue(it) }

    override suspend fun reconcileInvalidations(): Boolean = false

    override suspend fun estimatedChainTip(): Long = tip

    override suspend fun estimatedSecondsPerBlock(): Long = secondsPerBlock

    override suspend fun migrationDustThresholdZatoshi(): Long = dustThresholdZatoshi

    override suspend fun migratableOrchardTotal(): Long =
        notImpl("migratableOrchardTotal — add when a scenario needs it")

    override fun isSyncBlocked(): Flow<Boolean> = syncBlocked.asStateFlow()

    override fun privacySyncBufferDuration(): Duration = 30.seconds

    // ── OrchardMigrationSdk: not needed by current scenarios ───────────────────
    // NOTE(sim): implement these as scenarios that exercise the propose/commit, Keystone,
    // completion-summary, or invalidity-recovery flows are added to the harness.

    /**
     * Stateless preview: how many successive runs the CURRENT [migratableOrchardZatoshi] would need
     * given the per-run cap — `ceil(balance / perRunCap)`. Zero balance → 0 runs. Mirrors the real
     * method's "reflects whatever remains right now, no memory of prior rounds" contract, which is
     * exactly what a multi-round campaign polls after each round to decide whether another is due.
     */
    override suspend fun estimateMigrationRunCount(): Int? {
        val balance = migratableOrchardZatoshi
        if (balance <= 0L) return 0
        val cap = perRunMigratableCapZatoshi
        if (cap <= 0L) return 1
        // ceil without overflow for the Long.MAX_VALUE default.
        return ((balance - 1L) / cap + 1L).toInt()
    }

    override suspend fun isNoteSplitNeeded(): Boolean =
        notImpl("isNoteSplitNeeded — add when a note-split scenario needs it")

    override suspend fun prepareNoteSplit(): NoteSplitProposal =
        notImpl("prepareNoteSplit — add when a note-split scenario needs it")

    override suspend fun submitNoteSplit(
        proposal: NoteSplitProposal,
        usk: UnifiedSpendingKey,
    ): TransferResult = notImpl("submitNoteSplit — add when a note-split commit scenario needs it")

    override suspend fun createUnsignedNoteSplitPczt(proposal: NoteSplitProposal): Pczt =
        notImpl("createUnsignedNoteSplitPczt — Keystone path, add when a Keystone scenario needs it")

    override suspend fun storeSignedNoteSplitPczt(
        signedPczt: Pczt,
        options: NetworkPrivacyOptions,
    ): TransferResult = notImpl("storeSignedNoteSplitPczt — Keystone path")

    override suspend fun createUnsignedTransferPczts(
        schedule: MigrationSchedule,
    ): List<Pair<Long, Pczt>> = notImpl("createUnsignedTransferPczts — Keystone path")

    override suspend fun createUnsignedPreparationPczts(
        schedule: MigrationSchedule,
    ): List<cash.z.ecc.android.sdk.UnsignedPreparationPczt> = notImpl("createUnsignedPreparationPczts — Keystone path")

    override suspend fun storeSignedSchedulePczts(signed: List<Pair<Long, Pczt>>): Unit =
        notImpl("storeSignedSchedulePczts — Keystone path")

    override suspend fun buildKeystoneSignBatchQrParts(
        requestId: ByteArray,
        splitUnsignedPczt: Pczt?,
        transferUnsignedPczts: List<Pczt>,
        maxFragmentLen: Int,
    ): List<String> = notImpl("buildKeystoneSignBatchQrParts — Keystone batch-signing path")

    override suspend fun resetKeystoneSignBatchDecoder(): Unit =
        notImpl("resetKeystoneSignBatchDecoder — Keystone batch-signing path")

    override suspend fun decodeKeystoneSignBatchPart(
        part: String,
        expectedRequestId: ByteArray,
    ): KeystoneBatchDecodeResult = notImpl("decodeKeystoneSignBatchPart — Keystone batch-signing path")

    override suspend fun applyKeystoneBatchSignatures(
        splitUnsignedPczt: Pczt?,
        transferUnsignedPczts: List<Pczt>,
        batchSignResponse: ByteArray,
    ): KeystoneBatchSignedPczts = notImpl("applyKeystoneBatchSignatures — Keystone batch-signing path")

    override suspend fun proposeMigrationTransfers(includeResidual: Boolean): MigrationSchedule =
        notImpl("proposeMigrationTransfers — add when a propose scenario needs it")

    override suspend fun proposeMigrationTransfersFromSplit(
        splitProposal: NoteSplitProposal,
    ): MigrationSchedule = notImpl("proposeMigrationTransfersFromSplit — add when a split-propose scenario needs it")

    override suspend fun proposeImmediateMigration(): Proposal =
        notImpl("proposeImmediateMigration — add when an immediate send-max scenario needs it")

    override suspend fun signAndStoreMigrationSchedule(
        schedule: MigrationSchedule,
        usk: UnifiedSpendingKey,
    ): Unit = notImpl("signAndStoreMigrationSchedule — the sim seeds committed plans directly via the driver")

    override suspend fun restartCurrentMigrationStep(includeResidual: Boolean): MigrationSchedule =
        notImpl("restartCurrentMigrationStep — add when a RequiresAttention recovery scenario needs it")

    override suspend fun hasInvalidTransfers(): Boolean = invalidTransfersPresent

    /**
     * Read straight from the mock chain's SENT transfers (the engine's persisted, completion-surviving
     * record) — null until at least one transfer has actually been sent, matching the real method's
     * "returns null when there is no mined transfer yet" fallback-to-zeros contract. Per-transfer
     * crossing value is modelled as [dustThresholdZatoshi] so the summary is non-trivial; scenarios
     * that care about exact amounts can extend [SimTx] later.
     */
    override suspend fun getMigrationSummary(): MigrationSummary? {
        val sent = txs.filter { it.isTransfer && it.sent }
        if (sent.isEmpty()) return null
        val minedHeights = sent.mapNotNull { it.minedHeight }
        val first = minedHeights.minOrNull() ?: 0L
        val last = minedHeights.maxOrNull() ?: 0L
        return MigrationSummary(
            totalMigratedZatoshi = dustThresholdZatoshi * sent.size,
            transferCount = sent.size,
            // Block height stands in for the mined-block timestamp — the sim has no wall clock; the
            // ordering (first ≤ last) is what the elapsed-duration display relies on.
            firstMinedEpochSeconds = first,
            lastMinedEpochSeconds = last,
        )
    }

    override suspend fun lockRemainingOrchardBalance(): Unit =
        notImpl("lockRemainingOrchardBalance — add when a dust-locking scenario needs it")

    /** Recorded flag — set true when [clearMigration] is invoked; see RestartMigrationUseCaseTest. */
    var clearMigrationCalled: Boolean = false
        private set

    override suspend fun clearMigration() {
        clearMigrationCalled = true
        // Return the fake to a no-run state so getMigrationState() reads NotStarted. Note this models
        // the post-swap deleteMigration() target state, not today's interim real clearMigration() —
        // that one marks the run Failed (state -> RequiresAttention) and keeps txs around. The
        // simplification is fine here: RestartMigrationUseCaseTest asserts clearMigrationCalled plus
        // the cleanup side effects, not the resulting getMigrationState() value.
        txs.clear()
        invalidTransfersPresent = false
    }

    private fun notImpl(what: String): Nothing =
        throw NotImplementedError(
            "FakeOrchardMigrationSdk does not model this yet: $what. " +
                "Extend the fake (see the NOTE(sim) block) rather than reaching for a raw mockk stub."
        )
}
