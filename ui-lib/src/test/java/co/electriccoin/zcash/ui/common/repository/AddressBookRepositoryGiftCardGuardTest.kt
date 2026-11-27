package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.AddressBookDataSource
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.provider.AddressBookKeyStorageProvider
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

/**
 * [AddressBookRepositoryImpl] is the one way into the stored address book, so it refuses a gift card link as a
 * contact's address on its own, independently of any screen or use case: nothing is written and no key is touched.
 */
class AddressBookRepositoryGiftCardGuardTest {
    private val addressBookDataSource = mockk<AddressBookDataSource>(relaxed = true)
    private val addressBookKeyStorageProvider = mockk<AddressBookKeyStorageProvider>(relaxed = true)
    private val accountDataSource =
        mockk<AccountDataSource>(relaxed = true) {
            every { zashiAccount } returns MutableStateFlow(null)
        }

    private val repository =
        AddressBookRepositoryImpl(
            addressBookDataSource = addressBookDataSource,
            addressBookKeyStorageProvider = addressBookKeyStorageProvider,
            accountDataSource = accountDataSource,
            persistableWalletProvider = mockk<PersistableWalletProvider>(relaxed = true),
            blockchainProvider = mockk<BlockchainProvider>(relaxed = true)
        )

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

    @Test
    fun saveContactRefusesAGiftCardLink() {
        GiftCardSecretFixture.all.forEach { (name, link) ->
            val exception =
                assertFailsWith<GiftCardContactNotAllowedException>(name) {
                    repository.saveContact(name = "Card", address = link, chain = null)
                }
            assertFalse(exception.toString().contains(link), name)
        }

        verify { addressBookDataSource wasNot Called }
        verify { addressBookKeyStorageProvider wasNot Called }
    }

    @Test
    fun updateContactRefusesAGiftCardLink() {
        GiftCardSecretFixture.all.forEach { (name, link) ->
            val exception =
                assertFailsWith<GiftCardContactNotAllowedException>(name) {
                    repository.updateContact(contact = existing, name = "Card", address = link, chain = "btc")
                }
            assertFalse(exception.toString().contains(link), name)
        }

        verify { addressBookDataSource wasNot Called }
        verify { addressBookKeyStorageProvider wasNot Called }
    }
}
