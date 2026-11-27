package co.electriccoin.zcash.ui.screen.reviewtransaction

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewTransactionOrchardPrivacyWarningTest {
    @Test
    fun noOrchardSpendShowsNoWarning() {
        assertNull(orchardPrivacyWarningState(usesOrchardInputs = false))
    }

    @Test
    fun orchardSpendShowsWarningWithFigmaCopy() {
        val warning = orchardPrivacyWarningState(usesOrchardInputs = true)
        assertEquals("This send requires spending Orchard funds", warning?.title)
        assertEquals(
            "We recommend migrating your funds first to avoid leaking the transaction amount on-chain.",
            warning?.body,
        )
    }

    @Test
    fun noOrchardSpendKeepsDefaultButtonStyle() {
        assertNull(orchardPrivacyWarningButtonStyle(usesOrchardInputs = false))
    }

    @Test
    fun orchardSpendSwitchesToDestructiveButtonStyle() {
        assertEquals(ButtonStyle.DESTRUCTIVE1, orchardPrivacyWarningButtonStyle(usesOrchardInputs = true))
    }

    @Test
    fun noOrchardSpendBuildsNoWarningSheet() {
        assertNull(orchardWarningSheetState(usesOrchardInputs = false, onContinue = {}, onCancel = {}))
    }

    @Test
    fun orchardSpendBuildsWarningSheetWithFigmaCopyAndStyles() {
        val sheet = assertNotNull(orchardWarningSheetState(usesOrchardInputs = true, onContinue = {}, onCancel = {}))
        assertEquals(stringRes(R.string.send_orchardWarning_title), sheet.title)
        assertEquals(stringRes(R.string.send_orchardWarning_message), sheet.message)
        assertEquals(stringRes(R.string.send_orchardWarning_continue), sheet.primaryAction.text)
        assertEquals(ButtonStyle.DESTRUCTIVE1, sheet.primaryAction.style)
        assertEquals(stringRes(R.string.send_orchardWarning_cancel), sheet.secondaryAction?.text)
        assertEquals(ButtonStyle.PRIMARY, sheet.secondaryAction?.style)
    }

    @Test
    fun continueAnywayInvokesContinueCallbackOnly() {
        var continued = false
        var cancelled = false
        val sheet =
            assertNotNull(
                orchardWarningSheetState(
                    usesOrchardInputs = true,
                    onContinue = { continued = true },
                    onCancel = { cancelled = true },
                )
            )
        sheet.primaryAction.onClick()
        assertTrue(continued)
        assertTrue(!cancelled)
    }

    @Test
    fun cancelAndScrimDismissInvokeCancelCallback() {
        var cancelViaButton = false
        val viaButton =
            assertNotNull(
                orchardWarningSheetState(
                    usesOrchardInputs = true,
                    onContinue = {},
                    onCancel = { cancelViaButton = true }
                )
            )
        viaButton.secondaryAction?.onClick?.invoke()
        assertTrue(cancelViaButton)

        var cancelViaBack = false
        val viaBack =
            assertNotNull(
                orchardWarningSheetState(usesOrchardInputs = true, onContinue = {}, onCancel = { cancelViaBack = true })
            )
        viaBack.onBack()
        assertTrue(cancelViaBack)
    }
}
