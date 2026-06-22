package co.electriccoin.zcash.ui.screen.deletewallet

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.destructive
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
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
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val isKeepFilesChecked = MutableStateFlow(true)
    private val confirmationDialogFlow = MutableStateFlow<ZashiConfirmationState?>(null)
    private val resetLce = mutableLce<Unit>()

    val state: StateFlow<LceState<ResetZashiState>> =
        combine(
            isKeepFilesChecked,
            confirmationDialogFlow,
            resetLce.state,
        ) { isKeepFilesChecked, confirmationDialog, lce ->
            createState(
                isKeepFilesChecked = isKeepFilesChecked,
                confirmationDialog = confirmationDialog,
                isLoading = lce.loading,
            )
        }.withLce(resetLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(isKeepFilesChecked.value, null, false)))

    private fun createState(
        isKeepFilesChecked: Boolean,
        confirmationDialog: ZashiConfirmationState?,
        isLoading: Boolean,
    ) = ResetZashiState(
        onBack = { navigationRouter.back() },
        checkboxState =
            CheckboxState(
                title = stringRes(R.string.deleteWallet_metadataWarn1),
                subtitle = stringRes(R.string.delete_wallet_checkbox_warning_checked),
                isChecked = isKeepFilesChecked,
                onClick = ::onCheckboxToggled
            ),
        buttonState =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_confirm),
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
        ZashiConfirmationState.destructive(
            title = stringRes(R.string.delete_wallet_confirmation_title),
            message = stringRes(R.string.deleteWallet_sheet_msg),
            primaryText = stringRes(R.string.delete_wallet_confirmation_button),
            secondaryText = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
            onPrimary = ::onConfirmReset,
            onBack = ::onDismissConfirmation,
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
