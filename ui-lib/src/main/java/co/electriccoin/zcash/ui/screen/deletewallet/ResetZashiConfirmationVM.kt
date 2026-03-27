package co.electriccoin.zcash.ui.screen.deletewallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResetZashiConfirmationVM(
    private val args: ResetZashiConfirmationArgs,
    private val resetZashi: ResetZashiUseCase,
    private val navigationRouter: NavigationRouter
) : ViewModel() {
    val state: StateFlow<ZashiConfirmationState?> =
        MutableStateFlow(createBottomSheetState())
            .asStateFlow()

    private var resetJob: Job? = null

    private fun createBottomSheetState(): ZashiConfirmationState =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = stringRes(R.string.delete_wallet_confirmation_title),
            message = stringRes(R.string.delete_wallet_confirmation_subtitle),
            primaryAction =
                ButtonState(
                    text = stringRes(R.string.delete_wallet_confirmation_button),
                    style = ButtonStyle.DESTRUCTIVE2,
                    onClick = ::onConfirmClick
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(R.string.delete_wallet_confirmation_cancel),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::onDismissBottomSheet
                ),
            onBack = ::onDismissBottomSheet
        )

    private fun onDismissBottomSheet() = navigationRouter.back()

    private fun onConfirmClick() {
        if (resetJob?.isActive == true) return
        resetJob = viewModelScope.launch { resetZashi(keepFiles = args.keepFiles) }
    }
}
