package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.ValidateAddressUseCase
import co.electriccoin.zcash.ui.common.zns.LookUpZnsRecipientUseCase
import co.electriccoin.zcash.ui.common.zns.ZnsConfirmation
import co.electriccoin.zcash.ui.common.zns.ZnsLookupResult
import co.electriccoin.zcash.ui.common.zns.ZnsName
import co.electriccoin.zcash.ui.common.zns.ZnsSendRecipient
import co.electriccoin.zcash.ui.common.zns.ZnsSendRecipientRepository
import co.electriccoin.zcash.ui.common.zns.ZnsSendStopped
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.ZnsRecipientStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelZnsTest {
    private val alice = checkNotNull(ZnsName.fromRegistryName("alice"))
    private val resolved = ZnsLookupResult.Resolved(alice, "u1alice", AddressType.Unified)

    private val repository = ZnsSendRecipientRepository()
    private val lookUp = mockk<LookUpZnsRecipientUseCase> { coEvery { this@mockk(any()) } returns resolved }
    private val validateAddress =
        mockk<ValidateAddressUseCase> { coEvery { this@mockk(any()) } returns AddressType.Unified }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aTypedNameIsLookedUpOnceTheUserStopsTyping() =
        runTest {
            val vm = vm()

            vm.onRecipientInput("alice.zec")
            val lookingUp = vm.recipientAddressState.value
            assertIs<ZnsRecipientStatus.LookingUp>(lookingUp.zns)
            assertIs<AddressType.Invalid>(lookingUp.type)
            assertEquals("alice.zec", lookingUp.fieldText)
            coVerify(exactly = 0) { lookUp(any()) }

            advanceTimeBy(351)
            runCurrent()

            val state = vm.recipientAddressState.value
            assertEquals("u1alice", state.address)
            assertEquals(AddressType.Unified, state.type)
            assertEquals("alice.zec", state.fieldText)
            assertEquals(ZnsRecipientStatus.Resolved(alice, "alice.zec"), state.zns)
            coVerify(exactly = 1) { lookUp("alice.zec") }
        }

    @Test
    fun aLateAnswerForEarlierInputNeverChangesTheRecipient() =
        runTest {
            val gate = CompletableDeferred<ZnsLookupResult>()
            coEvery { lookUp("alice.zcash") } coAnswers { gate.await() }
            val vm = vm()

            vm.onRecipientInput("alice.zcash")
            advanceTimeBy(351)
            runCurrent()
            vm.onRecipientInput("u1typed")
            runCurrent()
            gate.complete(resolved)
            runCurrent()

            val state = vm.recipientAddressState.value
            assertEquals("u1typed", state.address)
            assertNull(state.zns)
        }

    @Test
    fun aNameThatDoesNotResolveBlocksReviewWithItsMessage() =
        runTest {
            val cases =
                mapOf(
                    ZnsLookupResult.NamesOff(alice) to R.string.zns_error_off,
                    ZnsLookupResult.NotRegistered(alice) to R.string.zns_error_notRegistered,
                    ZnsLookupResult.InvalidAddress(alice) to R.string.zns_error_invalidAddress,
                    ZnsLookupResult.Unreachable(alice) to R.string.zns_error_unreachable,
                )
            cases.forEach { (result, message) ->
                coEvery { lookUp(any()) } returns result
                val vm = vm()

                vm.onRecipientInput("alice.zcash")
                advanceTimeBy(351)
                runCurrent()

                val state = vm.recipientAddressState.value
                assertIs<AddressType.Invalid>(state.type)
                assertEquals(
                    ZnsRecipientStatus.Failed(alice, "alice.zcash", stringRes(message)),
                    state.zns
                )
            }
        }

    @Test
    fun aSendToANameRecordsTheNameForReview() =
        runTest {
            val vm = vm()
            vm.onRecipientInput("alice.zcash")
            advanceTimeBy(351)
            runCurrent()

            vm.onCreateZecSendClick(zecSendTo("u1alice"), mockk<AmountState>(relaxed = true)) {}
            assertEquals(ZnsSendRecipient(alice, "u1alice"), repository.recipient.value)

            vm.onRecipientInput("u1other")
            runCurrent()
            assertNull(repository.recipient.value)
        }

    @Test
    fun aSendToATypedAddressRecordsNoName() =
        runTest {
            val vm = vm()
            vm.onRecipientInput("u1alice")
            runCurrent()

            vm.onCreateZecSendClick(zecSendTo("u1alice"), mockk<AmountState>(relaxed = true)) {}

            assertNull(repository.recipient.value)
        }

    @Test
    fun aStoppedSendSaysWhyUnderTheField() =
        runTest {
            val vm = vm()

            repository.reportStopped(ZnsSendStopped(ZnsSendRecipient(alice, "u1alice"), ZnsConfirmation.CHANGED))
            runCurrent()

            val state = vm.recipientAddressState.value
            assertIs<AddressType.Invalid>(state.type)
            assertEquals(
                ZnsRecipientStatus.Failed(
                    alice,
                    "alice.zcash",
                    stringRes(R.string.zns_error_changed, "alice.zcash")
                ),
                state.zns
            )
            assertNull(repository.stopped.value)
        }

    @Test
    fun aSendThatCouldNotBeConfirmedSaysSoUnderTheField() =
        runTest {
            val vm = vm()

            repository.reportStopped(ZnsSendStopped(ZnsSendRecipient(alice, "u1alice"), ZnsConfirmation.UNCONFIRMED))
            runCurrent()

            assertEquals(
                ZnsRecipientStatus.Failed(
                    alice,
                    "alice.zcash",
                    stringRes(R.string.zns_error_unconfirmed, "alice.zcash")
                ),
                vm.recipientAddressState.value.zns
            )
        }

    private fun zecSendTo(address: String) =
        mockk<ZecSend>(relaxed = true) {
            val walletAddress = mockk<WalletAddress>()
            every { walletAddress.address } returns address
            every { destination } returns walletAddress
        }

    private fun vm() =
        SendViewModel(
            exchangeRateRepository = mockk(relaxed = true),
            observeContactByAddress = mockk(relaxed = true),
            observeContactPicked = mockk(relaxed = true),
            createProposal = mockk(relaxed = true),
            observeWalletAccounts = mockk(relaxed = true),
            navigateToSelectRecipient = mockk(relaxed = true),
            navigationRouter = mockk(relaxed = true),
            validateAddress = validateAddress,
            lookUpZnsRecipient = lookUp,
            znsSendRecipientRepository = repository,
        )
}
