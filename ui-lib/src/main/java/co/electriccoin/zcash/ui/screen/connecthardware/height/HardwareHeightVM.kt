package co.electriccoin.zcash.ui.screen.connecthardware.height

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.CreateHardwareWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.screen.common.BlockHeightState
import co.electriccoin.zcash.ui.screen.connecthardware.brandingOf
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Takes a birthday height typed by hand and imports the enrolled account at it.
 */
class HardwareHeightVM(
    private val args: HardwareHeightArgs,
    private val createHardwareWalletAccount: CreateHardwareWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val branding = brandingOf(args.enrollment)
    private val blockHeightText = MutableStateFlow(NumberTextFieldInnerState())
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<BlockHeightState>> =
        combine(blockHeightText, createAccountLce.state) { text, lce ->
            val isHigherThanSaplingActivationHeight =
                text.amount
                    ?.let { it.toLong() >= VersionInfo.NETWORK.saplingActivationHeight.value }
                    ?: false
            val isValid = !text.innerTextFieldState.value.isEmpty() && isHigherThanSaplingActivationHeight

            BlockHeightState(
                title = null,
                logo = branding.logo,
                onBack = ::onBack,
                dialogButton =
                    IconButtonState(
                        icon = R.drawable.ic_help,
                        onClick = ::onInfoClick,
                    ),
                primaryButton =
                    ButtonState(
                        text = branding.connect,
                        onClick = { text.amount?.toLong()?.let { onConfirmClick(it) } },
                        isEnabled = isValid && !lce.loading,
                        isLoading = lce.loading,
                        hapticFeedbackType = HapticFeedbackType.Confirm,
                    ),
                secondaryButton = null,
                blockHeight = NumberTextFieldState(innerState = text, onValueChange = ::onValueChanged),
                primaryButtonTestTag = branding.connectTestTag,
                blockHeightFieldTestTag = branding.blockHeightFieldTestTag,
            )
        }.withLce(createAccountLce, errorStateMapper::mapToState)
            .stateIn(this)

    init {
        if (!createHardwareWalletAccount.isReady(args.enrollment)) {
            navigationRouter.backToRoot()
        }
    }

    private fun onConfirmClick(height: Long) {
        createAccountLce.execute {
            createHardwareWalletAccount(args.enrollment, BlockHeight.new(height))
        }
    }

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onBack() = createAccountLce.guardLoading { navigationRouter.back() }

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }
}
