package co.electriccoin.zcash.ui.common.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behavioral tests for HTTP client retry logic (C-08 fix verification).
 *
 * Verifies that:
 * 1. Voting POST endpoints are never retried (they may carry OHTTP ciphertext with nonces)
 * 2. Non-voting endpoints ARE retried on 5xx errors
 * 3. Exchange rate endpoints are never retried
 *
 * This replaces the grep-based HttpClientProviderRetryTest.kt tests with actual behavioral
 * verification using mock HTTP engines.
 */
class HttpClientRetryBehaviorTest {
    /**
     * Test that voting POST endpoints (/delegate-vote, /cast-vote, /shares) are NOT retried
     * on 500 errors.
     */
    @Test
    fun testVotingPostsNotRetried() =
        runBlocking {
            val votingPaths =
                listOf(
                    "/shielded-vote/v1/delegate-vote",
                    "/shielded-vote/v1/cast-vote",
                    "/shielded-vote/v1/shares",
                )

            votingPaths.forEach { path ->
                val attemptCount = AtomicInteger(0)

                val mockEngine =
                    MockEngine { request ->
                        attemptCount.incrementAndGet()
                        respond(
                            content = ByteReadChannel("Server error"),
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain")
                        )
                    }

                val client =
                    HttpClient(mockEngine) {
                        expectSuccess = false
                        install(HttpRequestRetry) { configureClearnetRetry() }
                    }

                val result =
                    runCatching {
                        client.post("https://vote.example.com$path") {
                            // Voting POST
                        }
                    }
                // Expected to succeed (no exception with expectSuccess=false)
                assertTrue("Request should succeed", result.isSuccess)
                // But the response status should be 500
                assertTrue("Response should be 500", result.getOrNull()?.status?.value == 500)

                // Verify only ONE attempt was made (no retries)
                assertEquals(
                    "Voting path $path must not be retried on 500 error",
                    1,
                    attemptCount.get()
                )

                client.close()
            }
        }

