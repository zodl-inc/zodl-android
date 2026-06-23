package co.electriccoin.zcash.ui.screen.resync.height

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ConfirmResyncUseCase
import co.electriccoin.zcash.ui.common.usecase.ResyncErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.BlockHeightState
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class ResyncHeightVM(
    private val navigationRouter: NavigationRouter,
    private val confirmResync: ConfirmResyncUseCase,
    private val errorStateMapper: ResyncErrorMapperUseCase,
) : ViewModel() {
    private val blockHeightText = MutableStateFlow(NumberTextFieldInnerState())
    private val confirmLce = mutableLce<Unit>()

    val state: StateFlow<LceState<BlockHeightState>> =
        combine(blockHeightText, confirmLce.state) { text, confirm ->
            createState(text, confirm.loading)
        }.withLce(confirmLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(blockHeightText.value, false)))

    private fun createState(
        blockHeight: NumberTextFieldInnerState,
        isConfirming: Boolean,
    ): BlockHeightState {
        val isHigherThanSaplingActivationHeight =
            blockHeight.amount
                ?.let { it.toLong() >= VersionInfo.NETWORK.saplingActivationHeight.value }
                ?: false
        val isValid = !blockHeight.innerTextFieldState.value.isEmpty() && isHigherThanSaplingActivationHeight

        return BlockHeightState(
            title = stringRes(R.string.resyncWallet_title),
            logo = null,
            onBack = ::onBack,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoClick,
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_confirm),
                    onClick = ::onConfirmClick,
                    isEnabled = isValid && !isConfirming,
                    isLoading = isConfirming,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                ),
            secondaryButton = null,
            blockHeight = NumberTextFieldState(innerState = blockHeight, onValueChange = ::onValueChanged),
        )
    }

    private fun onConfirmClick() {
        val heightValue = blockHeightText.value.amount?.toLong() ?: return
        confirmLce.execute {
            confirmResync(BlockHeight.new(heightValue))
        }
    }

    private fun onBack() = navigationRouter.back()

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }
}
