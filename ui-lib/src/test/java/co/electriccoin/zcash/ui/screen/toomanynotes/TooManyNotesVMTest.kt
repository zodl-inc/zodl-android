package co.electriccoin.zcash.ui.screen.toomanynotes

import co.electriccoin.zcash.ui.common.usecase.NavigateToTooManyNotesUseCase
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertNotNull

class TooManyNotesVMTest {
    private val navigateToTooManyNotes = mockk<NavigateToTooManyNotesUseCase>(relaxed = true)
    private val args = TooManyNotesArgs()

    private fun vm() = TooManyNotesVM(args, navigateToTooManyNotes)

    @Test
    fun okAnswersTheCallerWithOk() {
        val state = assertNotNull(vm().state.value)

        state.ok.onClick()

        verify(exactly = 1) { navigateToTooManyNotes.onOk(args) }
        verify(exactly = 0) { navigateToTooManyNotes.onDismissed(any()) }
    }

    @Test
    fun backScrimAndDragAnswerTheCallerWithDismissed() {
        val state = assertNotNull(vm().state.value)

        state.onBack()

        verify(exactly = 1) { navigateToTooManyNotes.onDismissed(args) }
        verify(exactly = 0) { navigateToTooManyNotes.onOk(any()) }
    }
}
