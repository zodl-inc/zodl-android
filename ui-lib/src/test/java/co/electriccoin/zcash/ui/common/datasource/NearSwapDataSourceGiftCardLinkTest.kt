package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.near.ErrorDto
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.QuoteResponseDto
import co.electriccoin.zcash.ui.common.model.near.SubmitDepositTransactionRequest
import co.electriccoin.zcash.ui.common.model.near.SwapStatusResponseDto
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.provider.GetNearSupportedTokensResponse
import co.electriccoin.zcash.ui.common.provider.NearApiProvider
import co.electriccoin.zcash.ui.common.provider.ResponseWithNearErrorException
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.TokenIconProvider
import co.electriccoin.zcash.ui.common.provider.TokenNameProvider
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretFixture
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A gift card link carries the card's spending key in its fragment. [NearSwapDataSource.requestQuote] must reject one
 * given as the recipient or the refund address, in any disguise of [GiftCardSecretFixture], before anything reaches
 * the 1Click API, and the rejection must not repeat the link.
 */
class NearSwapDataSourceGiftCardLinkTest {
    private val nearApiProvider = RecordingNearApiProvider()
    private val synchronizerProvider = mockk<SynchronizerProvider>()
    private val dataSource =
        NearSwapDataSource(
            nearApiProvider,
            mockk<TokenIconProvider>(relaxed = true),
            mockk<TokenNameProvider>(relaxed = true),
            mockk<BlockchainProvider>(relaxed = true),
            synchronizerProvider
        )

    private val origin = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val zec = SwapAssetTestFixture.zecAsset()

    @Test
    fun giftCardLinkAsRecipientIsRejectedBeforeAnyCall() =
        runBlocking {
            GiftCardSecretFixture.all.forEach { (name, link) ->
                val exception =
                    assertFailsWith<GiftCardAddressNotAllowedException>(name) {
                        requestQuote(refundAddress = ORDINARY_ADDRESS, destinationAddress = link)
                    }
                assertDoesNotLeak(exception)
            }

            assertEquals(0, nearApiProvider.calls)
            verify { synchronizerProvider wasNot Called }
        }

    @Test
    fun giftCardLinkAsRefundAddressIsRejectedBeforeAnyCall() =
        runBlocking {
            GiftCardSecretFixture.all.forEach { (name, link) ->
                val exception =
                    assertFailsWith<GiftCardAddressNotAllowedException>(name) {
                        requestQuote(
                            swapMode = SwapMode.FLEX_INPUT,
                            refundAddress = link,
                            destinationAddress = ORDINARY_ADDRESS
                        )
                    }
                assertDoesNotLeak(exception)
            }

            assertEquals(0, nearApiProvider.calls)
            verify { synchronizerProvider wasNot Called }
        }

    @Test
    fun ordinaryAddressesStillReachTheApi() =
        runBlocking {
            GiftCardSecretFixture.ordinaryAddresses.forEachIndexed { index, address ->
                assertFailsWith<QuoteLowAmountException>(address) {
                    requestQuote(refundAddress = ORDINARY_ADDRESS, destinationAddress = address)
                }

                assertEquals(index + 1, nearApiProvider.calls, address)
                assertEquals(ORDINARY_ADDRESS, nearApiProvider.lastRequest?.refundTo)
                assertEquals(address, nearApiProvider.lastRequest?.recipient)
            }
        }

    @Test
    fun exceptionMessageNamesNoLink() {
        val exception = GiftCardAddressNotAllowedException()

        assertDoesNotLeak(exception)
    }

    private fun assertDoesNotLeak(exception: Exception) {
        val texts = listOf(exception.message.orEmpty(), exception.toString(), exception.stackTraceToString())
        texts.forEach { text ->
            assertFalse(text.contains(SECRET), "exception text repeats the card secret")
            assertFalse(text.contains("gift.zodl.com", ignoreCase = true), "exception text repeats the link")
            assertFalse(text.contains("vizor", ignoreCase = true), "exception text repeats the link")
            assertFalse(text.contains(ORDINARY_ADDRESS), "exception text repeats an address")
        }
        assertTrue(exception.message.orEmpty().isNotBlank())
    }

    private suspend fun requestQuote(
        refundAddress: String,
        destinationAddress: String,
        swapMode: SwapMode = SwapMode.EXACT_INPUT,
    ) = dataSource.requestQuote(
        swapMode = swapMode,
        amount = BigDecimal("1"),
        refundAddress = refundAddress,
        originAsset = origin,
        destinationAddress = destinationAddress,
        destinationAsset = zec,
        slippage = BigDecimal("2"),
        affiliateAddress = "affiliate"
    )

    /**
     * Counts every call of every endpoint. A quote request answers with "No quotes found", which the data source
     * maps to [QuoteLowAmountException], so an accepted request ends without a quote to parse.
     */
    private class RecordingNearApiProvider : NearApiProvider {
        var calls = 0
        var lastRequest: QuoteRequest? = null

        override suspend fun getSupportedTokens(): GetNearSupportedTokensResponse = record { emptyList() }

        override suspend fun requestQuote(request: QuoteRequest): QuoteResponseDto =
            record {
                lastRequest = request
                throw mockk<ResponseWithNearErrorException>(relaxed = true) {
                    every { error } returns ErrorDto(message = "No quotes found", timestamp = "", path = "")
                }
            }

        override suspend fun submitDepositTransaction(request: SubmitDepositTransactionRequest) = record { }

        override suspend fun checkSwapStatus(depositAddress: String): SwapStatusResponseDto =
            record { error("checkSwapStatus is not expected") }

        private inline fun <T> record(block: () -> T): T {
            calls++
            return block()
        }
    }

    private companion object {
        const val SECRET = GiftCardSecretFixture.SECRET
        const val ORDINARY_ADDRESS = "u1ordinaryrefundaddress"
    }
}
