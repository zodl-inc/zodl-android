package co.electriccoin.zcash.ui.common.viewmodel

import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.model.MaxFailurePresentation
import co.electriccoin.zcash.ui.common.model.presentation
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxTooManyNotesSheetState
import co.electriccoin.zcash.ui.design.component.MaxVariant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Send, Swap and Pay tell the user when Max fails: the non-blocking one-line [error] or the blocking
 * too-many-notes [sheet] (Figma "Send Max: Round 2"). [variant] picks the "send" or "swap" copy of the error.
 *
 * The sheet's OK dismisses it, calls [onClearAmount] (the screen owns its amount) and emits [focusRequests], which
 * the screen hands to [co.electriccoin.zcash.ui.design.component.ZashiMaxTooManyNotesSheet] to focus the ZEC amount
 * field once the sheet is gone.
 */
class MaxFeedbackController(
    variant: MaxVariant,
    private val onClearAmount: () -> Unit,
) {
    private val unavailableError = MaxFeedback.Error(variant)

    private val tooManyNotesSheet =
        MaxTooManyNotesSheetState.create(
            onOk = ::onOk,
            onDismiss = ::dismissSheet
        )

    private val errorFlow = MutableStateFlow<MaxFeedback.Error?>(null)
    private val sheetFlow = MutableStateFlow<MaxTooManyNotesSheetState?>(null)
    private val focusRequestsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The one-line "Max isn't available" error, or null. The screen clears it on a retap, edit or change. */
    val error: StateFlow<MaxFeedback.Error?> = errorFlow.asStateFlow()

    /** The too-many-notes sheet while it is shown, null otherwise. */
    val sheet: StateFlow<MaxTooManyNotesSheetState?> = sheetFlow.asStateFlow()

    /** Emits when the amount field should take the focus, after OK. */
    val focusRequests: SharedFlow<Unit> = focusRequestsFlow.asSharedFlow()

    /** Shows how a failed Max request ends: the sheet for too many notes, the error line otherwise. */
    fun onFailed(failure: MaxSendAmountFailure) {
        when (failure.presentation) {
            MaxFailurePresentation.SHEET -> showSheet()
            MaxFailurePresentation.INLINE -> errorFlow.value = unavailableError
        }
    }

    fun clearError() {
        errorFlow.value = null
    }

    /** Shows the too-many-notes sheet, which replaces the error line. */
    fun showSheet() {
        clearError()
        sheetFlow.value = tooManyNotesSheet
    }

    /**
     * Asks the screen to focus the amount field, for a too-many-notes OK that came back from a destination (swap and
     * pay quote) instead of from [sheet]; the caller clears its own amount first.
     */
    fun requestAmountFocus() {
        focusRequestsFlow.tryEmit(Unit)
    }

    private fun dismissSheet() {
        sheetFlow.value = null
    }

    private fun onOk() {
        dismissSheet()
        onClearAmount()
        requestAmountFocus()
    }
}
