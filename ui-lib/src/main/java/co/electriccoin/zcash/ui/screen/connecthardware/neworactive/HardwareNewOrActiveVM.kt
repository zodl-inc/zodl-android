package co.electriccoin.zcash.ui.screen.connecthardware.neworactive

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.CreateHardwareWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.screen.connecthardware.brandingOf
import co.electriccoin.zcash.ui.screen.connecthardware.date.HardwareDateArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Asks whether the device being enrolled is new or has been used before: a new one imports from
 * the chain tip, an active one goes on to the birthday question.
 */
class HardwareNewOrActiveVM(
    private val args: HardwareNewOrActiveArgs,
    private val createHardwareWalletAccount: CreateHardwareWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val branding = brandingOf(args.enrollment)
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<HardwareNewOrActiveState>> =
        createAccountLce.state
            .map { lce ->
                HardwareNewOrActiveState(
                    logo = branding.logo,
                    subtitle = branding.deviceQuestion,
                    message = branding.deviceDescription,
                    newDevice =
                        ButtonState(
                            text = branding.connectNewDevice,
                            isLoading = lce.loading,
                            onClick = ::onNewDeviceClick,
                            hapticFeedbackType = HapticFeedbackType.Confirm,
                        ),
                    activeDevice =
                        ButtonState(
                            text = branding.connectActiveDevice,
                            isEnabled = !lce.loading,
                            onClick = ::onActiveDeviceClick,
                        ),
                    newDeviceTestTag = branding.newDeviceTestTag,
                    activeDeviceTestTag = branding.activeDeviceTestTag,
                    onBack = ::onBack,
                )
            }.withLce(createAccountLce, errorStateMapper::mapToState)
            .stateIn(this)

    init {
        if (!createHardwareWalletAccount.isReady(args.enrollment)) {
            navigationRouter.backToRoot()
        }
    }

    private fun onNewDeviceClick() =
        createAccountLce.execute {
            createHardwareWalletAccount(args.enrollment, birthday = null)
        }

    private fun onActiveDeviceClick() =
        createAccountLce.guardLoading {
            navigationRouter.forward(HardwareDateArgs(args.enrollment))
        }

    private fun onBack() =
        createAccountLce.guardLoading {
            navigationRouter.back()
        }
}
