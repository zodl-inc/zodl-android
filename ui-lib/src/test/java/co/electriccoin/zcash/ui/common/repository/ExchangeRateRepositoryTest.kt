package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.ExchangeRateUnavailable
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the synchronizer-route fallback decision and the failure classification used by
 * [ExchangeRateRepositoryImpl] when the CMC exchange-rate lookup fails or is unavailable (MOB-1124).
 */
class ExchangeRateRepositoryTest {
    @Test
    fun fallsBackWhenCmcUnavailable() {
        assertTrue(shouldFallBackToSynchronizerRoute(isCmcAvailable = false))
    }

    @Test
    fun doesNotFallBackAroundTheGatewayWhenCmcAvailable() {
        // A failed CMC lookup through the OHTTP gateway must not be retried around it.
        assertFalse(shouldFallBackToSynchronizerRoute(isCmcAvailable = true))
    }

    // region classifyExchangeRateError

    @Test
    fun classifiesMissingQuoteAsRateUnavailable() {
        assertEquals(
            ExchangeRateError.CMC_EXCHANGE_RATE_UNAVAILABLE,
            classifyExchangeRateError(ExchangeRateUnavailable(message = "Exchange rate not found in response"))
        )
    }

    @Test
    fun classifiesServerErrorAsServerConnectionError() =
        runTest {
            assertEquals(
                ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
                classifyExchangeRateError(httpFailure(HttpStatusCode.InternalServerError))
            )
        }

    @Test
    fun classifiesUnauthorizedAsServerConnectionError() =
        runTest {
            assertEquals(
                ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
                classifyExchangeRateError(httpFailure(HttpStatusCode.Unauthorized))
            )
        }

    @Test
    fun classifiesNotFoundAsServerConnectionError() =
        runTest {
            assertEquals(
                ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
                classifyExchangeRateError(httpFailure(HttpStatusCode.NotFound))
            )
        }

    @Test
    fun classifiesDnsFailureAsServerConnectionError() {
        assertEquals(
            ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
            classifyExchangeRateError(UnknownHostException("pro-api.coinmarketcap.com"))
        )
    }

    @Test
    fun classifiesMalformedResponseAsServerConnectionError() {
        // ktor's JsonConvertException is a ContentConvertException subtype, so this branch covers it.
        assertEquals(
            ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
            classifyExchangeRateError(ContentConvertException("Unexpected JSON payload"))
        )
    }

    @Test
    fun classifiesTimeoutAsTransient() {
        assertNull(classifyExchangeRateError(SocketTimeoutException("read timed out")))
    }

    @Test
    fun classifiesGenericConnectivityFailureAsTransient() {
        assertNull(classifyExchangeRateError(IOException("Connection reset")))
    }

    @Test
    fun classifiesUnknownFailureAsTransient() {
        assertNull(classifyExchangeRateError(RuntimeException("unexpected")))
    }

    /**
     * Drives a real [HttpClient] with `expectSuccess = true` against a [MockEngine] returning
     * [status], so the resulting exception is a genuine ktor `ResponseException` subtype
     * (ServerResponseException for 5xx, ClientRequestException for 4xx) rather than a hand-rolled
     * stand-in.
     */
    private suspend fun httpFailure(status: HttpStatusCode): Throwable =
        HttpClient(MockEngine { respond(content = "error", status = status) }) {
            expectSuccess = true
        }.use { client ->
            runCatching { client.get("https://example.test/") }.exceptionOrNull()
        } ?: error("Expected an HTTP failure for $status")

    // endregion
}
