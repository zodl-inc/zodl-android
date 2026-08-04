package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MOB-1473: the main Address Book must keep showing every contact — including ones on chains we no
 * longer support for swaps — so its use case stays unfiltered by curation.
 */
class GetABContactsUseCaseTest {
    private val zcash = contact(name = "Zcash", chain = null)
    private val curated = contact(name = "Eth", chain = "eth")
    private val nonCurated = contact(name = "Doge", chain = "doge")

    private val addressBookRepository =
        mockk<AddressBookRepository> {
            every { contacts } returns flowOf(listOf(zcash, curated, nonCurated))
        }

    private val useCase = GetABContactsUseCase(addressBookRepository)

    @Test
    fun returnsAllContactsIncludingNonCuratedWhenNotZcashOnly() =
        runTest {
            assertEquals(listOf(zcash, curated, nonCurated), useCase.observe(zcashContactsOnly = false).first())
        }

    @Test
    fun returnsOnlyZcashContactsWhenZcashOnly() =
        runTest {
            assertEquals(listOf(zcash), useCase.observe(zcashContactsOnly = true).first())
        }

    private fun contact(name: String, chain: String?): EnhancedABContact =
        EnhancedABContact(
            contact =
                AddressBookContact(
                    name = name,
                    address = "$name-address",
                    lastUpdated = kotlin.time.Instant.fromEpochSeconds(0),
                    chain = chain,
                ),
            blockchain =
                chain?.let {
                    SwapBlockchain(chainTicker = it, chainName = stringRes(it), chainIcon = imageRes(it))
                }
        )
}
