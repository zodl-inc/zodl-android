package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretFixture
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A gift card link carries the card's spending key, so it is never saved as a contact: the Zashi contact validation
 * reports it, and the save and update use cases refuse it on their own, whatever the screen validated, before anything
 * is saved or any navigation happens. Ordinary addresses are saved as before.
 */
class GiftCardContactGuardTest {
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

    private val addressBookRepository =
        mockk<AddressBookRepository>(relaxed = true) {
            every { contacts } returns MutableStateFlow(listOf(existing))
        }

    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val sdkSynchronizer = mockk<Synchronizer>()

    private val synchronizerProvider =
        mockk<SynchronizerProvider> {
            coEvery { getSynchronizer() } returns sdkSynchronizer
        }

    @Test
    fun zashiContactValidationReportsAGiftCardLinkWithoutAskingTheSdk() =
        runTest {
            val validate = ValidateZashiABContactAddressUseCase(addressBookRepository, synchronizerProvider)

            GiftCardSecretFixture.all.forEach { (name, link) ->
                assertEquals(ContactAddressValidationResult.GiftCardLink, validate(link), name)
            }
            coVerify { synchronizerProvider wasNot Called }
        }

    @Test
    fun zashiContactValidationStillValidatesOrdinaryAddresses() =
        runTest {
            coEvery { sdkSynchronizer.validateAddress(any()) } returns AddressType.Unified
            val validate = ValidateZashiABContactAddressUseCase(addressBookRepository, synchronizerProvider)

            assertEquals(ContactAddressValidationResult.Valid, validate("u1new"))
            assertEquals(ContactAddressValidationResult.NotUnique, validate("u1existing"))
        }

    @Test
    fun saveRefusesAGiftCardLinkWithoutSavingOrNavigating() {
        val save = SaveABContactUseCase(addressBookRepository, navigationRouter)

        GiftCardSecretFixture.all.forEach { (name, link) ->
            assertEquals(
                ABContactSaveResult.GiftCardLinkRefused,
                save(name = "Card", address = link, chain = null),
                name
            )
        }
        verify(exactly = 0) { addressBookRepository.saveContact(any(), any(), any()) }
        verify { navigationRouter wasNot Called }
    }

    @Test
    fun updateRefusesAGiftCardLinkWithoutSavingOrNavigating() {
        val update = UpdateABContactUseCase(addressBookRepository, navigationRouter)

        GiftCardSecretFixture.all.forEach { (name, link) ->
            assertEquals(
                ABContactSaveResult.GiftCardLinkRefused,
                update(contact = existing, name = "Card", address = link, chain = "btc"),
                name
            )
        }
        verify(exactly = 0) { addressBookRepository.updateContact(any(), any(), any(), any()) }
        verify { navigationRouter wasNot Called }
    }

    @Test
    fun ordinaryAddressesAreSavedAndUpdatedAsBefore() {
        assertEquals(
            ABContactSaveResult.Saved,
            SaveABContactUseCase(addressBookRepository, navigationRouter)(name = "A", address = "u1new", chain = null)
        )
        assertEquals(
            ABContactSaveResult.Saved,
            UpdateABContactUseCase(addressBookRepository, navigationRouter)(
                contact = existing,
                name = "B",
                address = "bc1qordinary",
                chain = "btc"
            )
        )

        verify(exactly = 1) { addressBookRepository.saveContact("A", "u1new", null) }
        verify(exactly = 1) { addressBookRepository.updateContact(existing, "B", "bc1qordinary", "btc") }
        verify(exactly = 2) { navigationRouter.back() }
    }
}
