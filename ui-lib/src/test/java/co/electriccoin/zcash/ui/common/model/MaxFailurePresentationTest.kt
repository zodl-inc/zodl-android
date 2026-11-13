package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxFailurePresentationTest {
    @Test
    fun onlyTooManyNotesIsABlockingSheet() {
        assertEquals(MaxFailurePresentation.SHEET, MaxSendAmountFailure.TOO_MANY_NOTES.presentation)
        MaxSendAmountFailure.entries
            .filter { it != MaxSendAmountFailure.TOO_MANY_NOTES }
            .forEach { assertEquals(MaxFailurePresentation.INLINE, it.presentation, it.name) }
    }
}
