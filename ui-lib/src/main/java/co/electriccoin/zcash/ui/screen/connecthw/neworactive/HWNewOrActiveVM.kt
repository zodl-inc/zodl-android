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
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.screen.connecthw.brandingOf
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Asks whether the device being enrolled is new or has been used before: a new one imports from
 * the chain tip, an active one goes on to the birthday question. Only the hardware-wallet
 * enrollments ask it; a view-only import goes from its key straight to the birthday question, so
 * an enrollment whose branding has no new-or-active copy shows nothing and returns to the root.
 */
class HWNewOrActiveVM(
    private val args: HWNewOrActiveArgs,
    private val createHWWalletAccount: CreateHWWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    viewOnlyContent: ViewOnlyContent,
) : ViewModel() {
    private val branding = brandingOf(args.enrollment, viewOnlyContent)
    private val newOrActive = branding.newOrActive
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<HWNewOrActiveState>> =
        createAccountLce.state
            .map { lce ->
                newOrActive?.let {
                    HWNewOrActiveState(
                        logo = branding.logo,
                        subtitle = it.deviceQuestion,
                        message = it.deviceDescription,
                        newDevice =
                            ButtonState(
                                text = it.connectNewDevice,
                                isEnabled = !lce.loading,
                                isLoading = lce.loading,
                                onClick = ::onNewDeviceClick,
                                hapticFeedbackType = HapticFeedbackType.Confirm,
                            ),
                        activeDevice =
                            ButtonState(
                                text = it.connectActiveDevice,
                                isEnabled = !lce.loading,
                                onClick = ::onActiveDeviceClick,
                            ),
                        newDeviceTestTag = it.newDeviceTestTag,
                        activeDeviceTestTag = it.activeDeviceTestTag,
                        onBack = ::onBack,
                    )
                }
            }.withLce(createAccountLce, errorStateMapper::mapToState)
            .stateIn(this)

    init {
        if (newOrActive == null || !createHWWalletAccount.isReady(args.enrollment)) {
            navigationRouter.backToRoot()
        }
    }

    /**
     * A second tap while the import runs is ignored: restarting would cancel the running import
     * inside the SDK call, after the account exists but before it is selected.
     */
    private fun onNewDeviceClick() =
        createAccountLce.guardLoading {
            createAccountLce.execute {
                createHWWalletAccount(args.enrollment, birthday = null)
            }
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
