package co.electriccoin.zcash.ui.screen.balances

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.LoadedAccountBalances
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class SpendablePillStateTest {
    private val onClick = {}

    private fun balances(
        spendable: Long = 0,
        pending: Long = 0,
        shielded: Long = spendable + pending,
        transparent: Long = 0,
    ) = LoadedAccountBalances(
        isAllShielded = transparent == 0L,
        totalBalance = Zatoshi(shielded + transparent),
        totalShieldedBalance = Zatoshi(shielded),
        totalTransparentBalance = Zatoshi(transparent),
        spendableShieldedBalance = Zatoshi(spendable),
        pendingShieldedBalance = Zatoshi(pending),
        isShieldedPending = pending > 0,
        isShieldingAvailable = transparent > 0,
        transparentBalance = Zatoshi(transparent),
    )

    private fun pill(balances: LoadedAccountBalances?) = spendablePillState(balances, onClick)

    @Test
    fun syncingShowsASpinnerPill() {
        val pill = assertNotNull(pill(null))

        assertNull(pill.amount)
        assertSame(onClick, pill.onClick)
    }

    @Test
    fun noFundsHasNoPill() {
        assertNull(pill(balances()))
    }

    @Test
    fun allSpendableStillShowsThePill() {
        assertEquals(Zatoshi(100), assertNotNull(pill(balances(spendable = 100))).amount)
    }

    @Test
    fun partlySpendableShowsTheSpendableAmount() {
        assertEquals(Zatoshi(100), assertNotNull(pill(balances(spendable = 100, pending = 20, transparent = 5))).amount)
        assertEquals(Zatoshi(100), assertNotNull(pill(balances(spendable = 100, transparent = 5))).amount)
    }

    @Test
    fun confirmingShowsASpinner() {
        assertNull(assertNotNull(pill(balances(pending = 50))).amount)
    }

    @Test
    fun shieldedFundsNotYetSpendableShowSpinnerWithoutPendingChange() {
        assertNull(assertNotNull(pill(balances(shielded = 50))).amount)
    }

    @Test
    fun transparentOnlyShowsZeroNotASpinner() {
        assertEquals(Zatoshi(0), assertNotNull(pill(balances(transparent = 500))).amount)
    }
}
