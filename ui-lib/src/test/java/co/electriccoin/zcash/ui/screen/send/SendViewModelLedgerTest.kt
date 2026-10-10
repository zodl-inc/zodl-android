package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import io.mockk.coEvery
import io.mockk.mockk
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

/**
 * A Ledger account now builds its proposal like any other, so a failure to create one lands on
 * the generic send-failure stage.
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
    fun anyOtherFailureStillLandsOnTheFailureStage() =
        runTest {
            val stages = mutableListOf<SendStage>()
            val vm =
                vm(
                    createProposal =
                        mockk<CreateProposalUseCase> {
                            coEvery { this@mockk.invoke(any(), any()) } throws RuntimeException("boom")
                        },
                )

            vm.onCreateZecSendClick(mockk<ZecSend>(relaxed = true), amountState()) { stages += it }
            runCurrent()

            assertEquals(listOf<SendStage>(SendStage.SendFailure("boom")), stages)
        }

    private fun amountState() = mockk<AmountState>(relaxed = true)

    private fun vm(createProposal: CreateProposalUseCase) =
        SendViewModel(
            exchangeRateRepository = mockk(relaxed = true),
            observeContactByAddress = mockk(relaxed = true),
            observeContactPicked = mockk(relaxed = true),
            createProposal = createProposal,
            observeWalletAccounts = mockk(relaxed = true),
            navigateToSelectRecipient = mockk(relaxed = true),
            navigationRouter = mockk<NavigationRouter>(relaxed = true),
        )
}
