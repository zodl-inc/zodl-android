package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.pluralStringRes
import co.electriccoin.zcash.ui.design.util.stringRes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The pending gift card's "needs N more confirmations" subtitle, rendered from the real resources: one confirmation
 * reads in the singular, two in the plural, in English and in Spanish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedeemGiftPendingSubtitleTest {
    private fun subtitle(confirmations: Int): String =
        pluralStringRes(
            R.plurals.redeemGift_pending_subtitle_confirmations,
            confirmations,
            stringRes(AMOUNT),
            confirmations,
            MINUTES
        ).getString(RuntimeEnvironment.getApplication())

    @Test
    fun oneConfirmationIsSingularInEnglish() {
        assertEquals(
            "$AMOUNT needs 1 more confirmation (about $MINUTES min) before it can be redeemed. " +
                "We'll keep checking while this screen is open.",
            subtitle(1)
        )
    }

    @Test
    fun twoConfirmationsArePluralInEnglish() {
        assertEquals(
            "$AMOUNT needs 2 more confirmations (about $MINUTES min) before it can be redeemed. " +
                "We'll keep checking while this screen is open.",
            subtitle(2)
        )
    }

    @Test
    @Config(qualifiers = "es")
    fun oneConfirmationIsSingularInSpanish() {
        assertEquals(
            "$AMOUNT necesita 1 confirmación más (unos $MINUTES min) antes de poder canjearse. " +
                "Seguiremos verificando mientras esta pantalla esté abierta.",
            subtitle(1)
        )
    }

    @Test
    @Config(qualifiers = "es")
    fun twoConfirmationsArePluralInSpanish() {
        assertEquals(
            "$AMOUNT necesita 2 confirmaciones más (unos $MINUTES min) antes de poder canjearse. " +
                "Seguiremos verificando mientras esta pantalla esté abierta.",
            subtitle(2)
        )
    }

    private companion object {
        const val AMOUNT = "0.1 ZEC"
        const val MINUTES = 3
    }
}
