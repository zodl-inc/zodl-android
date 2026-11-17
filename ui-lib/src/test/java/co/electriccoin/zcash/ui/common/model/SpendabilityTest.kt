package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Zatoshi
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [spendability] separates "balances not emitted yet" (the only spinner state) from "nothing to spend", so a
 * wallet whose funds are all pending no longer waits forever.
 */
class SpendabilityTest {
    /** [isShieldingAvailable] stands for transparent funds above the shielding threshold; null is "not loaded". */
    private fun account(spendable: Long?, pending: Long?, isShieldingAvailable: Boolean? = false) =
        mockk<WalletAccount> {
            every { spendableShieldedBalance } returns spendable?.let { Zatoshi(it) }
            every { pendingShieldedBalance } returns pending?.let { Zatoshi(it) }
            every { isShieldedPending } returns pending?.let { it > 0 }
            every { this@mockk.isShieldingAvailable } returns isShieldingAvailable
        }

    @Test
    fun unloadedSpendableBalanceIsDetermining() {
        assertEquals(Spendability.Determining, account(spendable = null, pending = 0).spendability)
    }

    @Test
    fun unloadedPendingBalanceIsDetermining() {
        assertEquals(Spendability.Determining, account(spendable = 10, pending = null).spendability)
    }

    @Test
    fun positiveSpendableBalanceIsSome() {
        assertEquals(Spendability.Some(Zatoshi(10)), account(spendable = 10, pending = 0).spendability)
    }

    @Test
    fun spendableFundsAreSomeEvenWhileMoreIsPending() {
        assertEquals(Spendability.Some(Zatoshi(10)), account(spendable = 10, pending = 5).spendability)
    }

    @Test
    fun shieldedSpendableOrNullIsTheAmountZeroOrUnknown() {
        assertEquals(Zatoshi(10), Spendability.Some(Zatoshi(10)).shieldedSpendableOrNull())
        assertEquals(Zatoshi(0), nothing(Empty).shieldedSpendableOrNull())
        assertEquals(Zatoshi(0), nothing(FundsConfirming).shieldedSpendableOrNull())
        assertEquals(null, Spendability.Determining.shieldedSpendableOrNull())
    }

    @Test
    fun pendingOnlyWalletIsNothing() {
        assertEquals(nothing(FundsConfirming), account(spendable = 0, pending = 5).spendability)
    }

    @Test
    fun emptyWalletIsNothing() {
        assertEquals(nothing(Empty), account(spendable = 0, pending = 0).spendability)
    }

    @Test
    fun transparentOnlyWalletIsNothingWithTheShieldingReason() {
        assertEquals(
            nothing(TransparentOnly),
            account(spendable = 0, pending = 0, isShieldingAvailable = true).spendability
        )
    }

    @Test
    fun transparentOnlyWinsOverFundsConfirming() {
        assertEquals(
            nothing(TransparentOnly),
            account(spendable = 0, pending = 5, isShieldingAvailable = true).spendability
        )
    }

    @Test
    fun transparentFundsBelowTheShieldingThresholdDoNotCount() {
        assertEquals(
            nothing(FundsConfirming),
            account(spendable = 0, pending = 5, isShieldingAvailable = false).spendability
        )
        assertEquals(nothing(Empty), account(spendable = 0, pending = 0, isShieldingAvailable = false).spendability)
    }

    @Test
    fun spendableShieldedFundsAreSomeEvenWithShieldableTransparentFunds() {
        assertEquals(
            Spendability.Some(Zatoshi(10)),
            account(spendable = 10, pending = 5, isShieldingAvailable = true).spendability
        )
    }

    @Test
    fun anUnloadedTransparentBalanceLeavesTheReasonDetermining() {
        assertEquals(
            Spendability.Determining,
            account(spendable = 0, pending = 5, isShieldingAvailable = null).spendability
        )
    }

    @Test
    fun anUnloadedTransparentBalanceDoesNotHideSpendableFunds() {
        assertEquals(
            Spendability.Some(Zatoshi(10)),
            account(spendable = 10, pending = 0, isShieldingAvailable = null).spendability
        )
    }

    /** The real derivation: [WalletAccount.isShieldingAvailable] is null while the transparent balance is. */
    @Test
    fun anEmptyShieldedWalletWithAnUnloadedTransparentBalanceIsDetermining() {
        val account =
            mockk<ZodlAccount> {
                every { spendableShieldedBalance } returns Zatoshi(0)
                every { pendingShieldedBalance } returns Zatoshi(0)
                every { totalTransparentBalance } returns null
                every { isShieldedPending } answers { callOriginal() }
                every { isShieldingAvailable } answers { callOriginal() }
            }

        assertEquals(Spendability.Determining, account.spendability)
    }

    @Test
    fun onlyKnownPositiveSpendableAllowsThePrimaryAction() {
        assertTrue(Spendability.Some(Zatoshi(1)).allowsPrimaryAction)
        assertFalse(Spendability.Determining.allowsPrimaryAction)
        Spendability.Nothing.Reason.entries.forEach {
            assertFalse(nothing(it).allowsPrimaryAction, it.name)
        }
    }

    @Test
    fun theDerivedStatesGatePrimaryActionOnTheBalanceAlone() {
        assertFalse(account(spendable = null, pending = 0).spendability.allowsPrimaryAction)
        assertFalse(account(spendable = 0, pending = 5).spendability.allowsPrimaryAction)
        assertFalse(account(spendable = 0, pending = 0).spendability.allowsPrimaryAction)
        assertFalse(account(spendable = 0, pending = 0, isShieldingAvailable = true).spendability.allowsPrimaryAction)
        assertTrue(account(spendable = 10, pending = 5).spendability.allowsPrimaryAction)
    }

    private fun nothing(reason: Spendability.Nothing.Reason) = Spendability.Nothing(reason)

    private companion object {
        val TransparentOnly = Spendability.Nothing.Reason.TransparentOnly
        val FundsConfirming = Spendability.Nothing.Reason.FundsConfirming
        val Empty = Spendability.Nothing.Reason.Empty
    }
}
