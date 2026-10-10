package co.electriccoin.zcash.ui.screen.error

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.LedgerOperationUnsupportedException
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A Ledger refusal is a product limitation, not a crash, so both error surfaces render its own
 * copy rather than the generic title plus a stack trace — and the Lce sheet does so even when the
 * call site passed a title of its own, because no call site can know a Ledger was selected.
 */
class LedgerErrorCopyTest {
    @Test
    fun theLceSheetOverridesWhateverTheCallSitePassed() {
        val mapper = ErrorMapperUseCase(sendEmail = mockk(relaxed = true))

        val state =
            mapper.mapToState(
                error = error(LedgerOperationUnsupportedException()),
                title = stringRes(R.string.error_general_title),
                message = stringRes(R.string.error_general_title),
            )

        assertEquals(R.string.ledger_unsupported_title, state.state.title.resourceId())
        assertEquals(R.string.ledger_unsupported_message, state.state.message.resourceId())
    }

    @Test
    fun anOrdinaryFailureKeepsTheCallSiteCopy() {
        val mapper = ErrorMapperUseCase(sendEmail = mockk(relaxed = true))

        val state =
            mapper.mapToState(
                error = error(RuntimeException("boom")),
                title = stringRes(R.string.error_general_title),
            )

        assertEquals(R.string.error_general_title, state.state.title.resourceId())
        assertNotEquals(R.string.ledger_unsupported_message, state.state.message.resourceId())
    }

    @Test
    fun theErrorDialogShowsTheLedgerCopyInsteadOfAStackTrace() {
        val vm =
            ErrorVM(
                args = ErrorArgs.General(LedgerOperationUnsupportedException()),
                navigateToErrorBottom = mockk(relaxed = true),
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
                sendEmailUseCase = mockk(relaxed = true),
            )

        assertEquals(
            R.string.ledger_unsupported_title,
            vm.state.value.title
                .resourceId()
        )
        assertEquals(
            R.string.ledger_unsupported_message,
            vm.state.value.message
                .resourceId()
        )
    }

    @Test
    fun theErrorDialogStillShowsTheStackTraceForAnythingElse() {
        val vm =
            ErrorVM(
                args = ErrorArgs.General(RuntimeException("boom")),
                navigateToErrorBottom = mockk(relaxed = true),
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
                sendEmailUseCase = mockk(relaxed = true),
            )

        assertEquals(
            R.string.error_general_title,
            vm.state.value.title
                .resourceId()
        )
        assertEquals(
            R.string.error_general_message,
            vm.state.value.message
                .resourceId()
        )
    }

    private fun error(cause: Throwable) =
        LceContent.Error(cause = cause, restart = {}, dismiss = {})

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}
