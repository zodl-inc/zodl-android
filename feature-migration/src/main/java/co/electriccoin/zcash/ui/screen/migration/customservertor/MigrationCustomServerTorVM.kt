package co.electriccoin.zcash.ui.screen.migration.customservertor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerArgs
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryArgs
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import co.electriccoin.zcash.ui.design.R as DesignR

class MigrationCustomServerTorVM(
    private val args: MigrationCustomServerTorArgs,
    private val navigationRouter: NavigationRouter,
    private val isMigrationTorEnabledStorageProvider: IsMigrationTorEnabledStorageProvider,
) : ViewModel() {
    val state: StateFlow<MigrationCustomServerTorState?> =
        flowOf(
            MigrationCustomServerTorState(
                body =
                    when (args.mode) {
                        MigrationMode.IMMEDIATE -> stringRes(DesignR.string.migrationCustomServerTor_bodyImmediate)
                        MigrationMode.AUTOMATIC -> stringRes(DesignR.string.migrationCustomServerTor_bodyAutomatic)
                    },
                riskBody =
                    when (args.mode) {
                        MigrationMode.IMMEDIATE -> stringRes(DesignR.string.migrationCustomServerTor_riskBodyImmediate)
                        MigrationMode.AUTOMATIC -> stringRes(DesignR.string.migrationCustomServerTor_riskBodyAutomatic)
                    },
                onContinueWithoutTor = ::onContinueWithoutTor,
                onSwitchServer = ::onSwitchServer,
                onBack = ::onBack,
            )
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null,
        )

    private fun onContinueWithoutTor() {
        // Persists the migration-scoped Tor setting so subsequent broadcasts for this account don't
        // keep retrying Tor against a custom server that was just confirmed not to support it —
        // mirrors MigrationPrivacyVM.onConfirm() / MigrationTorFailureVM.onContinueWithoutTor().
        viewModelScope.launch { isMigrationTorEnabledStorageProvider.store(false) }
        navigationRouter.forward(
            when (args.mode) {
                MigrationMode.IMMEDIATE -> MigrationReviewArgs(mode = args.mode)
                MigrationMode.AUTOMATIC -> MigrationBatteryArgs
            }
        )
    }

    private fun onSwitchServer() = navigationRouter.forward(ChooseServerArgs)

    private fun onBack() = navigationRouter.back()
}
