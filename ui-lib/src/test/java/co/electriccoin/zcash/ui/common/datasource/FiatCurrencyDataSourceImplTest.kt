package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.CMCFiatCurrencyDto
import co.electriccoin.zcash.ui.common.model.GetCMCFiatMapResponse
import co.electriccoin.zcash.ui.common.model.GetCMCQuoteResponse
import co.electriccoin.zcash.ui.common.provider.CMCApiProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * MOB-1707: the supported-currency list comes from the exchange-rate provider, minus the codes
 * product decided never to offer and anything the platform currency table cannot name.
 */
class FiatCurrencyDataSourceImplTest {
    @Test
    fun excludesTheCodesProductNeverOffers() =
        runTest {
            val provider = FakeCMCApiProvider(listOf("USD", "CUP", "EUR", "IRR", "JPY", "RUB"))

            val currencies = dataSource(provider).getSupportedFiatCurrencies()

            assertEquals(listOf("USD", "EUR", "JPY"), currencies.map { it.code })
            assertEquals(setOf("CUP", "IRR", "RUB"), EXCLUDED_FIAT_CURRENCY_CODES)
        }

    @Test
    fun dropsUnknownNonAlpha3AndDuplicateCodes() =
        runTest {
            val provider = FakeCMCApiProvider(listOf("USD", "XYZ", "ZZZ1", "usd", "EUR"))

            val currencies = dataSource(provider).getSupportedFiatCurrencies()

            assertEquals(listOf("USD", "EUR"), currencies.map { it.code })
        }

    @Test
    fun blankKeyFailsWithoutCallingTheProvider() =
        runTest {
            val provider = FakeCMCApiProvider(listOf("USD"))

            assertFailsWith<ExchangeRateUnavailable> {
                dataSource(provider, apiKey = "  ").getSupportedFiatCurrencies()
            }

            assertEquals(0, provider.calls)
        }

    @Test
    fun passesTheApiKeyThrough() =
        runTest {
            val provider = FakeCMCApiProvider(listOf("USD"))

            dataSource(provider, apiKey = "secret").getSupportedFiatCurrencies()

            assertEquals(listOf("secret"), provider.apiKeys)
        }

    @Test
    fun emptyResponseFails() =
        runTest {
            val provider = FakeCMCApiProvider(listOf())

            assertFailsWith<ExchangeRateUnavailable> { dataSource(provider).getSupportedFiatCurrencies() }
        }

    @Test
    fun providerFailurePropagates() =
        runTest {
            val failure = RuntimeException("boom")
            val provider = FakeCMCApiProvider(listOf(), failure = failure)

            val thrown =
                assertFailsWith<RuntimeException> { dataSource(provider).getSupportedFiatCurrencies() }

            assertEquals(failure, thrown)
        }

    private fun dataSource(provider: CMCApiProvider, apiKey: String = "key") =
        FiatCurrencyDataSourceImpl(cmcApiProvider = provider, cmcApiKey = apiKey)
}

private class FakeCMCApiProvider(
    private val symbols: List<String>,
    private val failure: Exception? = null,
) : CMCApiProvider {
    var calls = 0
        private set
    val apiKeys = mutableListOf<String>()

    override suspend fun getExchangeRateQuote(apiKey: String, fiat: String): GetCMCQuoteResponse =
        error("Not used in these tests")

    override suspend fun getFiatMap(apiKey: String): GetCMCFiatMapResponse {
        calls++
        apiKeys += apiKey
        failure?.let { throw it }
        return GetCMCFiatMapResponse(
            data = symbols.mapIndexed { index, symbol -> CMCFiatCurrencyDto(id = index, symbol = symbol) }
        )
    }
}
