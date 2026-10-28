package co.electriccoin.zcash.ui.screen.connecthw.height

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
import co.electriccoin.zcash.ui.common.usecase.CreateHWWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.screen.common.BlockHeightState
import co.electriccoin.zcash.ui.screen.connecthw.brandingOf
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Takes a birthday height typed by hand and imports the enrolled hardware-wallet account at it; a
 * view-only import stores the height and goes on to name the account instead.
 */
class HWHeightVM(
    private val args: HWHeightArgs,
    private val createHWWalletAccount: CreateHWWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    viewOnlyContent: ViewOnlyContent,
) : ViewModel() {
    private val branding = brandingOf(args.enrollment, viewOnlyContent)
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
        if (!createHWWalletAccount.isReady(args.enrollment)) {
            navigationRouter.backToRoot()
        }
    }

    /**
     * A second tap while the import runs is ignored: restarting would cancel the running import
     * inside the SDK call, after the account exists but before it is selected.
     */
    private fun onConfirmClick(height: Long) =
        createAccountLce.guardLoading {
            createAccountLce.execute {
                createHWWalletAccount(args.enrollment, BlockHeight.new(height))
            }
        }

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onBack() = createAccountLce.guardLoading { navigationRouter.back() }

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }
}
