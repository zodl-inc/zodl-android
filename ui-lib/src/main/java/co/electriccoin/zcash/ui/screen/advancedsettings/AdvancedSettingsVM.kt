package co.electriccoin.zcash.ui.screen.advancedsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.NavigationTargets
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.common.migration.MigrationNavigator
import co.electriccoin.zcash.ui.common.model.DistributionDimension
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletRestoringStateUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToExportPrivateDataUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToResetWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToTaxExportUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToWalletBackupUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.DebugArgs
import co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerArgs
import co.electriccoin.zcash.ui.screen.disconnect.DisconnectArgs
import co.electriccoin.zcash.ui.screen.tor.settings.TorSettingsArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class AdvancedSettingsVM(
    getWalletRestoringState: GetWalletRestoringStateUseCase,
    getWalletAccounts: GetWalletAccountsUseCase,
    private val navigationRouter: NavigationRouter,
    private val navigateToTaxExport: NavigateToTaxExportUseCase,
    private val navigateToWalletBackup: NavigateToWalletBackupUseCase,
    private val getVersionInfo: GetVersionInfoProvider,
    private val navigateToResetWallet: NavigateToResetWalletUseCase,
    private val navigateToExportPrivateData: NavigateToExportPrivateDataUseCase,
    private val migrationGate: MigrationGate,
    private val migrationNavigator: MigrationNavigator,
) : ViewModel() {
    private val versionInfo by lazy { getVersionInfo() }

    private val restartAvailable = MutableStateFlow(false)

    init {
        viewModelScope.launch { restartAvailable.value = migrationGate.isRestartAvailable() }
    }

    val state: StateFlow<AdvancedSettingsState> =
        combine(
            getWalletRestoringState.observe(),
            getWalletAccounts.observe(),
            restartAvailable,
        ) { walletState, accounts, isRestartAvailable ->
            createState(walletState, accounts, isRestartAvailable)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue =
                createState(
                    getWalletRestoringState.observe().value,
                    getWalletAccounts.observe().value,
                    restartAvailable.value,
                )
        )

    private fun createState(
        walletRestoringState: WalletRestoringState,
        accounts: List<WalletAccount>?,
        isRestartAvailable: Boolean,
    ): AdvancedSettingsState {
        val hasKeystoneAccount = accounts?.any { it is co.electriccoin.zcash.ui.common.model.KeystoneAccount } == true
        val restoring = walletRestoringState == WalletRestoringState.RESTORING
        return AdvancedSettingsState(
            onBack = ::onBack,
            items =
                listOfNotNull(
                    ListItemState(
                        title = stringRes(R.string.settings_recoveryPhrase),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_recovery),
                        onClick = ::onSeedRecoveryClick
                    ),
                    ListItemState(
                        title = stringRes(R.string.settings_exportPrivateData),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_export),
                        onClick = ::onExportPrivateDataClick
                    ),
                    ListItemState(
                        title = stringRes(R.string.taxExport_taxFile),
                        bigIcon =
                            imageRes(
                                if (walletRestoringState == WalletRestoringState.RESTORING) {
                                    R.drawable.ic_advanced_settings_tax_disabled
                                } else {
                                    R.drawable.ic_advanced_settings_tax
                                }
                            ),
                        isEnabled = !restoring,
                        onClick = ::onTaxExportClick
                    ),
                    ListItemState(
                        title = stringRes(R.string.settings_chooseServer),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_choose_server),
                        onClick = ::onChooseServerClick
                    ),
                    // ListItemState(
                    //     title = stringRes(R.string.resyncWallet_title),
                    //     bigIcon = imageRes(R.drawable.ic_advanced_settings_resync),
                    //     isEnabled = !restoring,
                    //     onClick = ::onResyncWalletClick,
                    // ),
                    ListItemState(
                        title = stringRes(R.string.settings_private),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_privacy),
                        onClick = ::onPrivacyClick
                    ),
                    ListItemState(
                        title = stringRes(R.string.advanced_settings_crash_reporting),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_crash_reporting),
                        onClick = ::onCrashReportingClick
                    ).takeIf { versionInfo.distribution == DistributionDimension.STORE },
                    ListItemState(
                        title = stringRes(R.string.disconnectHWWallet_cta),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_disconnect_hw),
                        onClick = ::onDisconnectHwWalletClick
                    ).takeIf { hasKeystoneAccount },
                    ListItemState(
                        title = stringRes(co.electriccoin.zcash.ui.design.R.string.restartMigration_settingsItem),
                        bigIcon = imageRes(R.drawable.ic_advanced_settings_restart_migration),
                        onClick = ::onRestartMigrationClick,
                    ).takeIf { isRestartAvailable },
                    ListItemState(
                        title = stringRes("Debug menu"),
                        onClick = ::onDebugMenuClick
                    ).takeIf { BuildConfig.DEBUG },
                ),
            deleteButton =
                ButtonState(
                    text = stringRes(R.string.settings_deleteZashi),
                    onClick = ::onResetWalletClick,
                ),
        )
    }

    private fun onPrivacyClick() = navigationRouter.forward(TorSettingsArgs)

    fun onBack() = navigationRouter.back()

    private fun onChooseServerClick() = navigationRouter.forward(ChooseServerArgs)

    private fun onDebugMenuClick() = navigationRouter.forward(DebugArgs)

    private fun onCrashReportingClick() = navigationRouter.forward(NavigationTargets.CRASH_REPORTING_OPT_IN)

    private fun onTaxExportClick() = viewModelScope.launch { navigateToTaxExport() }

    private fun onSeedRecoveryClick() =
        viewModelScope.launch {
            navigateToWalletBackup(isOpenedFromSeedBackupInfo = false)
        }

    private fun onExportPrivateDataClick() = viewModelScope.launch { navigateToExportPrivateData() }

    private fun onResetWalletClick() = viewModelScope.launch { navigateToResetWallet() }

    private fun onDisconnectHwWalletClick() {
        navigationRouter.forward(DisconnectArgs)
    }

    private fun onRestartMigrationClick() = migrationNavigator.forwardToRestartMigration()

    // private fun onResyncWalletClick() = navigationRouter.forward(ResyncConfirmArgs)
}
