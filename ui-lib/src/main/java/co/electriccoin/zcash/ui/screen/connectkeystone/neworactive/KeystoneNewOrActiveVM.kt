package co.electriccoin.zcash.ui.screen.connectkeystone.neworactive

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectkeystone.date.KeystoneDateArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class KeystoneNewOrActiveVM(
    private val args: KeystoneNewOrActiveArgs,
    parseKeystoneUrToZashiAccounts: ParseKeystoneUrToZashiAccountsUseCase,
    private val createKeystoneAccount: CreateKeystoneAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val accounts = parseKeystoneUrToZashiAccounts(args.ur)
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<KeystoneNewOrActiveState>> =
        createAccountLce.state
            .map { lce ->
                KeystoneNewOrActiveState(
                    subtitle = stringRes(R.string.keystone_new_or_active_subtitle),
                    message = stringRes(R.string.keystone_new_or_active_message),
                    newDevice =
                        ButtonState(
                            text = stringRes(R.string.keystone_new_device_button),
                            isLoading = lce.loading,
                            onClick = ::onNewDeviceClick,
                            hapticFeedbackType = HapticFeedbackType.Confirm,
                        ),
                    activeDevice =
                        ButtonState(
                            text = stringRes(R.string.keystone_active_device_button),
                            isEnabled = !lce.loading,
                            onClick = ::onActiveDeviceClick,
                        ),
                    onBack = ::onBack,
                )
            }.withLce(createAccountLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun onNewDeviceClick() =
        createAccountLce.execute {
            val account = accounts.accounts.firstOrNull() ?: throw IllegalStateException("No account loaded")
            createKeystoneAccount(accounts, account, birthday = null)
        }

    private fun onActiveDeviceClick() =
        createAccountLce.guardLoading {
            navigationRouter.forward(KeystoneDateArgs(args.ur))
        }

    private fun onBack() =
        createAccountLce.guardLoading {
            navigationRouter.back()
        }
}
