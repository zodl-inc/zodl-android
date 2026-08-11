package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.time.Instant

/**
 * [IsABContactHintVisibleUseCase] decides whether to show the "add this address to your contacts"
 * hint: never for blank/short text or when a contact is already chosen; and when the typed address
 * is not yet a known contact it flashes the hint (true) then auto-hides it (false) after a delay.
 */
class IsABContactHintVisibleUseCaseTest {
    private val addressBookRepository = mockk<AddressBookRepository>(relaxed = true)
    private val useCase = IsABContactHintVisibleUseCase(addressBookRepository)

    @Test
    fun hiddenWhenTextIsNull() =
        runTest {
            assertContentEquals(listOf(false), useCase.observe(selectedContact = null, text = null).toList())
            verify(exactly = 0) { addressBookRepository.observeContactByAddress(any()) }
        }

    @Test
    fun hiddenWhenTextIsBlank() =
        runTest {
            assertContentEquals(listOf(false), useCase.observe(selectedContact = null, text = "   ").toList())
        }

    @Test
    fun hiddenWhenTextIsShorterThanThreeChars() =
        runTest {
            assertContentEquals(listOf(false), useCase.observe(selectedContact = null, text = "ab").toList())
            verify(exactly = 0) { addressBookRepository.observeContactByAddress(any()) }
        }

    @Test
    fun hiddenWhenAContactIsAlreadySelected() =
        runTest {
            val result = useCase.observe(selectedContact = contact("Alice"), text = "long-enough-address").toList()

            assertContentEquals(listOf(false), result)
            verify(exactly = 0) { addressBookRepository.observeContactByAddress(any()) }
        }

    @Test
    fun flashesHintThenAutoHidesWhenAddressIsNotYetAContact() =
        runTest {
            every { addressBookRepository.observeContactByAddress("abc") } returns flowOf(null)

            val result = useCase.observe(selectedContact = null, text = "abc").toList()

            // Visible, then hidden again after the 3s auto-dismiss delay (virtual time).
            assertContentEquals(listOf(true, false), result)
        }

    @Test
    fun hiddenWhenAddressAlreadyMatchesAContact() =
        runTest {
            every { addressBookRepository.observeContactByAddress("abc") } returns flowOf(contact("Existing"))

            val result = useCase.observe(selectedContact = null, text = "abc").toList()

            assertContentEquals(listOf(false), result)
        }

    private fun contact(name: String) =
        EnhancedABContact(
            contact =
                AddressBookContact(
                    name = name,
                    address = "address-$name",
                    lastUpdated = Instant.fromEpochMilliseconds(0),
                    chain = null
                ),
            blockchain = null
        )
}
