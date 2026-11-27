package co.electriccoin.zcash.ui.screen.redeemgift

import cash.z.ecc.android.sdk.model.Zatoshi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The statuses that wait for the card or for the redemption keep the screen awake; the others let it time out.
 */
class RedeemGiftStateWaitingTest {
    private fun isWaiting(state: RedeemGiftState): Boolean =
        assertIs<RedeemGiftState.CardStatus>(state).status.isWaiting

    @Test
    fun checkingPendingAndRedeemingKeepTheScreenAwake() {
        assertEquals(true, isWaiting(RedeemGiftState.checking(onBack = {})))
        assertEquals(true, isWaiting(RedeemGiftState.redeeming()))
        assertEquals(
            true,
            isWaiting(
                RedeemGiftState.pending(
                    amount = Zatoshi(AMOUNT),
                    isRechecking = false,
                    onCheckAgain = {},
                    onClose = {}
                )
            )
        )
    }

    @Test
    fun theOtherStatusesDoNot() {
        assertEquals(
            false,
            isWaiting(RedeemGiftState.empty(isDust = false, isRechecking = false, onCheckAgain = {}, onClose = {}))
        )
    }

    private companion object {
        const val AMOUNT = 10_000L
    }
}
