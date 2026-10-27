package co.electriccoin.zcash.ui.screen.error

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A view-only refusal renders the copy the view-only module supplies, on both error surfaces, and
 * the Lce sheet does so even when the call site passed a title of its own, exactly like the Ledger
 * refusal in LedgerErrorCopyTest.
 */
class ViewOnlyErrorCopyTest {
    private val title = stringRes(R.string.accounts_keystone)

    private val message = stringRes(R.string.accounts_zashi)

    private val viewOnlyContent =
        mockk<ViewOnlyContent> {
            every { unsupportedOperationTitle } returns title
            every { unsupportedOperationMessage } returns message
        }

    private val exception = AccountOperationUnsupportedException.viewOnly()

    @Test
    fun theLceSheetShowsTheViewOnlyCopyWhateverTheCallSitePassed() {
        val mapper = ErrorMapperUseCase(sendEmail = mockk(relaxed = true), viewOnlyContent = viewOnlyContent)

        val state =
            mapper.mapToState(
                error = LceContent.Error(cause = exception, restart = {}, dismiss = {}),
                title = stringRes(R.string.error_general_title),
                message = stringRes(R.string.error_general_title),
            )

        assertEquals(title, state.state.title)
        assertEquals(message, state.state.message)
    }

    @Test
    fun theErrorDialogShowsTheViewOnlyCopyInsteadOfAStackTrace() {
        val vm =
            ErrorVM(
                args = ErrorArgs.General(exception),
                navigateToErrorBottom = mockk(relaxed = true),
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
                sendEmailUseCase = mockk(relaxed = true),
                viewOnlyContent = viewOnlyContent,
            )

        assertEquals(title, vm.state.value.title)
        assertEquals(message, vm.state.value.message)
    }
}
