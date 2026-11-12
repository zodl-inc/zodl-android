package co.electriccoin.zcash.ui.common.viewmodel

import cash.z.ecc.android.sdk.model.Zatoshi
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxFillPolicyTest {
    private val fee = 10_000L

    private fun refresh(
        spendable: Long,
        baseline: Long = BASELINE,
        adjust: (Zatoshi) -> Zatoshi = { it }
    ) = MaxFillPolicy.refresh(Zatoshi(baseline), Zatoshi(spendable), adjust).value

    @Test
    fun netSpendableSubtractsTheReserveCapturedAtTap() {
        assertEquals(85_000, MaxFillPolicy.netSpendable(Zatoshi(100_000), Zatoshi(15_000)).value)
    }

    @Test
    fun netSpendableFloorsAtZero() {
        assertEquals(0, MaxFillPolicy.netSpendable(Zatoshi(10_000), Zatoshi(15_000)).value)
    }

    /** 3 notes: real fee 15_000 plus a 5_000 buffer, so the reserve is 20_000, not one marginal fee. */
    @Test
    fun aDropOfOneMarginalFeeLowersTheFillByThatMuchForAMultiNoteProposal() {
        val reserve = Zatoshi(20_000)
        val spendableAtTap = 110_000L
        val baseline = spendableAtTap - reserve.value
        val net = MaxFillPolicy.netSpendable(Zatoshi(spendableAtTap - 5_000), reserve)
        assertEquals(baseline - 5_000, refresh(spendable = net.value, baseline = baseline))
    }

    @Test
    fun aNetSpendableJustBelowTheBaselineLowersTheFill() {
        // gross spendable 95_000 is above the 90_000 baseline, but net of the 20_000 reserve it is 75_000
        val net = MaxFillPolicy.netSpendable(Zatoshi(95_000), Zatoshi(20_000))
        assertEquals(75_000, refresh(spendable = net.value))
    }

    @Test
    fun fillIsTheBaselineWhileSpendableCoversIt() = assertEquals(BASELINE, refresh(spendable = BASELINE))

    @Test
    fun fillLowersWhenSpendableDropsAndRestoresWhenItReturns() {
        assertEquals(60_000, refresh(spendable = 60_000))
        assertEquals(BASELINE, refresh(spendable = BASELINE))
    }

    @Test
    fun newFundsDoNotRaiseTheFillAboveTheBaseline() = assertEquals(BASELINE, refresh(spendable = 500_000))

    @Test
    fun adjustAppliesToTheCappedAmount() {
        val seen = mutableListOf<Long>()
        val fill =
            refresh(spendable = 500_000) {
                seen += it.value
                Zatoshi(it.value - fee)
            }
        assertEquals(listOf(BASELINE), seen)
        assertEquals(BASELINE - fee, fill)
    }

    @Test
    fun anAdjustResultAboveTheBaselineIsClamped() =
        assertEquals(BASELINE, refresh(spendable = BASELINE) { Zatoshi(it.value + fee) })

    private companion object {
        const val BASELINE = 90_000L
    }
}
