package co.electriccoin.zcash.ui.screen.deletewallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.usecase.CreateLceErrorConfirmationStateUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class ResetZashiVM(
    private val navigationRouter: NavigationRouter,
    private val resetZashi: ResetZashiUseCase,
    private val createLceErrorConfirmationState: CreateLceErrorConfirmationStateUseCase,
) : ViewModel() {
    private val isKeepFilesChecked = MutableStateFlow(true)
    private val confirmationDialogFlow = MutableStateFlow<ZashiConfirmationState?>(null)
    private val resetLce = mutableLce<Unit>()

    val state: StateFlow<ResetZashiState?> =
        combine(
            isKeepFilesChecked,
            confirmationDialogFlow,
            resetLce.state,
        ) { isKeepFilesChecked, confirmationDialog, lce ->
            createState(
                isKeepFilesChecked = isKeepFilesChecked,
                confirmationDialog =
                    lce.error?.let { createLceErrorConfirmationState(it, viewModelScope) }
                        ?: confirmationDialog,
                isLoading = lce.loading,
            )
        }.stateIn(this)

    private fun createState(
        isKeepFilesChecked: Boolean,
        confirmationDialog: ZashiConfirmationState?,
        isLoading: Boolean,
    ) = ResetZashiState(
        onBack = { navigationRouter.back() },
        checkboxState =
            CheckboxState(
                title = stringRes(R.string.delete_wallet_checkbox_title),
                subtitle = stringRes(R.string.delete_wallet_checkbox_warning_checked),
                isChecked = isKeepFilesChecked,
                onClick = ::onCheckboxToggled
            ),
        buttonState =
            ButtonState(
                text = stringRes(R.string.delete_wallet_button),
                isLoading = isLoading,
                onClick = ::onConfirmClicked
            ),
        confirmationDialog = confirmationDialog,
    )

    private fun onCheckboxToggled() = isKeepFilesChecked.update { !it }

    private fun onConfirmClicked() {
        confirmationDialogFlow.value = createConfirmationState()
    }

    private fun createConfirmationState(): ZashiConfirmationState =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = stringRes(R.string.delete_wallet_confirmation_title),
            message = stringRes(R.string.delete_wallet_confirmation_subtitle),
            primaryAction =
                ButtonState(
                    text = stringRes(R.string.delete_wallet_confirmation_button),
                    style = ButtonStyle.DESTRUCTIVE2,
                    onClick = ::onConfirmReset
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(R.string.delete_wallet_confirmation_cancel),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::onDismissConfirmation
                ),
            onBack = ::onDismissConfirmation
        )

    private fun onDismissConfirmation() {
        confirmationDialogFlow.value = null
    }

    private fun onConfirmReset() {
        confirmationDialogFlow.value = null
        resetLce.execute {
            resetZashi(keepFiles = isKeepFilesChecked.value)
        }
    }
}
