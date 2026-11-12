package co.electriccoin.zcash.ui.screen.send.view

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.Spendability
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review follows the balance (the same states that disable Max) AND the form rules (Figma "Send Max: Round 2"). */
class SendReviewGatingTest {
    private val balanceStatesThatBlock =
        listOf(
            Spendability.Determining,
            Spendability.Nothing(Spendability.Nothing.Reason.Empty),
            Spendability.Nothing(Spendability.Nothing.Reason.FundsConfirming),
            Spendability.Nothing(Spendability.Nothing.Reason.TransparentOnly)
        )

    @Test
    fun reviewIsDisabledWhenNothingIsSpendableEvenWithAValidForm() {
        balanceStatesThatBlock.forEach {
            assertFalse(isReviewEnabled(spendability = it, isFormValid = true), it.toString())
        }
    }

    @Test
    fun reviewNeedsTheFormRulesToo() {
        assertFalse(isReviewEnabled(Spendability.Some(Zatoshi(1)), isFormValid = false))
    }

    @Test
    fun reviewIsEnabledWhenSpendableAndTheFormIsValid() {
        assertTrue(isReviewEnabled(Spendability.Some(Zatoshi(1)), isFormValid = true))
    }
}
