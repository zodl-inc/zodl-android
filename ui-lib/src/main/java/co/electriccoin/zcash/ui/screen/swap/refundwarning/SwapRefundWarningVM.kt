package co.electriccoin.zcash.ui.screen.swap.refundwarning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapRefundWarningUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SwapRefundWarningVM(
    private val args: SwapRefundWarningArgs,
    private val navigateToSwapRefundWarning: NavigateToSwapRefundWarningUseCase,
) : ViewModel() {
    private val isChecked = MutableStateFlow(false)

    val state: StateFlow<SwapRefundWarningState> =
        isChecked
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(isChecked.value)
            )

    private fun createState(isChecked: Boolean) =
        SwapRefundWarningState(
            title = stringRes(R.string.swap_refund_warning_title),
            message =
                when (args.mode) {
                    EXACT_INPUT, FLEX_INPUT -> stringRes(R.string.swap_refund_warning_message_swap)
                    EXACT_OUTPUT -> stringRes(R.string.swap_refund_warning_message_pay)
                },
            checkbox =
                CheckboxState(
                    title = stringRes(R.string.swap_refund_warning_checkbox),
                    isChecked = isChecked,
                    onClick = ::onCheckboxClick
                ),
            cancelButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
                    onClick = ::onCancelClick
                ),
            continueButton =
                ButtonState(
                    text = stringRes(R.string.swap_refund_warning_continue),
                    onClick = ::onContinueClick
                ),
            onBack = ::onCancelClick
        )

    private fun onCheckboxClick() = isChecked.update { !it }

    private fun onContinueClick() =
        viewModelScope.launch {
            navigateToSwapRefundWarning.onContinue(args, isChecked.value)
        }

    private fun onCancelClick() =
        viewModelScope.launch {
            navigateToSwapRefundWarning.onCancel(args)
        }
}
