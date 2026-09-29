package co.electriccoin.zcash.ui.screen.connecthw.neworactive

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.CreateHWWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.screen.connecthw.brandingOf
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateArgs
import co.electriccoin.zcash.ui.screen.connecthw.importOrReturnToRoot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Asks whether the device being enrolled is new or has been used before: a new one imports from
 * the chain tip, an active one goes on to the birthday question.
 */
class HWNewOrActiveVM(
    private val args: HWNewOrActiveArgs,
    private val createHWWalletAccount: CreateHWWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val branding = brandingOf(args.enrollment)
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<HWNewOrActiveState>> =
        createAccountLce.state
            .map { lce ->
                HWNewOrActiveState(
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
        if (!createHWWalletAccount.isReady(args.enrollment)) {
            navigationRouter.backToRoot()
        }
    }

    private fun onNewDeviceClick() =
        createAccountLce.execute {
            createHWWalletAccount.importOrReturnToRoot(args.enrollment, birthday = null, navigationRouter)
        }

    private fun onActiveDeviceClick() =
        createAccountLce.guardLoading {
            navigationRouter.forward(HWDateArgs(args.enrollment))
        }

    private fun onBack() =
        createAccountLce.guardLoading {
            navigationRouter.back()
        }
}
