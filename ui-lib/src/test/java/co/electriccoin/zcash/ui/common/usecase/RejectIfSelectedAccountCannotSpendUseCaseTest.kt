package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The selected account's own type decides: a view-only account gets the unsupported error, any
 * other account the flow. A guard called before the accounts load waits for the selected account,
 * so a cold start cannot open the send form for a view-only account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RejectIfSelectedAccountCannotSpendUseCaseTest {
    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

    @Test
    fun aViewOnlyAccountShowsTheUnsupportedError() =
        runTest {
            assertTrue(guard(selected(isSpendable = false)))
            verifyViewOnlyErrorShown(times = 1)
        }

    @Test
    fun aSpendableAccountLetsTheFlowOpen() =
        runTest {
            assertFalse(guard(selected(isSpendable = true)))
            verifyViewOnlyErrorShown(times = 0)
        }

    @Test
    fun aColdStartWaitsForTheSelectedAccountBeforeDeciding() =
        runTest {
            val selectedAccount = CompletableDeferred<WalletAccount>()
            val accountDataSource =
                mockk<AccountDataSource> { coEvery { getSelectedAccount() } coAnswers { selectedAccount.await() } }

            val decision = async { RejectIfSelectedAccountCannotSpendUseCase(accountDataSource, navigateToError)() }
            runCurrent()
            assertFalse(decision.isCompleted)
            verifyViewOnlyErrorShown(times = 0)

            selectedAccount.complete(selected(isSpendable = false))
            assertTrue(decision.await())
            verifyViewOnlyErrorShown(times = 1)
        }

    private suspend fun guard(account: WalletAccount): Boolean =
        RejectIfSelectedAccountCannotSpendUseCase(
            accountDataSource = mockk<AccountDataSource> { coEvery { getSelectedAccount() } returns account },
            navigateToError = navigateToError,
        )()

    private fun selected(isSpendable: Boolean) =
        mockk<WalletAccount> {
            every { isSelected } returns true
            every { this@mockk.isSpendable } returns isSpendable
        }

    private fun verifyViewOnlyErrorShown(times: Int) {
        verify(exactly = times) { navigateToError(match { it.isViewOnlyError() }, any()) }
    }

    private fun ErrorArgs.isViewOnlyError() =
        ((this as? ErrorArgs.General)?.exception as? AccountOperationUnsupportedException)?.kind ==
            AccountOperationUnsupportedException.Kind.VIEW_ONLY
}
