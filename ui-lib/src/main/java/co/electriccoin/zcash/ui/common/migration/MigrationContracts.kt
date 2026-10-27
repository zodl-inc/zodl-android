package co.electriccoin.zcash.ui.common.migration

import android.content.Intent
import androidx.navigation.NavGraphBuilder
import co.electriccoin.zcash.ui.common.repository.MigrationHomeMessage
import co.electriccoin.zcash.ui.screen.home.HomeMessageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/*
 * Seam between ui-lib (the app "core") and the feature-migration module. ui-lib never imports
 * feature-migration classes — it talks exclusively to these contracts, and the app module wires
 * the implementations in via Koin (`featureMigrationModule`). When the migration era ends, delete
 * the feature module, its Koin module and these contracts' call-sites.
 */

/** Produces the migration home banner: the reactive payload and its UI state incl. click routing. */
interface MigrationHomeMessageSource {
    fun observe(): Flow<MigrationHomeMessage?>

    fun createMessageState(data: MigrationHomeMessage): HomeMessageState
}

/** True while a migration plan is active — the daily background [SyncWorker] yields to Lane A. */
interface MigrationGate {
    suspend fun isMigrationActive(): Boolean

    /**
     * True when the selected account has a migration that can be restarted — engine state
     * InProgress or RequiresAttention (any mid-run or stuck state). False for NotStarted/Complete.
     * Gates the Advanced-settings "Restart Migration" item.
     */
    suspend fun isRestartAvailable(): Boolean

    /**
     * Debug builds only (the Debug menu is the one place that sets it): whether the SDK's NU7 pause on new
     * scheduled migrations is lifted, so a scheduled migration can be tried on mainnet before the activation.
     * Remembered across restarts and off by default. It lifts nothing else: a transaction built for the wrong
     * consensus branch, or an expired one, is still never submitted.
     */
    suspend fun isNetworkUpgradeGateBypassEnabled(): Boolean = false

    /** See [isNetworkUpgradeGateBypassEnabled]. */
    suspend fun setNetworkUpgradeGateBypass(enabled: Boolean) = Unit
}

/**
 * Foreground SYNCED hook (prove + reconcile + lane revival). Fired by SynchronizerProviderImpl on
 * every SYNCED transition; the implementation no-ops when no migration plan is active.
 */
interface MigrationSyncedHook {
    suspend fun onSynced()
}

/** App-shell entry points (MainActivity, RootNavGraph, DebugVM). */
interface MigrationAppHooks {
    /**
     * Handles migration intent extras (notification deep links, the debug E2E driver). Returns
     * true when the intent was recognized and handled.
     */
    fun handleIntent(intent: Intent, scope: CoroutineScope): Boolean

    /** App-open / foreground migration catch-up (recovery routing + lane revival). */
    suspend fun checkRecovery()

    /**
     * Cancels all scheduled migration work — both lanes, the due alarm and any shown migration
     * notification. [accountKeyId] targets one account (Keystone disconnect); null cancels for
     * every account (wallet reset). Safe to call when nothing is scheduled. The workers' own
     * account-gone kill switch backs this up for anything already in flight.
     */
    suspend fun cancelMigrationWork(accountKeyId: String? = null)
}

/** Installs the migration destinations into the wallet nav graph. */
interface MigrationNavContributor {
    fun contribute(navGraphBuilder: NavGraphBuilder)
}

/**
 * Send-pipeline nav seam: the IMMEDIATE-mode Keystone cancel path pops back to Migration Review
 * (Send was never on that back stack — see CancelProposalFlowUseCase).
 */
interface MigrationNavigator {
    fun backToMigrationReview()

    /** Opens the Restart Migration screen (Advanced settings entry point). */
    fun forwardToRestartMigration()
}

/** Debug-menu migration actions; each returns the result text the debug screen displays. */
interface MigrationDebugActions {
    suspend fun restartMigration(): String

    suspend fun simulateTorFailure(): String
}

// The Balance Breakdown pool-correction seam (MigrationPoolCorrectionSource, PoolTruthCorrection)
// that lived here was removed 2026-08-06: GetBalancePoolsUseCase now reads
// WalletBalance.locked directly (a real field, plumbed from the SDK's Rust dependency's
// Balance::locked_value()) instead of reconstructing an approximation of it from live migration
// transfer states. No migration-specific contract is needed for balance display anymore.
