package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.MigrationNextAction
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationTransferStates
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One atomic live readout of the migration engine: the per-transfer states, the tip-estimate pair
 * needed to render them (`OrchardMigrationSdk.estimatedChainTip()`/`estimatedSecondsPerBlock()`),
 * the derived [MigrationState], and whether a due transfer is overdue — all read together from the
 * same call site, at the same instant, so a consumer never pairs a fresh field with a stale one
 * (2026-08-06/07: the ready-to-send flicker this repository's sibling fix closed for the Home banner
 * was exactly this — `scheduledAt` and `hasOverdueTransfers` read at different instants).
 *
 * [estimatedTip] follows [cash.z.ecc.android.sdk.OrchardMigrationSdk.estimatedChainTip]'s own
 * "negative means unavailable" convention — callers fall back to `states.tipHeight` in that case,
 * same as before this type existed.
 */
data class MigrationLiveReadout(
    val states: MigrationTransferStates?,
    val estimatedTip: Long,
    val estimatedSecondsPerBlock: Long,
    val migrationState: MigrationState?,
    val hasOverdueTransfers: Boolean,
    /** The NU7 activation height of the wallet's network, for splitting height ranges that span it. */
    val nu7ActivationHeight: Long? = null,
)

/**
 * Builds a [MigrationLiveReadout] entirely from the SDK's mutex-free `loggedRead` lane
 * (`getMigrationTransferStates()`, `getMigrationStateUnreconciled()`, the no-DB tip estimators) —
 * never [OrchardMigrationSdk.getMigrationState]/`hasOverdueTransfers()`, which take
 * `MIGRATION_DB_ACCESS_MUTEX` (2026-08-07 read/write-separation design). `hasOverdueTransfers` is
 * derived from the same pure transfer-states read: `ready && action == BROADCAST` is exactly the
 * "should the engine broadcast this now" signal the mutating `hasOverdueTransfers()` call itself
 * checks internally.
 *
 * The single shared shape for every caller that wants a display-only readout without triggering
 * any mutex-gated engine work — the Home/Progress cold-start fallbacks and the live driver's own
 * priming publish (see [co.electriccoin.zcash.work.MigrationLiveDriverImpl]) all call this instead
 * of hand-rolling the same five-field construction three times and risking them drifting apart.
 */
suspend fun OrchardMigrationSdk.readUnreconciledLiveReadout(): MigrationLiveReadout {
    val states = getMigrationTransferStates()
    return MigrationLiveReadout(
        states = states,
        estimatedTip = estimatedChainTip(),
        estimatedSecondsPerBlock = estimatedSecondsPerBlock(),
        migrationState = getMigrationStateUnreconciled(),
        hasOverdueTransfers = states?.transfers?.any { it.ready && it.action == MigrationNextAction.BROADCAST } == true,
        nu7ActivationHeight = nu7ActivationHeight,
    )
}

/**
 * The live migration engine's readout, published by `MigrationLiveDriverImpl`'s own loop after
 * every step it drives (and periodically during a long re-armed wait — see the driver's own kdoc).
 * No screen calls `OrchardMigrationSdk.getMigrationTransferStates()`/`estimatedChainTip()`/
 * `estimatedSecondsPerBlock()` on its own poll anymore — those used to mean the Progress screen's
 * independent reads and the live driver's prove/broadcast calls competed for the SDK's
 * single-threaded DB I/O executor (`SdkDispatchers.DATABASE_IO` — a genuinely single OS thread, per
 * its own kdoc: no WAL, so all `RustBackend`/`MigrationRustBackend` calls serialize onto it,
 * regardless of which Kotlin DB-mutex lane a given call is on), producing a long white-screen wait
 * whenever the user opened Progress right as the driver was mid-step (e.g. proving, which blocks
 * that one thread for real wall-clock seconds). Observing this instead costs nothing: [observe]
 * never blocks — it just replays whatever the driver last saw, published from reads the driver was
 * already going to do as part of its own already-serialized loop, not a second concurrent caller.
 *
 * Tagged per account, mirroring [PendingMigrationScheduleRepository] — a Zodl and a Keystone
 * account migrating in parallel each get their own independent published value.
 *
 * [observe] returns `null` until the live driver has published at least once for that account this
 * process's lifetime (e.g. before its very first loop iteration completes, or if no migration is
 * `in_progress` so the driver never runs at all) — callers needing a value in that window fall back
 * to one direct SDK read of their own, same as before this repository existed.
 */
interface MigrationTransferStateRepository {
    fun observe(accountKeyId: String): StateFlow<MigrationLiveReadout?>

    fun publish(accountKeyId: String, readout: MigrationLiveReadout)

    /**
     * Resets this account's cached readout to `null` — for a flow that resets the engine's own
     * state OUTSIDE the driver's normal step progression (currently only "Restart Migration"),
     * where the last-published readout would otherwise keep describing a plan that no longer
     * exists. Any active observer's `combine` re-emits immediately on this write (a `StateFlow`
     * always notifies collectors on a `.value =` change), so `published ?: fetchFreshReadout()`
     * falls through to a genuine SDK read right away — no need to wait for that read's own 15s
     * recheck cadence, and no need to pre-populate a fresh value here ourselves.
     */
    fun clear(accountKeyId: String)
}

class MigrationTransferStateRepositoryImpl : MigrationTransferStateRepository {
    private val perAccount = mutableMapOf<String, MutableStateFlow<MigrationLiveReadout?>>()

    @Synchronized
    private fun flowFor(accountKeyId: String): MutableStateFlow<MigrationLiveReadout?> =
        perAccount.getOrPut(accountKeyId) { MutableStateFlow(null) }

    override fun observe(accountKeyId: String): StateFlow<MigrationLiveReadout?> = flowFor(accountKeyId)

    override fun publish(accountKeyId: String, readout: MigrationLiveReadout) {
        flowFor(accountKeyId).value = readout
    }

    override fun clear(accountKeyId: String) {
        flowFor(accountKeyId).value = null
    }
}
