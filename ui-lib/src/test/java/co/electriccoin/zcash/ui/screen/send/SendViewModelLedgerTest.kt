package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerOperationUnsupportedException
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Ledger account cannot build a proposal yet. That refusal has its own sheet with translated
 * copy, so it must not fall through to the generic send-failure stage, which renders the
 * exception's own English text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelLedgerTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aLedgerRefusalOpensTheSharedSheetInsteadOfTheFailureStage() =
        runTest {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val stages = mutableListOf<SendStage>()
            val vm =
                vm(
                    createProposal =
                        mockk<CreateProposalUseCase> {
                            coEvery { this@mockk.invoke(any(), any()) } throws LedgerOperationUnsupportedException()
                        },
                    navigateToError = navigateToError,
                )

            vm.onCreateZecSendClick(mockk<ZecSend>(relaxed = true), amountState()) { stages += it }
            runCurrent()

            val args = slot<ErrorArgs>()
            verify(exactly = 1) { navigateToError.invoke(capture(args), any()) }
            assertTrue((args.captured as ErrorArgs.General).exception is LedgerOperationUnsupportedException)
            assertEquals(emptyList<SendStage>(), stages)
        }

    @Test
    fun anyOtherFailureStillLandsOnTheFailureStage() =
        runTest {
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val stages = mutableListOf<SendStage>()
            val vm =
                vm(
                    createProposal =
                        mockk<CreateProposalUseCase> {
                            coEvery { this@mockk.invoke(any(), any()) } throws RuntimeException("boom")
                        },
                    navigateToError = navigateToError,
                )

            vm.onCreateZecSendClick(mockk<ZecSend>(relaxed = true), amountState()) { stages += it }
            runCurrent()

            assertEquals(listOf<SendStage>(SendStage.SendFailure("boom")), stages)
            verify(exactly = 0) { navigateToError.invoke(any(), any()) }
        }

    private fun amountState() = mockk<AmountState>(relaxed = true)

    private fun vm(
        createProposal: CreateProposalUseCase,
        navigateToError: NavigateToErrorUseCase,
    ) = SendViewModel(
        exchangeRateRepository = mockk(relaxed = true),
        observeContactByAddress = mockk(relaxed = true),
        observeContactPicked = mockk(relaxed = true),
        createProposal = createProposal,
        observeWalletAccounts = mockk(relaxed = true),
        navigateToSelectRecipient = mockk(relaxed = true),
        navigateToError = navigateToError,
        navigationRouter = mockk<NavigationRouter>(relaxed = true),
    )
}
