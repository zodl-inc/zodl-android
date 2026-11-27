package co.electriccoin.zcash.ui.common.viewmodel

import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxVariant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MaxFeedbackControllerTest {
    private var amountClears = 0
    private val controller = MaxFeedbackController(variant = MaxVariant.SEND, onClearAmount = { amountClears++ })

    @Test
    fun nothingIsShownInitially() {
        assertNull(controller.error.value)
        assertNull(controller.sheet.value)
    }

    @Test
    fun everyFailureButTooManyNotesShowsTheErrorLine() {
        MaxSendAmountFailure.entries
            .filter { it != MaxSendAmountFailure.TOO_MANY_NOTES }
            .forEach { failure ->
                controller.clearError()

                controller.onFailed(failure)

                assertEquals(MaxFeedback.Error(MaxVariant.SEND), controller.error.value, failure.name)
                assertNull(controller.sheet.value, failure.name)
            }
    }

    @Test
    fun tooManyNotesShowsTheSheetAndClearsTheError() {
        controller.onFailed(MaxSendAmountFailure.OTHER)

        controller.onFailed(MaxSendAmountFailure.TOO_MANY_NOTES)

        assertNotNull(controller.sheet.value)
        assertNull(controller.error.value)
    }

    @Test
    fun clearErrorHidesTheError() {
        controller.onFailed(MaxSendAmountFailure.SYNC_REQUIRED)

        controller.clearError()

        assertNull(controller.error.value)
    }

    @Test
    fun showSheetReplacesTheError() {
        controller.onFailed(MaxSendAmountFailure.OTHER)

        controller.showSheet()

        assertNotNull(controller.sheet.value)
        assertNull(controller.error.value)
    }

    @Test
    fun theSheetIsAllocatedOnce() {
        controller.showSheet()
        val first = controller.sheet.value
        assertNotNull(first).ok.onClick()

        controller.showSheet()

        assertSame(first, controller.sheet.value)
    }

    @Test
    fun theErrorCarriesTheVariantOfTheController() {
        val swap = MaxFeedbackController(variant = MaxVariant.SWAP, onClearAmount = {})

        swap.onFailed(MaxSendAmountFailure.OTHER)

        assertEquals(MaxFeedback.Error(MaxVariant.SWAP), swap.error.value)
    }

    @Test
    fun backOnlyDismissesTheSheet() =
        runTest(UnconfinedTestDispatcher()) {
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch { controller.focusRequests.toList(focusRequests) }

            controller.showSheet()
            assertNotNull(controller.sheet.value).onBack()
            assertNull(controller.sheet.value)

            assertEquals(0, amountClears)
            assertTrue(focusRequests.isEmpty())
        }

    @Test
    fun okDismissesClearsAndRequestsFocus() =
        runTest(UnconfinedTestDispatcher()) {
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch { controller.focusRequests.toList(focusRequests) }
            controller.showSheet()

            assertNotNull(controller.sheet.value).ok.onClick()

            assertNull(controller.sheet.value)
            assertEquals(1, amountClears)
            assertEquals(listOf(Unit), focusRequests)
        }

    @Test
    fun requestAmountFocusEmitsWithoutTouchingTheSheetOrTheAmount() =
        runTest(UnconfinedTestDispatcher()) {
            val focusRequests = mutableListOf<Unit>()
            backgroundScope.launch { controller.focusRequests.toList(focusRequests) }

            controller.requestAmountFocus()

            assertNull(controller.sheet.value)
            assertEquals(0, amountClears)
            assertEquals(listOf(Unit), focusRequests)
        }
}
