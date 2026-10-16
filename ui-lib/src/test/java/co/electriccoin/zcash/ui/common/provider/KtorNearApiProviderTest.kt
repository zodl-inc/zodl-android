package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.ui.common.model.near.AppFee
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.SubmitDepositTransactionRequest
import co.electriccoin.zcash.ui.common.model.near.SwapType
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [KtorNearApiProvider] drives the 1Click HTTP API through OHTTP. The contract these tests
 * pin down: all requests route through OHTTP (never create() or createTor()); the app NEVER sends
 * the `Authorization: Bearer` token (the gateway injects it server-side); depositAddress query params
 * are sent; NEAR error bodies are lifted into [OhttpNearErrorException]; unparsable error bodies fall
 * back to generic exceptions.
 */
class KtorNearApiProviderTest {
    private val capturedRequests = mutableListOf<OhttpHttpClient.Request>()

    private var responseStatus = 200
    private var responseBody = "[]"

    private var createCalls = 0
    private var createTorCalls = 0
    private var createOhttpCalls = 0

    private val provider = KtorNearApiProvider(RecordingHttpClientProvider())

    @Test
    fun getSupportedTokensDoesNotSendAuthorizationHeader() =
        runBlocking {
            responseBody = "[]"

            provider.getSupportedTokens()

            val request = capturedRequests.single()
            assertEquals("https://near.zodl.com/v0/tokens", request.url)
            assertEquals("GET", request.method)
            // The token list is public: the app never sends Authorization and the gateway injects none.
            assertNoAuthorizationHeader(request)
            assertUsesOhttpOnly()
        }

    @Test
    fun requestQuoteDoesNotSendAuthorizationHeader() =
        runBlocking {
            responseBody =
                """
                {
                  "timestamp": "2024-01-01T00:00:00Z",
                  "quoteRequest": {
                    "dry": false,
                    "swapType": "EXACT_INPUT",
                    "slippageTolerance": 100,
                    "originAsset": "origin",
                    "destinationAsset": "destination",
                    "amount": "1.0",
                    "refundTo": "refund",
                    "recipient": "recipient",
                    "deadline": "1970-01-01T00:00:00Z",
                    "appFees": [{"recipient": "affiliate", "fee": 75}]
                  },
                  "quote": {
                    "depositAddress": "d",
                    "amountIn": "1.0",
                    "amountInFormatted": "1.0",
                    "amountInUsd": "1.0",
                    "minAmountIn": "1.0",
                    "amountOut": "1.0",
                    "amountOutFormatted": "1.0",
                    "amountOutUsd": "1.0",
                    "minAmountOut": "1.0",
                    "deadline": "2024-01-01T00:00:00Z"
                  }
                }
                """.trimIndent()

            provider.requestQuote(quoteRequest())

            val request = capturedRequests.single()
            assertEquals("https://near.zodl.com/v0/quote", request.url)
            assertEquals("POST", request.method)
            assertTrue(request.headers.any { it.first == "Content-Type" && it.second == "application/json" })
            // The app must never send Authorization; the gateway injects it server-side.
            assertNoAuthorizationHeader(request)
            assertUsesOhttpOnly()
        }

    @Test
    fun submitDepositTransactionDoesNotSendAuthorizationHeader() =
        runBlocking {
            responseBody = ""

            provider.submitDepositTransaction(
                SubmitDepositTransactionRequest(txHash = "hash", depositAddress = "deposit")
            )

            val request = capturedRequests.single()
            assertEquals("https://near.zodl.com/v0/deposit/submit", request.url)
            assertEquals("POST", request.method)
            // The app must never send Authorization; the gateway injects it server-side.
            assertNoAuthorizationHeader(request)
            assertUsesOhttpOnly()
        }

