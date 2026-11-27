package co.electriccoin.zcash.ui.common.model.migration.sim

import cash.z.ecc.android.sdk.TransferResult

/**
 * The scriptable clock for the migration simulation harness. Owns the mock chain
 * ([FakeOrchardMigrationSdk]) and gives a test deterministic control over block production and
 * transaction mining, so a scenario can drive the real Lane A → Lane B → notification/banner
 * pipeline against one shared, time-advancing world instead of stubbing each SDK call by hand.
 *
 * Typical scenario shape:
 * ```
 * val driver = MigrationSimDriver()
 * driver.seedPlan(
 *     preparations = listOf(SimPrep(id = 1, layer = 0, scheduledHeight = ...)),
 *     transfers = listOf(SimTransfer(id = 8, scheduledHeight = ..., anchorBoundary = A, dependsOn = listOf(1))),
 *     startTip = ...,
 * )
 * driver.advanceTip(blocks = 10)          // time passes, nothing mines
 * driver.mine(id = 1, height = A + 78)     // the prep mines LATE, past the anchor
 * driver.sdk.finalizeReadyTransfers()      // exercise Lane A against the seeded state
 * ```
 *
 * The [sdk] is exposed directly so tests both drive it (the real workers/VMs call its
 * [FakeOrchardMigrationSdk] methods) and read its state for assertions
 * ([FakeOrchardMigrationSdk.allTxs], [FakeOrchardMigrationSdk.txById]).
 */
class MigrationSimDriver(
    val sdk: FakeOrchardMigrationSdk = FakeOrchardMigrationSdk(),
) {
    /** One preparation (note-split layer) tx to seed. */
    data class SimPrep(
        val id: Long,
        val layer: Int,
        val index: Int = 0,
        val scheduledHeight: Long,
        val dependsOn: List<Long> = emptyList(),
    )

    /** One transfer (crossing) tx to seed. [anchorBoundary] is the committed ZIP 318 bucket. */
    data class SimTransfer(
        val id: Long,
        val scheduledHeight: Long,
        val anchorBoundary: Long,
        val dependsOn: List<Long> = emptyList(),
    )

    /**
     * Seeds a committed migration plan directly into the fake chain — the state
     * `signAndStoreMigrationSchedule` would have persisted, so a scenario starts from an
     * in-progress migration without walking the propose/sign flow.
     *
     * Order-independent: preparations and transfers may reference each other's ids via `dependsOn`
     * regardless of listing order.
     */
    fun seedPlan(
        preparations: List<SimPrep> = emptyList(),
        transfers: List<SimTransfer> = emptyList(),
        startTip: Long,
    ) {
        preparations.forEach { prep ->
            sdk.addTx(
                FakeOrchardMigrationSdk.SimTx(
                    id = prep.id,
                    isTransfer = false,
                    layer = prep.layer,
                    scheduledHeight = prep.scheduledHeight,
                    anchorBoundary = null,
                    dependsOn = prep.dependsOn,
                )
            )
        }
        transfers.forEach { transfer ->
            sdk.addTx(
                FakeOrchardMigrationSdk.SimTx(
                    id = transfer.id,
                    isTransfer = true,
                    layer = 0,
                    scheduledHeight = transfer.scheduledHeight,
                    anchorBoundary = transfer.anchorBoundary,
                    dependsOn = transfer.dependsOn,
                )
            )
        }
        sdk.setTip(startTip)
    }

    /** Advances the chain tip by [blocks] blocks (no mining — just time passing). */
    fun advanceTip(blocks: Long) {
        require(blocks >= 0) { "Cannot advance a negative number of blocks: $blocks" }
        sdk.setTip(sdk.tip + blocks)
    }

    /** Sets the absolute tip. Must not move backward. */
    fun setTip(height: Long) = sdk.setTip(height)

    /**
     * Mines tx [id] at [height]. This is how a scenario places a dependency's confirmation on time
     * (height ≤ the spender's anchor) or LATE (height > the anchor). Advances the tip if [height]
     * is beyond it.
     */
    fun mine(id: Long, height: Long) = sdk.mineTx(id, height)

    /**
     * Delivers [n] fresh blocks the way a sync burst would — advances the tip by [n]. Mining is a
     * separate explicit act ([mine]); delivering blocks alone never confirms a pending tx.
     */
    fun deliverBlocks(n: Long) = advanceTip(n)

    /** Toggles the SDK's privacy sync-gate for scenarios that assert on Lane gating. */
    fun setSyncBlocked(blocked: Boolean) = sdk.setSyncBlocked(blocked)

    /**
     * Arms the NEXT broadcast attempt (the one that would otherwise execute a proved+due transfer)
     * to fail with a Tor circuit-bootstrap failure instead — the transfer stays unsent. One-shot:
     * the injection is consumed on that attempt, so the following attempt broadcasts normally,
     * modelling "Tor recovers".
     */
    fun failNextBroadcastWithTorFailure() {
        sdk.nextBroadcastFailure = TransferResult.NetworkError(retryable = true, isTorFailure = true)
    }

    /** Arms the next broadcast with an arbitrary [TransferResult] failure variant. */
    fun failNextBroadcast(result: TransferResult) {
        sdk.nextBroadcastFailure = result
    }

    /** Clears any armed broadcast failure — "the network / Tor recovers". */
    fun clearBroadcastFailure() {
        sdk.nextBroadcastFailure = null
    }

    /**
     * Sets the still-migratable Orchard residual (and, optionally, the per-run cap) the fake reports,
     * so [FakeOrchardMigrationSdk.estimateMigrationRunCount] and multi-round continuation decisions
     * read a real balance. Pass `perRunCap` to model an engine note cap smaller than the residual.
     */
    fun setMigratableResidual(zatoshi: Long, perRunCap: Long = sdk.perRunMigratableCapZatoshi) {
        sdk.migratableOrchardZatoshi = zatoshi
        sdk.perRunMigratableCapZatoshi = perRunCap
    }

    /** Marks the migration as requiring attention (invalid/expired transfer), for recovery scenarios. */
    fun markInvalidTransfers(
        reason: cash.z.ecc.android.sdk.AttentionReason = cash.z.ecc.android.sdk.AttentionReason.TransferExpired,
    ) {
        sdk.attentionReason = reason
        sdk.invalidTransfersPresent = true
    }
}
