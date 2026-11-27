package co.electriccoin.zcash.ui.design.component

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SheetOpenFractionTest {
    @Test
    fun fullyOpenSheetReportsOne() {
        assertEquals(1f, sheetOpenFraction(rootHeight = 2000, sheetTop = 1400f, sheetHeight = 600))
    }

    @Test
    fun sheetBelowTheRootReportsZero() {
        assertEquals(0f, sheetOpenFraction(rootHeight = 2000, sheetTop = 2000f, sheetHeight = 600))
        assertEquals(0f, sheetOpenFraction(rootHeight = 2000, sheetTop = 2300f, sheetHeight = 600))
    }

    @Test
    fun halfShownSheetReportsHalf() {
        assertEquals(0.5f, sheetOpenFraction(rootHeight = 2000, sheetTop = 1700f, sheetHeight = 600))
    }

    @Test
    fun sheetTallerThanTheRootIsOpenOnceItFillsTheRoot() {
        assertEquals(1f, sheetOpenFraction(rootHeight = 2000, sheetTop = 0f, sheetHeight = 2400))
        assertEquals(0.5f, sheetOpenFraction(rootHeight = 2000, sheetTop = 1000f, sheetHeight = 2400))
    }

    @Test
    fun sheetWithoutHeightReportsNothing() {
        assertNull(sheetOpenFraction(rootHeight = 2000, sheetTop = 2000f, sheetHeight = 0))
        assertNull(sheetOpenFraction(rootHeight = 0, sheetTop = 0f, sheetHeight = 600))
    }
}
