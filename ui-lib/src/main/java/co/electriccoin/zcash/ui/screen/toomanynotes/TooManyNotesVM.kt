package co.electriccoin.zcash.ui.screen.toomanynotes

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.common.usecase.NavigateToTooManyNotesUseCase
import co.electriccoin.zcash.ui.design.component.MaxTooManyNotesSheetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TooManyNotesVM(
    private val args: TooManyNotesArgs,
    private val navigateToTooManyNotes: NavigateToTooManyNotesUseCase
) : ViewModel() {
    val state: StateFlow<MaxTooManyNotesSheetState?> =
        MutableStateFlow<MaxTooManyNotesSheetState?>(
            MaxTooManyNotesSheetState.create(
                onOk = { navigateToTooManyNotes.onOk(args) },
                onDismiss = { navigateToTooManyNotes.onDismissed(args) }
            )
        ).asStateFlow()

    override fun onCleared() {
        navigateToTooManyNotes.onAbandoned(args)
        super.onCleared()
    }
}
