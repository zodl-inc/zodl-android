package co.electriccoin.zcash.ui.screen.contact

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretFixture
import co.electriccoin.zcash.ui.common.usecase.ContactAddressValidationResult
import co.electriccoin.zcash.ui.common.usecase.DeleteABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.GetABContactByIdUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectSwapBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateContactNameResult
import co.electriccoin.zcash.ui.common.usecase.ValidateGenericABContactNameUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateSwapABContactAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateZashiABContactAddressUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import kotlin.test.assertFalse
import kotlin.time.Instant

/**
 * A gift card link pasted into a contact's address and saved before the screen's own validation has caught up with
 * it: the save and update use cases refuse it, and the screen shows why, stops loading and stays, instead of crashing
 * with the refusal. The validators here answer as they would before catching up, so only the use cases' own guard
 * stands between the link and the address book.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactGiftCardLinkRefusalTest {
    private val zcash = SwapAssetTestFixture.blockchain("zec")

    private val existing =
        EnhancedABContact(
            contact =
                AddressBookContact(
                    name = "Existing",
                    address = "u1existing",
                    lastUpdated = Instant.fromEpochMilliseconds(0),
                    chain = null
                ),
            blockchain = null
        )

    private val addressBookRepository = mockk<AddressBookRepository>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val blockchainProvider = mockk<BlockchainProvider> { every { getZcashBlockchain() } returns zcash }

    private val validateZashiAddress =
        mockk<ValidateZashiABContactAddressUseCase> {
            coEvery { this@mockk(any(), any()) } returns ContactAddressValidationResult.Valid
        }

    private val validateSwapAddress =
        mockk<ValidateSwapABContactAddressUseCase> {
            coEvery { this@mockk(any(), any(), any()) } returns ContactAddressValidationResult.Valid
        }

    private val validateName =
        mockk<ValidateGenericABContactNameUseCase> {
            coEvery { this@mockk(any(), any()) } returns ValidateContactNameResult.Valid
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addingAContactWithAGiftCardLinkShowsTheRefusal() =
        runTest {
            val vm =
                AddGenericABContactVM(
                    args = AddGenericABContactArgs(address = null),
                    blockchainProvider = blockchainProvider,
                    validateZashiABContactAddress = validateZashiAddress,
                    validateSwapABContactAddress = validateSwapAddress,
                    validateGenericABContactName = validateName,
                    saveABContact = SaveABContactUseCase(addressBookRepository, navigationRouter),
                    navigationRouter = navigationRouter,
                    navigateToSelectSwapBlockchain = mockk(relaxed = true)
                )
            val state = observe(vm.state)
            state().walletAddress.onValueChange(GiftCardSecretFixture.LINK)
            state().contactName.onValueChange("Card")
            runCurrent()

            state().positiveButton.onClick()
            runCurrent()

            assertRefused(state())
            verify(exactly = 0) { addressBookRepository.saveContact(any(), any(), any()) }
        }

    @Test
    fun updatingAContactWithAGiftCardLinkShowsTheRefusal() =
        runTest {
            val getContact =
                mockk<GetABContactByIdUseCase> { coEvery { this@mockk(any(), any()) } returns existing }
            val vm =
                UpdateGenericABContactVM(
                    blockchainProvider = blockchainProvider,
                    args = UpdateGenericABContactArgs(address = existing.address, chain = null),
                    validateGenericABContactName = validateName,
                    updateContact = UpdateABContactUseCase(addressBookRepository, navigationRouter),
                    deleteContact = mockk<DeleteABContactUseCase>(relaxed = true),
                    getContactByAddress = getContact,
                    navigationRouter = navigationRouter,
                    navigateToSelectSwapBlockchain = mockk<NavigateToSelectSwapBlockchainUseCase>(relaxed = true),
                    validateZashiABContactAddress = validateZashiAddress,
                    validateSwapABContactAddress = validateSwapAddress
                )
            val state = observe(vm.state)
            state().walletAddress.onValueChange(GiftCardSecretFixture.LINK)
            runCurrent()

            state().positiveButton.onClick()
            runCurrent()

            assertRefused(state())
            verify(exactly = 0) { addressBookRepository.updateContact(any(), any(), any(), any()) }
        }

    /** Keeps [flow] collected for the rest of the test, and returns a reader of its current, non-null state. */
    private fun TestScope.observe(flow: StateFlow<ABContactState?>): () -> ABContactState {
        backgroundScope.launch { flow.collect {} }
        runCurrent()
        return { requireNotNull(flow.value) }
    }

    private fun assertRefused(state: ABContactState) {
        assertEquals(stringRes(R.string.contact_error_giftCardLink), state.walletAddress.error)
        assertFalse(state.positiveButton.isLoading, "the save button stops loading")
        assertFalse(state.positiveButton.isEnabled, "the refused link cannot be saved again")
        verify(exactly = 0) { navigationRouter.back() }
    }
}
