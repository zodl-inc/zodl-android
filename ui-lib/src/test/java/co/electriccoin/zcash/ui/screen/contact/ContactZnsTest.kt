package co.electriccoin.zcash.ui.screen.contact

import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.usecase.ContactAddressValidationResult
import co.electriccoin.zcash.ui.common.usecase.SaveABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateContactNameResult
import co.electriccoin.zcash.ui.common.usecase.ValidateGenericABContactNameUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateSwapABContactAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateZashiABContactAddressUseCase
import co.electriccoin.zcash.ui.common.zns.LookUpZnsRecipientUseCase
import co.electriccoin.zcash.ui.common.zns.ZnsFieldStatus
import co.electriccoin.zcash.ui.common.zns.ZnsLookupResult
import co.electriccoin.zcash.ui.common.zns.ZnsName
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A contact saved from a ZNS name stores the address the name resolved to, labelled with the name, so a later change
 * to the name cannot move payments to that contact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactZnsTest {
    private val alice = checkNotNull(ZnsName.fromRegistryName("alice"))
    private val zcash = SwapAssetTestFixture.blockchain("zec")

    private val addressBookRepository = mockk<AddressBookRepository>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val blockchainProvider = mockk<BlockchainProvider> { every { getZcashBlockchain() } returns zcash }

    private val lookUp =
        mockk<LookUpZnsRecipientUseCase> {
            coEvery { this@mockk(any()) } returns ZnsLookupResult.NotAName
            coEvery { this@mockk("alice.zcash") } returns
                ZnsLookupResult.Resolved(alice, "u1alice", AddressType.Unified)
            coEvery { this@mockk("nobody.zcash") } returns
                ZnsLookupResult.NotRegistered(checkNotNull(ZnsName.fromRegistryName("nobody")))
        }

    private val validateZashiAddress =
        mockk<ValidateZashiABContactAddressUseCase> {
            coEvery { this@mockk(any(), any()) } returns ContactAddressValidationResult.Invalid
            coEvery { this@mockk("u1alice", any()) } returns ContactAddressValidationResult.Valid
        }

    private val validateSwapAddress =
        mockk<ValidateSwapABContactAddressUseCase> {
            coEvery { this@mockk(any(), any(), any()) } returns ContactAddressValidationResult.Invalid
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
    fun addingFromTheSendFlowSavesTheResolvedAddressUnderTheName() =
        runTest {
            val state = observe(zashiVm().state)

            state().walletAddress.onValueChange("alice.zcash")
            runCurrent()
            assertEquals(ZnsFieldStatus.LookingUp, state().walletAddressStatus)
            assertFalse(state().positiveButton.isEnabled)

            advanceTimeBy(351)
            runCurrent()
            assertEquals(ZnsFieldStatus.Resolved("u1alice"), state().walletAddressStatus)
            assertNull(state().walletAddress.error)
            assertEquals(stringRes("alice.zcash"), state().contactName.value)
            assertTrue(state().positiveButton.isEnabled)

            state().positiveButton.onClick()
            runCurrent()

            verify(exactly = 1) { addressBookRepository.saveContact("alice.zcash", "u1alice", null) }
            coVerify { validateZashiAddress("u1alice", any()) }
        }

    @Test
    fun addingFromTheAddressBookSavesTheResolvedAddressAsAZcashContact() =
        runTest {
            val state = observe(genericVm().state)

            state().walletAddress.onValueChange("alice.zcash")
            advanceTimeBy(351)
            runCurrent()
            assertEquals(ZnsFieldStatus.Resolved("u1alice"), state().walletAddressStatus)
            assertEquals(zcash.chainName, state().chain?.text)

            state().positiveButton.onClick()
            runCurrent()

            verify(exactly = 1) { addressBookRepository.saveContact("alice.zcash", "u1alice", null) }
        }

    @Test
    fun aTypedLabelIsKept() =
        runTest {
            val state = observe(zashiVm().state)

            state().contactName.onValueChange("Alice")
            state().walletAddress.onValueChange("alice.zcash")
            advanceTimeBy(351)
            runCurrent()

            assertEquals(stringRes("Alice"), state().contactName.value)
        }

    @Test
    fun anEditedNameIsNeverSavedWithTheAddressOfTheNameBeforeIt() =
        runTest {
            listOf(observe(zashiVm().state), observe(genericVm().state)).forEach { state ->
                state().walletAddress.onValueChange("alice.zcash")
                advanceTimeBy(351)
                runCurrent()
                val saveForAlice = state().positiveButton.onClick

                // The user edits the name and taps Save before the new name is looked up.
                state().walletAddress.onValueChange("nobody.zcash")
                saveForAlice()
                state().positiveButton.onClick()
                runCurrent()

                assertEquals(ZnsFieldStatus.LookingUp, state().walletAddressStatus)
                assertFalse(state().positiveButton.isEnabled)
            }
            verify(exactly = 0) { addressBookRepository.saveContact(any(), any(), any()) }
        }

    @Test
    fun anUnregisteredNameCannotBeSaved() =
        runTest {
            listOf(observe(zashiVm().state), observe(genericVm().state)).forEach { state ->
                state().contactName.onValueChange("Nobody")
                state().walletAddress.onValueChange("nobody.zcash")
                advanceTimeBy(351)
                runCurrent()

                assertEquals(stringRes(R.string.zns_error_notRegistered), state().walletAddress.error)
                assertNull(state().walletAddressStatus)
                assertFalse(state().positiveButton.isEnabled)
            }
        }

    private fun zashiVm() =
        AddZashiABContactVM(
            args = AddZashiABContactArgs(address = null),
            validateContactAddress = validateZashiAddress,
            validateContactName = validateName,
            saveContact = SaveABContactUseCase(addressBookRepository, navigationRouter),
            navigationRouter = navigationRouter,
            lookUpZnsRecipient = lookUp,
        )

    private fun genericVm() =
        AddGenericABContactVM(
            args = AddGenericABContactArgs(address = null),
            blockchainProvider = blockchainProvider,
            validateZashiABContactAddress = validateZashiAddress,
            validateSwapABContactAddress = validateSwapAddress,
            validateGenericABContactName = validateName,
            saveABContact = SaveABContactUseCase(addressBookRepository, navigationRouter),
            navigationRouter = navigationRouter,
            navigateToSelectSwapBlockchain = mockk(relaxed = true),
            lookUpZnsRecipient = lookUp,
        )

    private fun TestScope.observe(flow: StateFlow<ABContactState?>): () -> ABContactState {
        backgroundScope.launch { flow.collect {} }
        runCurrent()
        return { requireNotNull(flow.value) }
    }
}
