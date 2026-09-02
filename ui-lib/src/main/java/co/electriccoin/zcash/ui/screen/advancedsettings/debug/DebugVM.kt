package co.electriccoin.zcash.ui.screen.advancedsettings.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.migration.MigrationDebugActions
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.DebugForceBackgroundExecutionUnavailable
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.db.DebugDBArgs
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.orchardbalance.DebugOrchardBalanceArgs
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.text.DebugTextArgs
import co.electriccoin.zcash.ui.screen.hotfix.ephemeral.EphemeralHotfixArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DebugVM(
    private val copyToClipboardUseCase: CopyToClipboardUseCase,
    private val ephemeralAddressRepository: EphemeralAddressRepository,
    private val accountDataSource: AccountDataSource,
    private val migrationDebugActions: MigrationDebugActions,
    private val migrationAppHooks: MigrationAppHooks,
    private val context: Context,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<DebugState> =
        MutableStateFlow(
            DebugState(
                onBack = ::onBack,
                items =
                    listOf(
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Get Current Ephemeral Address"),
                            onClick = ::onGetEphemeralAddressClick
                        ),
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Generate an Ephemeral Address"),
                            onClick = ::onGenerateEphemeralAddressClick
                        ),
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Discover Funds"),
                            onClick = ::onDiscoverFundsClick
                        ),
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Query Database"),
                            onClick = ::onQueryDatabaseClick
                        ),
                        ListItemState(
                            title = stringRes("Current Shield Addresses"),
                            onClick = ::onCurrentShieldAddressesClick
                        ),
                        ListItemState(
                            title = stringRes("Set Mock Orchard Balance (Migration)"),
                            onClick = ::onSetMockOrchardBalanceClick
                        ),
                        ListItemState(
                            title = stringRes("Migration restart"),
                            onClick = ::onMigrationRestartClick
                        ),
                        ListItemState(
                            title = stringRes("Migration: simulate Tor background failure"),
                            onClick = ::onSimulateMigrationTorFailureClick
                        ),
                        ListItemState(
                            title = stringRes("Migration: toggle 'no background execution' (Transfer Ready to Send)"),
                            onClick = ::onToggleBackgroundExecutionUnavailableClick
                        )
                    )
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onGetEphemeralAddressClick() =
        viewModelScope.launch {
            val address = ephemeralAddressRepository.get()
            copyToClipboardUseCase(address?.address.toString())
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Current Ephemeral Address",
                    text = address.toString()
                )
            )
        }

    private fun onGenerateEphemeralAddressClick() =
        viewModelScope.launch {
            val address = ephemeralAddressRepository.create()
            copyToClipboardUseCase(address.address)
            navigationRouter.forward(
                DebugTextArgs(
                    title = "New Ephemeral Address",
                    text = address.toString()
                )
            )
        }

    private fun onCurrentShieldAddressesClick() =
        viewModelScope.launch {
            val accounts = accountDataSource.getAllAccounts()
            val text =
                accounts.joinToString("\n\n") { account ->
                    val label =
                        when (account) {
                            is ZashiAccount -> "Zashi"
                            is KeystoneAccount -> "Keystone"
                        }
                    "$label\n${account.unifiedAddress}"
                }
            copyToClipboardUseCase(text)
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Current Shield Addresses",
                    text = text
                )
            )
        }

    private fun onDiscoverFundsClick() = navigationRouter.forward(EphemeralHotfixArgs(null))

    private fun onQueryDatabaseClick() = navigationRouter.forward(DebugDBArgs)

    private fun onSetMockOrchardBalanceClick() = navigationRouter.forward(DebugOrchardBalanceArgs)

    /**
     * Wipes the current account's in-progress migration entirely — the engine's own run (see
     * [cash.z.ecc.android.sdk.OrchardMigrationSdk.clearMigration]'s kdoc) plus every app-side
     * leftover — so a fresh propose/commit can be tested immediately, instead of waiting out or
     * resuming whatever migration is already in progress. Delegates to
     * [MigrationDebugActions.restartMigration], which promotes the same orchestration used by the
     * production "Restart Migration" flow (`RestartMigrationUseCase`).
     */
    private fun onMigrationRestartClick() =
        viewModelScope.launch {
            val text = migrationDebugActions.restartMigration()
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Migration restart",
                    text = text
                )
            )
        }

    // Reproduces spec §6.2's "background Tor failure" state (MigrationWorker's non-retryable
    // NetworkError-while-useTor branch) without waiting for a real background run to fail — sets
    // the same persisted flag and posts the same notification, then immediately re-runs the same
    // on-launch reconciliation HomeVM's init{} triggers, so the Sending screen shows up right away
    // instead of only on the next app relaunch/foreground.
    private fun onSimulateMigrationTorFailureClick() =
        viewModelScope.launch {
            val text = migrationDebugActions.simulateTorFailure()
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Migration: simulate Tor background failure",
                    text = text
                )
            )
        }

    // Spec §6.4 "Transfer Ready to Send" is otherwise only reachable by actually revoking the
    // app's battery-optimization exemption from system Settings — this flips a debug-only override
    // read by IsBackgroundExecutionAvailableProvider.isAvailable() instead, so QA can toggle the
    // condition on demand. Toggling back "on" (available) doesn't undo an already-shown banner —
    // that still needs a fresh reconciliation pass (e.g. reopening the app) to re-evaluate.
    private fun onToggleBackgroundExecutionUnavailableClick() =
        viewModelScope.launch {
            val nowForced = !DebugForceBackgroundExecutionUnavailable.isForced(context)
            DebugForceBackgroundExecutionUnavailable.set(context, nowForced)
            migrationAppHooks.checkRecovery()
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Migration: toggle 'no background execution'",
                    text =
                        if (nowForced) {
                            "Background execution now forced UNAVAILABLE. On testnet a committed " +
                                "transfer becomes due within ~15 min (12-block anchor bucket); once " +
                                "due, the Transfer Ready to Send banner/screen appears."
                        } else {
                            "Background execution restored to the device's real state."
                        }
                )
            )
        }
}
