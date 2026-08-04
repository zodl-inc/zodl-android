package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.DynamicSimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
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
 * MOB-1473: the swap/pay recipient contact picker must offer only contacts on curated (supported)
 * blockchains, while the main Address Book keeps showing all contacts. This use case is the picker's
 * sole source, so it filters to the curated chain set.
 */
class GetABSwapContactsUseCaseTest {
    private val addressBookRepository = mockk<AddressBookRepository>()
    private val simpleSwapAssetProvider =
        mockk<SimpleSwapAssetProvider> {
            every { getCuratedSwapAssets() } returns listOf(curatedAsset("eth"), curatedAsset("btc"))
        }

    private val useCase = GetABSwapContactsUseCase(addressBookRepository, simpleSwapAssetProvider)

    @Test
    fun keepsContactsOnCuratedChainsAndDropsTheRest() =
        runTest {
            val eth = contact(name = "Eth", chain = "eth")
            val ethUpper = contact(name = "EthUpper", chain = "ETH")
            val doge = contact(name = "Doge", chain = "doge")
            val zcash = contact(name = "Zcash", chain = null)
            every { addressBookRepository.contacts } returns flowOf(listOf(eth, ethUpper, doge, zcash))

            val result = useCase.observe().first()

            assertEquals(listOf(eth, ethUpper), result)
        }

    @Test
    fun emitsNullWhenContactsAreNull() =
        runTest {
            every { addressBookRepository.contacts } returns flowOf(null)

            assertEquals(null, useCase.observe().first())
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
            blockchain = chain?.let { blockchain(it) }
        )
}

private fun curatedAsset(chainTicker: String): SimpleSwapAsset =
    DynamicSimpleSwapAsset(
        tokenTicker = chainTicker,
        tokenName = stringRes(chainTicker),
        tokenIcon = imageRes(chainTicker),
        blockchain = blockchain(chainTicker),
    )

private fun blockchain(chainTicker: String) =
    SwapBlockchain(chainTicker = chainTicker, chainName = stringRes(chainTicker), chainIcon = imageRes(chainTicker))
