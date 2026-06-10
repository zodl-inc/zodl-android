package co.electriccoin.zcash.ui.common

import co.electriccoin.zcash.ui.common.extension.first
import kotlin.test.Test
import kotlin.test.assertEquals

class ListExtTest {
    @Test
    fun first_under() {
        val limited = listOf(1, 2, 3).first(2)

        assertEquals(2, limited.count())
        assertEquals(listOf(1, 2), limited)
    }

    @Test
    fun first_equal() {
        val limited = listOf(1, 2, 3).first(3)

        assertEquals(3, limited.count())
        assertEquals(listOf(1, 2, 3), limited)
    }

    @Test
    fun first_over() {
        val limited = listOf(1, 2, 3).first(5)

        assertEquals(3, limited.count())
        assertEquals(listOf(1, 2, 3), limited)
    }
}
