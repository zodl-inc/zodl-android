package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.TransactionProposalNotCreatedException
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A manual amount that needs more notes than the device can spend in one transaction ends on the too-many-notes stage,
 * not on the generic failure. Runs under Robolectric because a failure is logged via `Twig`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendViewModelTooManyNotesTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aProposalOverTheBoundLandsOnTheTooManyNotesStage() =
        runTest {
            val tooLarge = TransactionEncoderException.ProposalTooLargeException(RuntimeException("raw"))

            assertEquals(
                listOf<SendStage>(SendStage.TooManyNotes),
                stagesOf(TransactionProposalNotCreatedException(tooLarge))
            )
        }

    @Test
    fun anotherProposalFailureStillLandsOnTheFailureStage() =
        runTest {
            val other = TransactionEncoderException.ProposalFromParametersException(RuntimeException("raw"))

            assertIs<SendStage.SendFailure>(stagesOf(TransactionProposalNotCreatedException(other)).single())
        }

    private fun TestScope.stagesOf(error: Exception): List<SendStage> {
        val stages = mutableListOf<SendStage>()
        val vm =
            SendViewModel(
                exchangeRateRepository = mockk(relaxed = true),
                observeContactByAddress = mockk(relaxed = true),
                observeContactPicked = mockk(relaxed = true),
                createProposal =
                    mockk<CreateProposalUseCase> {
                        coEvery { this@mockk.invoke(any(), any()) } throws error
                    },
                observeWalletAccounts = mockk(relaxed = true),
                getSelectedWalletAccount = mockk(relaxed = true),
                getMaxSendAmount = mockk(relaxed = true),
                navigateToSelectRecipient = mockk(relaxed = true),
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
                signerCapabilitiesProvider = mockk(relaxed = true),
            )

        vm.onCreateZecSendClick(mockk<ZecSend>(relaxed = true), mockk<AmountState>(relaxed = true)) { stages += it }
        runCurrent()
        return stages
    }
}