    /**
     * Test that voting GET endpoints (/share-status) are NOT retried either.
     */
    @Test
    fun testVotingGetsNotRetried() =
        runBlocking {
            val attemptCount = AtomicInteger(0)

            val mockEngine =
                MockEngine { request ->
                    attemptCount.incrementAndGet()
                    respond(
                        content = ByteReadChannel("Server error"),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain")
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    expectSuccess = false
                    install(HttpRequestRetry) { configureClearnetRetry() }
                }

            val result =
                runCatching {
                    client.get("https://vote.example.com/shielded-vote/v1/share-status/round123/nullifier456")
                }
            // Expected to succeed (no exception thrown with expectSuccess=false)
            assertTrue("Request should succeed", result.isSuccess)
            // But the response status should be 500
            assertTrue("Response should be 500", result.getOrNull()?.status?.value == 500)

            // Verify only ONE attempt (no retries)
            assertEquals(
                "Voting GET /share-status must not be retried",
                1,
                attemptCount.get()
            )

            client.close()
        }

    /**
     * Test that NON-voting endpoints ARE retried on 500 errors (up to maxRetries).
     */
    @Test
    fun testNonVotingEndpointsAreRetried() =
        runBlocking {
            val attemptCount = AtomicInteger(0)

            val mockEngine =
                MockEngine { request ->
                    attemptCount.incrementAndGet()
                    respond(
                        content = ByteReadChannel("Server error"),
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain")
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    expectSuccess = false
                    install(HttpRequestRetry) { configureClearnetRetry() }
                }

            val result =
                runCatching {
                    client.get("https://api.example.com/some-other-endpoint")
                }
            // Expected to succeed (no exception with expectSuccess=false)
            assertTrue("Request should succeed", result.isSuccess)
            // But the response status should be 500 after retries
            assertTrue("Response should be 500", result.getOrNull()?.status?.value == 500)

            // Verify it was tried 5 times (1 initial + 4 retries)
            assertEquals(
                "Non-voting endpoints must be retried up to maxRetries=4",
                5,
                attemptCount.get()
            )

            client.close()
        }

    /**
     * Test that voting endpoints on exception are also not retried.
     */
    @Test
    fun testVotingPostsNotRetriedOnException() =
        runBlocking {
            val attemptCount = AtomicInteger(0)

            val mockEngine =
                MockEngine { request ->
                    attemptCount.incrementAndGet()
                    throw java.io.IOException("Connection failed")
                }

            val client =
                HttpClient(mockEngine) {
                    expectSuccess = false
                    install(HttpRequestRetry) { configureClearnetRetry() }
                }

            val result =
                runCatching {
                    client.post("https://vote.example.com/shielded-vote/v1/delegate-vote")
                }
            // Expected - connection failed
            assertTrue("Request should fail on connection error", result.isFailure)

            // Verify only ONE attempt (no retries on exception for voting paths)
            assertEquals(
                "Voting POST must not retry on exception",
                1,
                attemptCount.get()
            )

            client.close()
        }

    /**
     * Test that exchange-rate (CMC) requests are NOT retried on 500 errors or on exceptions.
     */
    @Test
    fun testExchangeRateRequestsNotRetried() =
        runBlocking {
            val statusAttempts = AtomicInteger(0)
            val statusClient =
                HttpClient(
                    MockEngine {
                        statusAttempts.incrementAndGet()
                        respond(
                            content = ByteReadChannel("Server error"),
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain")
                        )
                    }
                ) {
                    expectSuccess = false
                    install(HttpRequestRetry) { configureClearnetRetry() }
                }

            val response = statusClient.get("https://$CMC_API_HOST/v1/cryptocurrency/quotes/latest")
            assertEquals(500, response.status.value)
            assertEquals("Exchange-rate host must not be retried on 500", 1, statusAttempts.get())
            statusClient.close()

            val exceptionAttempts = AtomicInteger(0)
            val exceptionClient =
                HttpClient(
                    MockEngine {
                        exceptionAttempts.incrementAndGet()
                        throw java.io.IOException("Connection failed")
                    }
                ) {
                    expectSuccess = false
                    install(HttpRequestRetry) { configureClearnetRetry() }
                }

            val result = runCatching { exceptionClient.get("https://$CMC_API_HOST/v1/fiat/map") }
            assertTrue("Request should fail on connection error", result.isFailure)
            assertEquals("Exchange-rate host must not be retried on exception", 1, exceptionAttempts.get())
            exceptionClient.close()
        }

    /**
     * Test that the isVotingPath logic correctly identifies all voting endpoints. Uses the real production
     * isVotingPath() (internal, same package), not a reimplementation.
     */
    @Test
    fun testVotingPathIdentification() {
        val votingPaths =
            listOf(
                "https://vote.zodl.com/shielded-vote/v1/shares",
                "https://vote.zodl.com/shielded-vote/v1/share-status/abc/def",
                "https://vote.zodl.com/shielded-vote/v1/delegate-vote",
                "https://vote.zodl.com/shielded-vote/v1/cast-vote",
                "https://example.com/path?query=/shielded-vote/v1/shares",
            )

        val nonVotingPaths =
            listOf(
                "https://api.zodl.com/some-endpoint",
                "https://example.com/shielded-vote/v2/shares", // v2, not v1
                "https://example.com/api/data",
            )

        votingPaths.forEach { path ->
            assertTrue(
                "Path $path should be identified as voting path",
                path.isVotingPath()
            )
        }

        nonVotingPaths.forEach { path ->
            assertTrue(
                "Path $path should NOT be identified as voting path",
                !path.isVotingPath()
            )
        }
    }

    /**
     * Verify that all four voting endpoints are properly excluded.
     * This is the behavioral equivalent of the grep test checking for the four contains() calls.
     */
    @Test
    fun testAllFourVotingEndpointsExcluded() {
        // The four voting endpoints that must be excluded from retry:
        val endpoints =
            mapOf(
                "/shares" to "/shielded-vote/v1/shares",
                "/share-status" to "/shielded-vote/v1/share-status/",
                "/delegate-vote" to "/shielded-vote/v1/delegate-vote",
                "/cast-vote" to "/shielded-vote/v1/cast-vote",
            )

        endpoints.forEach { (name, path) ->
            val testUrl = "https://vote.example.com$path"
            assertTrue(
                "Endpoint $name ($path) must be excluded from retry",
                testUrl.isVotingPath()
            )
        }
    }
}
