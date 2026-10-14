package co.electriccoin.zcash.ui.design.component

import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxFeedbackTest {
    private val hint = maxFundsConfirmingHintState()
    private val banner = maxFailedBannerState()

    @Test
    fun nothingIsShownWithoutHintOrBanner() {
        assertNull(MaxFeedback.of(hint = null, banner = null))
    }

    @Test
    fun aLoneHintIsShown() {
        assertEquals(MaxFeedback.Hint(hint), MaxFeedback.of(hint = hint, banner = null))
    }

    @Test
    fun aLoneBannerIsShown() {
        assertEquals(MaxFeedback.Banner(banner), MaxFeedback.of(hint = null, banner = banner))
    }

    @Test
    fun theBannerWinsOverTheHint() {
        assertEquals(MaxFeedback.Banner(banner), MaxFeedback.of(hint = hint, banner = banner))
    }

    @Test
    fun theFailedBannerUsesTheGenericCopy() {
        assertEquals(stringRes(R.string.general_maxFailedTitle), banner.title)
        assertEquals(stringRes(R.string.general_maxFailedSubtitle), banner.subtitle)
    }

    @Test
    fun theFundsConfirmingHintHasNoAction() {
        assertEquals(stringRes(R.string.general_maxHintConfirming), hint.text)
        assertNull(hint.button)
    }

    @Test
    fun theShieldedOnlyHintOffersShield() {
        var shieldClicks = 0
        val state = maxShieldedOnlyHintState(onShield = { shieldClicks++ })

        assertEquals(stringRes(R.string.general_maxHintShieldedOnly), state.text)
        assertEquals(stringRes(R.string.general_maxHintShield), state.button?.text)
        state.button?.onClick?.invoke()
        assertEquals(1, shieldClicks)
    }
}
