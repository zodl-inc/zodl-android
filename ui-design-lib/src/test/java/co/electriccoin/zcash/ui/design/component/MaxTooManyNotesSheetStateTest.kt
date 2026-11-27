package co.electriccoin.zcash.ui.design.component

import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxTooManyNotesSheetStateTest {
    private var okClicks = 0
    private var dismissals = 0

    private val state =
        MaxTooManyNotesSheetState.create(
            onOk = { okClicks++ },
            onDismiss = { dismissals++ }
        )

    @Test
    fun theSheetUsesTheDesignedCopyAndASinglePrimaryOk() {
        assertEquals(stringRes(R.string.general_maxTooManyNotesTitle), state.title)
        assertEquals(stringRes(R.string.general_maxTooManyNotesBody), state.body)
        assertEquals(stringRes(R.string.general_ok), state.ok.text)
        assertEquals(ButtonStyle.PRIMARY, state.ok.style)
    }

    @Test
    fun okOnlyInvokesItsCallback() {
        state.ok.onClick()

        assertEquals(1, okClicks)
        assertEquals(0, dismissals)
    }

    @Test
    fun backOnlyDismisses() {
        state.onBack()

        assertEquals(0, okClicks)
        assertEquals(1, dismissals)
    }

    @Test
    fun closeOnlyOkAndBackBothDismiss() {
        var closes = 0
        val closeOnly = MaxTooManyNotesSheetState.createCloseOnly(onClose = { closes++ })

        assertEquals(stringRes(R.string.general_ok), closeOnly.ok.text)
        closeOnly.ok.onClick()
        closeOnly.onBack()

        assertEquals(2, closes)
    }
}
