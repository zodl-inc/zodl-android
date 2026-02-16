package co.electriccoin.zcash.ui.screen.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.GetFlexaStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetKeystoneStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletRestoringStateUseCase
import co.electriccoin.zcash.ui.common.usecase.Status
import co.electriccoin.zcash.ui.common.usecase.Status.DISABLED
import co.electriccoin.zcash.ui.common.usecase.Status.ENABLED
import co.electriccoin.zcash.ui.common.usecase.Status.UNAVAILABLE
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.ConnectKeystoneArgs
import co.electriccoin.zcash.ui.screen.flexa.Flexa
import co.electriccoin.zcash.ui.screen.more.MoreArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IntegrationsVM(
    getWalletRestoringState: GetWalletRestoringStateUseCase,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    getFlexaStatus: GetFlexaStatusUseCase,
    getKeystoneStatus: GetKeystoneStatusUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val isRestoring = getWalletRestoringState.observe().map { it == WalletRestoringState.RESTORING }

    val state =
        combine(
            isRestoring,
            getSelectedWalletAccount.observe(),
            getFlexaStatus.observe(),
            getKeystoneStatus.observe(),
        ) { isRestoring, selectedAccount, flexaStatus, keystoneStatus ->
            createState(
                isRestoring = isRestoring,
                selectedAccount = selectedAccount,
                flexaStatus = flexaStatus,
                keystoneStatus = keystoneStatus,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createState(
        isRestoring: Boolean,
        selectedAccount: WalletAccount?,
        flexaStatus: Status,
        keystoneStatus: Status
    ) = IntegrationsState(
        disabledInfo =
            when {
                isRestoring -> stringRes(R.string.integrations_disabled_info)
                selectedAccount is KeystoneAccount -> stringRes(R.string.integrations_disabled_info_flexa)
                else -> null
            },
        onBack = ::onBack,
        items =
            listOfNotNull(
                ListItemState(
                    // Set the wallet currency by app build is more future-proof, although we hide it from
                    // the UI in the Testnet build
                    isEnabled = isRestoring.not() && selectedAccount is ZashiAccount,
                    bigIcon =
                        imageRes(
                            when (flexaStatus) {
                                ENABLED -> R.drawable.ic_integrations_flexa
                                DISABLED -> R.drawable.ic_integrations_flexa_disabled
                                UNAVAILABLE -> R.drawable.ic_integrations_flexa_disabled
                            }
                        ),
                    title = stringRes(R.string.integrations_flexa),
                    subtitle = stringRes(R.string.integrations_flexa_subtitle),
                    onClick = ::onFlexaClicked
                ).takeIf { flexaStatus != UNAVAILABLE },
                ListItemState(
                    title = stringRes(R.string.integrations_keystone),
                    subtitle = stringRes(R.string.integrations_keystone_subtitle),
                    bigIcon = imageRes(R.drawable.ic_integrations_keystone),
                    onClick = ::onConnectKeystoneClick
                ).takeIf { keystoneStatus != UNAVAILABLE },
                ListItemState(
                    title =
                        stringRes(co.electriccoin.zcash.ui.design.R.string.general_more) +
                            stringRes("..."),
                    bigIcon = imageRes(R.drawable.ic_integrations_more),
                    onClick = ::onMoreClick
                ),
            ),
    )

    private fun onBack() = navigationRouter.back()

    private fun onConnectKeystoneClick() = viewModelScope.launch { navigationRouter.replace(ConnectKeystoneArgs) }

    private fun onFlexaClicked() = navigationRouter.replace(Flexa)

    private fun onMoreClick() = navigationRouter.forward(MoreArgs)
}
