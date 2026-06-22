package co.electriccoin.zcash.ui.screen.transactionnote.viewmodel

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CreateOrUpdateTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.DeleteTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionMetadataUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.transactionnote.TransactionNote
import co.electriccoin.zcash.ui.screen.transactionnote.model.TransactionNoteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class TransactionNoteViewModel(
    private val transactionNote: TransactionNote,
    private val navigationRouter: NavigationRouter,
    private val getTransactionNote: GetTransactionMetadataUseCase,
    private val createOrUpdateTransactionNote: CreateOrUpdateTransactionNoteUseCase,
    private val deleteTransactionNote: DeleteTransactionNoteUseCase
) : ViewModel() {
    private val noteText = MutableStateFlow("")
    private val foundNote = MutableStateFlow<String?>(null)

    val state: StateFlow<TransactionNoteState> =
        combine(noteText, foundNote) { noteText, foundNote ->
            createState(noteText = noteText, foundNote = foundNote)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(noteText = "", foundNote = null)
        )

    private fun createState(
        noteText: String,
        foundNote: String?
    ): TransactionNoteState {
        val noteTextNormalized = noteText.trim()
        val isNoteTextTooLong = noteText.length > MAX_NOTE_LENGTH
        return TransactionNoteState(
            onBack = ::onBack,
            title =
                if (foundNote == null) {
                    stringRes(R.string.transaction_note_add_note_title)
                } else {
                    stringRes(R.string.annotation_edit)
                },
            note =
                TextFieldState(
                    value = stringRes(noteText),
                    error = stringRes("").takeIf { isNoteTextTooLong },
                    onValueChange = ::onNoteTextChanged
                ),
            noteCharacters =
                stringRes(R.string.transaction_note_max_length, noteText.length.toString())
                    .withStyle(
                        StyledStringStyle(
                            color = if (isNoteTextTooLong) StringResourceColor.NEGATIVE else StringResourceColor.PRIMARY
                        )
                    ),
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.annotation_add),
                    onClick = ::onAddOrUpdateNoteClick,
                    isEnabled = !isNoteTextTooLong && noteTextNormalized.isNotEmpty(),
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ).takeIf { foundNote == null },
            secondaryButton =
                ButtonState(
                    text = stringRes(R.string.annotation_save),
                    onClick = ::onAddOrUpdateNoteClick,
                    isEnabled = !isNoteTextTooLong && noteTextNormalized.isNotEmpty(),
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ).takeIf { foundNote != null },
            negative =
                ButtonState(
                    text = stringRes(R.string.annotation_delete),
                    onClick = ::onDeleteNoteClick,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ).takeIf { foundNote != null },
        )
    }

    init {
        viewModelScope.launch {
            val metadata = getTransactionNote(transactionNote.txId)
            foundNote.update { metadata.note }
            noteText.update { metadata.note.orEmpty() }
        }
    }

    private fun onAddOrUpdateNoteClick() =
        viewModelScope.launch {
            createOrUpdateTransactionNote(txId = transactionNote.txId, note = noteText.value)
        }

    private fun onDeleteNoteClick() =
        viewModelScope.launch {
            deleteTransactionNote(transactionNote.txId)
        }

    private fun onNoteTextChanged(newValue: String) {
        noteText.update { newValue }
    }

    private fun onBack() = navigationRouter.back()
}

private const val MAX_NOTE_LENGTH = 90
