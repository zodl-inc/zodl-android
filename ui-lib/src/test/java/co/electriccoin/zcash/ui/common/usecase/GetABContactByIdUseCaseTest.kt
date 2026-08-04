package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MOB-1473: editing an existing contact whose chain is no longer curated must still resolve it, so
 * the editor's lookup stays unfiltered (and case-insensitive on the chain ticker).
 */
class GetABContactByIdUseCaseTest {
    private val doge = contact(name = "Doge", address = "doge-address", chain = "doge")

    private val addressBookRepository =
        mockk<AddressBookRepository> {
            every { contacts } returns flowOf(listOf(doge))
        }

    private val useCase = GetABContactByIdUseCase(addressBookRepository)

    @Test
    fun resolvesNonCuratedContactCaseInsensitively() =
        runTest {
            assertEquals(doge, useCase(address = "doge-address", chain = "DOGE"))
        }

    @Test
    fun returnsNullWhenAddressDoesNotMatch() =
        runTest {
            assertEquals(null, useCase(address = "other-address", chain = "doge"))
        }

    private fun contact(name: String, address: String, chain: String?): EnhancedABContact =
        EnhancedABContact(
            contact =
                AddressBookContact(
                    name = name,
                    address = address,
                    lastUpdated = kotlin.time.Instant.fromEpochSeconds(0),
                    chain = chain,
                ),
            blockchain =
                chain?.let {
                    SwapBlockchain(chainTicker = it, chainName = stringRes(it), chainIcon = imageRes(it))
                }
        )
}
