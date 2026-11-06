package co.electriccoin.zcash.ui.design.component

import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxTooManyNotesSheetStateTest {
    private var sendSmallerClicks = 0
    private var closeClicks = 0

    private val state =
        MaxTooManyNotesSheetState.create(
            onSendSmallerAmount = { sendSmallerClicks++ },
            onClose = { closeClicks++ }
        )

    @Test
    fun theSheetUsesTheDesignedCopyAndStyles() {
        assertEquals(stringRes(R.string.general_maxTooManyNotesTitle), state.title)
        assertEquals(stringRes(R.string.general_maxTooManyNotesBody), state.body)
        assertEquals(stringRes(R.string.general_maxTooManyNotesCta), state.sendSmallerAmount.text)
        assertEquals(ButtonStyle.PRIMARY, state.sendSmallerAmount.style)
        assertEquals(stringRes(R.string.general_maxTooManyNotesClose), state.close.text)
        assertEquals(ButtonStyle.SECONDARY, state.close.style)
    }

    @Test
    fun sendSmallerAmountOnlyInvokesItsCallback() {
        state.sendSmallerAmount.onClick()

        assertEquals(1, sendSmallerClicks)
        assertEquals(0, closeClicks)
    }

    @Test
    fun closeAndBackBothOnlyDismiss() {
        state.close.onClick()
        state.onBack()

        assertEquals(0, sendSmallerClicks)
        assertEquals(2, closeClicks)
    }
}