    @Test
    fun checkSwapStatusDoesNotSendAuthorizationHeaderAndSendsDepositAddressParameter() =
        runBlocking {
            responseBody =
                """
                {
                  "status": "PENDING_DEPOSIT",
                  "updatedAt": "2024-01-01T00:00:00Z",
                  "quoteResponse": {
                    "timestamp": "2024-01-01T00:00:00Z",
                    "quoteRequest": {
                      "dry": false,
                      "swapType": "EXACT_INPUT",
                      "slippageTolerance": 100,
                      "originAsset": "origin",
                      "destinationAsset": "destination",
                      "amount": "1.0",
                      "refundTo": "refund",
                      "recipient": "recipient",
                      "deadline": "1970-01-01T00:00:00Z",
                      "appFees": [{"recipient": "affiliate", "fee": 75}]
                    },
                    "quote": {
                      "depositAddress": "d",
                      "amountIn": "1.0",
                      "amountInFormatted": "1.0",
                      "amountInUsd": "1.0",
                      "minAmountIn": "1.0",
                      "amountOut": "1.0",
                      "amountOutFormatted": "1.0",
                      "amountOutUsd": "1.0",
                      "minAmountOut": "1.0",
                      "deadline": "2024-01-01T00:00:00Z"
                    }
                  }
                }
                """.trimIndent()

            provider.checkSwapStatus(depositAddress = "deposit-address")

            val request = capturedRequests.single()
            // The depositAddress is a query parameter in the URL, not a secret — still sent by the app.
            assertTrue(
                request.url.contains("depositAddress=deposit-address"),
                "depositAddress parameter must be in URL"
            )
            assertTrue(request.url.startsWith("https://near.zodl.com/v0/status"))
            assertEquals("GET", request.method)
            // The app must never send Authorization; the gateway injects it server-side.
            assertNoAuthorizationHeader(request)
            assertUsesOhttpOnly()
        }

    @Test
    fun errorResponseWithNearErrorBodyMapsToTypedException() {
        runBlocking {
            responseStatus = 400
            responseBody = """{"message":"boom","timestamp":"t","path":"/v0/quote"}"""

            val exception =
                assertFailsWith<OhttpNearErrorException> {
                    responseBody = """{"message":"boom","timestamp":"t","path":"/v0/quote"}"""
                    provider.requestQuote(quoteRequest())
                }

            assertEquals("boom", exception.error.message)
            assertEquals(400, exception.status)
            assertUsesOhttpOnly()
        }
    }

    @Test
    fun errorResponseWithoutNearErrorBodyThrowsGenericException() {
        runBlocking {
            responseStatus = 400
            responseBody = "plain text, not a NEAR error"

            val exception =
                assertFailsWith<IllegalStateException> {
                    provider.requestQuote(quoteRequest())
                }

            // Unparsable body -> the generic error() is thrown, not the typed one.
            assertTrue(exception.message?.contains("HTTP 400") == true)
            assertTrue(exception.message?.contains("plain text") == true)
            assertUsesOhttpOnly()
        }
    }

    private fun assertNoAuthorizationHeader(request: OhttpHttpClient.Request) {
        // The app must never send Authorization; the gateway injects it server-side.
        assertFalse(
            request.headers.any { it.first.equals("Authorization", ignoreCase = true) },
            "Authorization header must not be sent by the app (gateway injects it)"
        )
    }

    private fun quoteRequest() =
        QuoteRequest(
            dry = false,
            swapType = SwapType.EXACT_INPUT,
            slippageTolerance = 100,
            originAsset = "origin",
            destinationAsset = "destination",
            amount = BigDecimal.ONE,
            refundTo = "refund",
            recipient = "recipient",
            deadline = Instant.fromEpochMilliseconds(0),
            appFees = listOf(AppFee(recipient = "affiliate", fee = 75))
        )

    private fun assertUsesOhttpOnly() {
        // OHTTP is the only path; create() and createTor() must never be called.
        assertEquals(1, createOhttpCalls, "createOhttp() must be called exactly once")
        assertEquals(0, createCalls, "create() must never be called (OHTTP only)")
        assertEquals(0, createTorCalls, "createTor() must never be called (OHTTP handles routing)")
    }

    private inner class RecordingHttpClientProvider : HttpClientProvider {
        override suspend fun isTorEnabled(): Boolean = false

        override suspend fun createTor(): HttpClient {
            createTorCalls++
            error("NEAR must never use createTor() (OHTTP handles routing)")
        }

        override suspend fun create(): HttpClient {
            createCalls++
            error("NEAR must never use create() (OHTTP only)")
        }

        override suspend fun createOhttp(): OhttpHttpClient {
            createOhttpCalls++
            return mockk<OhttpHttpClient> {
                every { canHandle(any()) } returns true
                coEvery { execute(any()) } answers {
                    val request = firstArg<OhttpHttpClient.Request>()
                    capturedRequests += request
                    OhttpHttpClient.Response(
                        status = responseStatus,
                        body = responseBody.toByteArray()
                    )
                }
            }
        }
    }
}
