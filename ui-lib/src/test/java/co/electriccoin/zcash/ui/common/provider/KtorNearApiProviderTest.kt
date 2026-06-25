package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.near.AppFee
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.SubmitDepositTransactionRequest
import co.electriccoin.zcash.ui.common.model.near.SwapType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [KtorNearApiProvider] drives the 1Click HTTP API. The contract these tests pin down: the three
 * privileged endpoints (quote, deposit submit, status) must carry the `Authorization: Bearer …`
 * credential, the public token list must NOT, and a NEAR error body must be lifted into a typed
 * [ResponseWithNearErrorException] (falling back to the raw [ResponseException] otherwise).
 */
class KtorNearApiProviderTest {
    private val requests = mutableListOf<HttpRequestData>()

    private var responseStatus = HttpStatusCode.OK
    private var responseBody = "[]"
    private var responseContentType = "application/json"

    private var createCalls = 0
    private var createTorCalls = 0

    private val provider = KtorNearApiProvider(RecordingHttpClientProvider())

    @Test
    fun getSupportedTokensDoesNotSendAuthorizationHeader() =
        runBlocking {
            responseBody = "[]"

            provider.getSupportedTokens()

            val request = requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v0/tokens", request.url.encodedPath)
            // The token list is public — no credential must leak onto it.
            assertNull(request.headers[HttpHeaders.Authorization])
            assertRespectsTorPreference()
        }

    @Test
    fun requestQuoteSendsBearerAuthorizationHeader() =
        runBlocking {
            // The QuoteResponseDto body intentionally fails to deserialize; we only assert the request.
            responseBody = "{}"
            runCatching { provider.requestQuote(quoteRequest()) }

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v0/quote", request.url.encodedPath)
            assertBearerAuthorization(request)
            assertRespectsTorPreference()
        }

    @Test
    fun submitDepositTransactionSendsBearerAuthorizationHeader() =
        runBlocking {
            provider.submitDepositTransaction(
                SubmitDepositTransactionRequest(txHash = "hash", depositAddress = "deposit")
            )

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v0/deposit/submit", request.url.encodedPath)
            assertBearerAuthorization(request)
            assertRespectsTorPreference()
        }

    @Test
    fun checkSwapStatusSendsBearerAuthorizationHeaderAndDepositAddressParameter() =
        runBlocking {
            responseBody = "{}"
            runCatching { provider.checkSwapStatus(depositAddress = "deposit-address") }

            val request = requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v0/status", request.url.encodedPath)
            assertEquals("deposit-address", request.url.parameters["depositAddress"])
            assertBearerAuthorization(request)
            assertRespectsTorPreference()
        }

    @Test
    fun errorResponseWithNearErrorBodyMapsToTypedException() {
        runBlocking {
            responseStatus = HttpStatusCode.BadRequest
            responseBody = """{"message":"boom","timestamp":"t","path":"/v0/quote"}"""

            val exception =
                assertFailsWith<ResponseWithNearErrorException> {
                    provider.requestQuote(quoteRequest())
                }

            assertEquals("boom", exception.error.message)
            assertRespectsTorPreference()
        }
    }

    @Test
    fun errorResponseWithoutNearErrorBodyRethrowsRawResponseException() {
        runBlocking {
            responseStatus = HttpStatusCode.BadRequest
            responseBody = "plain text, not a NEAR error"
            responseContentType = "text/plain"

            val exception =
                assertFailsWith<ResponseException> {
                    provider.requestQuote(quoteRequest())
                }

            // Unparsable body -> the raw transport exception is rethrown, not the typed one.
            assertFalse(exception is ResponseWithNearErrorException)
            assertRespectsTorPreference()
        }
    }

    private fun assertBearerAuthorization(request: HttpRequestData) {
        val authorization = request.headers[HttpHeaders.Authorization]
        assertNotNull(authorization, "Authorization header must be present")
        assertTrue(authorization.startsWith("Bearer "), "Authorization header must be a Bearer token")
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

    private fun assertRespectsTorPreference() {
        // create() routes over Tor or clearnet per the user's Tor setting; createTor() would force Tor
        // regardless. The swap API must defer to the preference, so it must use create() and never createTor().
        assertEquals(1, createCalls, "create() (honors the user's Tor preference) must be called exactly once")
        assertEquals(0, createTorCalls, "createTor() (forces Tor) must never be called by KtorNearApiProvider")
    }

    private inner class RecordingHttpClientProvider : HttpClientProvider {
        override suspend fun createTor(): HttpClient {
            createTorCalls++
            error("KtorNearApiProvider must use create() (honors the Tor preference), never createTor()")
        }

        override suspend fun create(): HttpClient {
            createCalls++
            return HttpClient(
                MockEngine { request ->
                    requests += request
                    respond(
                        content = responseBody,
                        status = responseStatus,
                        headers = headersOf(HttpHeaders.ContentType, responseContentType)
                    )
                }
            ) {
                install(ContentNegotiation) { json() }
                // Mirrors the production client so non-2xx responses surface as ResponseExceptions.
                expectSuccess = true
            }
        }
    }
}
