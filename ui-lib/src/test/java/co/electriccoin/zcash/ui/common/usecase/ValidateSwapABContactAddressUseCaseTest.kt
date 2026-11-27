package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretFixture
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A gift card link, in any disguise of [GiftCardSecretFixture], must never be saved as a swap contact, from where it
 * would reach the swap provider. Ordinary addresses keep the uniqueness check.
 */
class ValidateSwapABContactAddressUseCaseTest {
    private val btc = SwapAssetTestFixture.blockchain("btc")

    private val existing =
        EnhancedABContact(
            contact =
                AddressBookContact(
                    name = "Existing",
                    address = "bc1qexisting",
                    lastUpdated = Instant.fromEpochMilliseconds(0),
                    chain = "btc"
                ),
            blockchain = btc
        )

    private val addressBookRepository =
        mockk<AddressBookRepository> {
            every { contacts } returns MutableStateFlow(listOf(existing))
        }

    private val validate = ValidateSwapABContactAddressUseCase(addressBookRepository = addressBookRepository)

    @Test
    fun giftCardLinkIsRejected() =
        runTest {
            GiftCardSecretFixture.all.forEach { (name, link) ->
                assertEquals(ContactAddressValidationResult.GiftCardLink, validate(link, btc), name)
                assertEquals(ContactAddressValidationResult.GiftCardLink, validate(link, null), name)
            }
        }

    @Test
    fun ordinaryAddressesKeepTheUniquenessCheck() =
        runTest {
            assertEquals(ContactAddressValidationResult.Valid, validate("bc1qnew", btc))
            assertEquals(ContactAddressValidationResult.NotUnique, validate("bc1qexisting", btc))
        }
}
