package co.electriccoin.zcash.ui.screen.reviewtransaction

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SendTransactionProposal
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.ObserveContactByAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.imageRes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The sending account's row shows the account's icon as the rest of the app does: the Zodl mark on
 * the account's colour once it has one, its own drawable before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewTransactionVMTest {
    private val zodl = EditAccountFixtures.zodl()

    private val accounts = MutableStateFlow<List<WalletAccount>?>(listOf(zodl, EditAccountFixtures.viewOnly()))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aColoredZodlAccountSendsWithItsMarkOnItsColor() =
        runTest {
            val vm = vm(metadata = mapOf(zodl.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(COLOR))))
            collect(vm)

            assertEquals(
                ImageResource.Layered(
                    background = Color(COLOR),
                    foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph)
                ),
                sender(vm).icon
            )
        }

    @Test
    fun aZodlAccountWithoutAColorSendsWithItsOwnIcon() =
        runTest {
            val vm = vm(metadata = emptyMap())
            collect(vm)

            assertEquals(imageRes(zodl.icon), sender(vm).icon)
        }

    private fun sender(vm: ReviewTransactionVM) =
        assertNotNull(assertNotNull(vm.state.value).items.filterIsInstance<SenderState>().singleOrNull())

    private suspend fun vm(metadata: Map<AccountUuid, MetadataV3>): ReviewTransactionVM {
        val proposal = proposal()
        return ReviewTransactionVM(
            getWalletAccounts = EditAccountFixtures.getWalletAccounts(accounts),
            observeContactByAddress =
                mockk<ObserveContactByAddressUseCase> { every { this@mockk(any()) } returns flowOf(null) },
            observeSelectedWalletAccount =
                mockk<ObserveSelectedWalletAccountUseCase> { every { require() } returns flowOf(zodl) },
            observeProposal =
                mockk<ObserveProposalUseCase> { every { filterSend() } returns MutableStateFlow(proposal) },
            observeAccountIcon = AccountIconFixtures.useCase(MutableStateFlow(metadata)),
            cancelProposalFlow = mockk(relaxed = true),
            getExchangeRate = mockk(relaxed = true),
            navigationRouter = mockk(relaxed = true),
            submitProposal = mockk(relaxed = true),
        )
    }

    private suspend fun proposal(): SendTransactionProposal =
        RegularTransactionProposal(
            destination = WalletAddress.Unified.new(RECIPIENT),
            amount = Zatoshi(1234L),
            memo = Memo(""),
            proposal =
                mockk<Proposal> {
                    every { totalFeeRequired() } returns Zatoshi(1000L)
                    every { usesOrchardInputs() } returns false
                },
        )

    private fun TestScope.collect(vm: ReviewTransactionVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        runCurrent()
    }

    private companion object {
        const val RECIPIENT = "recipient"
        const val COLOR = 0xFFBE38F3
    }
}
