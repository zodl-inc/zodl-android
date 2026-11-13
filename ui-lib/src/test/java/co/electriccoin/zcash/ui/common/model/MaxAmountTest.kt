package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.ext.toZecExact
import cash.z.ecc.android.sdk.model.Zatoshi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaxAmountTest {
    private val onClick = {}

    @Test
    fun floorNeverRoundsUp() {
        assertEquals("1.23", "1.239".toBigDecimal().floorToScale(2).toPlainString())
        assertEquals("1.23", "1.231".toBigDecimal().floorToScale(2).toPlainString())
    }

    @Test
    fun floorPadsToTheScale() {
        assertEquals("1.50", "1.5".toBigDecimal().floorToScale(2).toPlainString())
    }

    @Test
    fun floorKeepsAValueAlreadyAtTheScale() {
        assertEquals("0.00000001", "0.00000001".toBigDecimal().floorToScale(8).toPlainString())
    }

    /** The SDK conversion this file relies on keeps every zatoshi, unlike the 6-decimal display conversion. */
    @Test
    fun exactZecKeepsAllEightDecimals() {
        assertEquals("1.23456789", Zatoshi(123_456_789).toZecExact().toPlainString())
    }

    @Test
    fun aSpendableAccountGivesAnEnabledChip() {
        val state = maxChipState(Spendability.Some(Zatoshi(1)), isExtraEnabled = true, onClick)

        assertTrue(state.isEnabled)
        assertSame(onClick, state.onClick)
    }

    @Test
    fun theScreenConditionCanDisableTheChip() {
        val state = maxChipState(Spendability.Some(Zatoshi(1)), isExtraEnabled = false, onClick)

        assertFalse(state.isEnabled)
    }

    @Test
    fun nothingToSpendDisablesTheChip() {
        Spendability.Nothing.Reason.entries.forEach {
            val state = maxChipState(Spendability.Nothing(it), isExtraEnabled = true, onClick)

            assertFalse(state.isEnabled, it.name)
        }
    }

    @Test
    fun undeterminedBalancesDisableTheChip() {
        val state = maxChipState(Spendability.Determining, isExtraEnabled = true, onClick)

        assertFalse(state.isEnabled)
    }
}
