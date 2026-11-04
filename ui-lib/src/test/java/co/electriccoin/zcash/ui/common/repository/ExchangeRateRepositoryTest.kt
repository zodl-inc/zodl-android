package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.ExchangeRateUnavailable
import co.electriccoin.zcash.ui.common.provider.CmcHttpStatusException
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Covers the synchronizer-route fallback decision and the failure classification used by
 * [ExchangeRateRepositoryImpl] when the CMC exchange-rate lookup fails or is unavailable (MOB-1124).
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    // region retry of transient failures (MOB-2431)

    @Test
    fun refreshIsAllowedWhenThereIsNoRateEvenIfTheLockSaysNo() {
        // A first lookup that failed leaves no timestamp, so the time lock reports "disabled" forever.
        assertTrue(isRefreshAllowed(isRefreshEnabled = false, hasExchangeRate = false))
    }

    @Test
    fun refreshFollowsTheTimeLockOnceThereIsARate() {
        assertFalse(isRefreshAllowed(isRefreshEnabled = false, hasExchangeRate = true))
        assertTrue(isRefreshAllowed(isRefreshEnabled = true, hasExchangeRate = true))
        assertTrue(isRefreshAllowed(isRefreshEnabled = true, hasExchangeRate = false))
    }

    @Test
    fun gatewayAndConnectivityFailuresAreTransient() {
        // CMCApiProvider reports a non-2xx OHTTP answer as an IllegalStateException (CmcHttpStatusException), not a
        // ResponseException. A plain one, without a status, is still treated as a failure that may pass.
        assertTrue(isTransientExchangeRateFailure(IllegalStateException("CMC via OHTTP returned HTTP 503")))
        assertTrue(isTransientExchangeRateFailure(IOException("Connection reset")))
        assertTrue(isTransientExchangeRateFailure(SocketTimeoutException("read timed out")))
        assertTrue(isTransientExchangeRateFailure(UnknownHostException("pro-api.coinmarketcap.com")))
    }

    @Test
    fun serverSideAndRateLimitAnswersAreTransient() {
        listOf(500, 502, 503, 504, 429, 408).forEach { status ->
            assertTrue(
                isTransientExchangeRateFailure(CmcHttpStatusException(status, "https://example.test/")),
                "HTTP $status may pass"
            )
        }
    }

    @Test
    fun aRejectedRequestIsNotTransient() {
        // 401/403: the key the gateway holds was rejected; 400/404/422: the request itself is wrong.
        listOf(400, 401, 403, 404, 422).forEach { status ->
            assertFalse(
                isTransientExchangeRateFailure(CmcHttpStatusException(status, "https://example.test/")),
                "HTTP $status repeats identically"
            )
        }
    }

    @Test
    fun malformedJsonIsNotTransient() {
        assertFalse(isTransientExchangeRateFailure(SerializationException("Unexpected JSON token")))
    }

    @Test
    fun failuresThatRepeatIdenticallyAreNotTransient() {
        assertFalse(isTransientExchangeRateFailure(ExchangeRateUnavailable(message = "not found")))
        assertFalse(isTransientExchangeRateFailure(ContentConvertException("Unexpected JSON payload")))
        assertFalse(isTransientExchangeRateFailure(CancellationException("cancelled")))
    }

    @Test
    fun theBackgroundLoopKeepsAskingOnlyWhileThereIsNoRateAndTheFailureMayPass() {
        assertTrue(shouldKeepAskingForExchangeRate(hasExchangeRate = false, isLastFailureTransient = true))
        assertFalse(shouldKeepAskingForExchangeRate(hasExchangeRate = true, isLastFailureTransient = true))
        // A missing quote or a rejected key repeats identically, so the gateway is not asked again.
        assertFalse(shouldKeepAskingForExchangeRate(hasExchangeRate = false, isLastFailureTransient = false))
    }

    @Test
    fun theBackgroundWaitDoublesUpToTheCap() {
        val waits = generateSequence(30.seconds) { nextExchangeRateRetryWait(it) }.take(7).toList()
        assertEquals(
            listOf(30.seconds, 60.seconds, 120.seconds, 240.seconds, 300.seconds, 300.seconds, 300.seconds),
            waits
        )
    }

    @Test
    fun retryReturnsTheResultOfTheAttemptThatSucceeds() =
        runTest {
            var attempts = 0
            val result =
                retryTransientExchangeRateFailure(retryDelays = listOf(2.seconds, 6.seconds)) {
                    attempts++
                    if (attempts < 2) error("CMC via OHTTP returned HTTP 503")
                    "rate"
                }
            assertEquals("rate", result)
            assertEquals(2, attempts)
            assertEquals(2_000L, currentTime)
        }

    @Test
    fun retryGivesUpAfterTheLastDelayAndRethrowsTheLastFailure() =
        runTest {
            var attempts = 0
            val failure =
                runCatching {
                    retryTransientExchangeRateFailure(retryDelays = listOf(2.seconds, 6.seconds)) {
                        attempts++
                        throw IOException("attempt $attempts")
                    }
                }.exceptionOrNull()
            assertEquals("attempt 3", failure?.message)
            assertEquals(3, attempts)
            assertEquals(8_000L, currentTime)
        }

    @Test
    fun retryDoesNotRepeatAFailureThatRepeatsIdentically() =
        runTest {
            var attempts = 0
            val failure =
                runCatching {
                    retryTransientExchangeRateFailure(retryDelays = listOf(2.seconds, 6.seconds)) {
                        attempts++
                        throw ExchangeRateUnavailable(message = "Exchange rate not found in response")
                    }
                }.exceptionOrNull()
            assertTrue(failure is ExchangeRateUnavailable)
            assertEquals(1, attempts)
            assertEquals(0L, currentTime)
        }

    // endregion

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
