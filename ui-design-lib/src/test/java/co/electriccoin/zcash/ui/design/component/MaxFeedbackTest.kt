package co.electriccoin.zcash.ui.design.component

import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxFeedbackTest {
    private val sendError = MaxFeedback.Error(MaxVariant.SEND)
    private val swapError = MaxFeedback.Error(MaxVariant.SWAP)

    @Test
    fun nothingIsShownWithoutHintOrError() {
        assertNull(MaxFeedback.of(hint = null, error = null))
    }

    @Test
    fun aLoneHintIsShown() {
        assertEquals(MaxFeedback.Hint, MaxFeedback.of(hint = MaxFeedback.Hint, error = null))
    }

    @Test
    fun aLoneErrorIsShown() {
        assertEquals(sendError, MaxFeedback.of(hint = null, error = sendError))
    }

    @Test
    fun theErrorWinsOverTheHint() {
        assertEquals(swapError, MaxFeedback.of(hint = MaxFeedback.Hint, error = swapError))
    }

    @Test
    fun theHintCoversFeesAndSlippage() {
        assertEquals(stringRes(R.string.general_maxFeeSlippageHint), MaxFeedback.Hint.text)
    }

    @Test
    fun theSendHintCoversTheTransactionFeeOnly() {
        assertEquals(stringRes(R.string.general_maxFeeHint), MaxFeedback.SendHint.text)
        assertEquals(sendError, MaxFeedback.of(hint = MaxFeedback.SendHint, error = sendError))
    }

    @Test
    fun theErrorCarriesTheSendOrSwapCopy() {
        assertEquals(stringRes(R.string.general_maxUnavailableSend), sendError.text)
        assertEquals(stringRes(R.string.general_maxUnavailableSwap), swapError.text)
    }

    @Test
    fun theSlotKeeps24dpToTheButton() {
        assertEquals(24f, ZashiMaxFeedbackSlotDefaults.SpacingToButton.value)
    }
}
