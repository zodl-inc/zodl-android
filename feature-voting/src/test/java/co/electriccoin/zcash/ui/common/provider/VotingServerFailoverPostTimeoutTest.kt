package co.electriccoin.zcash.ui.common.provider

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * `TimeoutCancellationException`'s (message: String) constructor is internal to kotlinx.coroutines
 * -- tests can't call it directly. A real instance is obtained the same way production code would
 * ever see one: a `withTimeout` block that actually times out.
 */
private fun fakeTimeoutException(): TimeoutCancellationException =
    runBlocking {
        try {
            withTimeout(1) { delay(1_000) }
            error("expected withTimeout to throw TimeoutCancellationException")
        } catch (e: TimeoutCancellationException) {
            e
        }
    }

/**
 * F-06 audit fix verification: Tests that voting server failover does NOT retry after timeout.
 *
 * A TimeoutCancellationException means the request may have timed out after delivery. For POSTs
 * (delegate vote, cast vote), retrying on another server would risk double-submission. The fix
 * makes shouldTryNextVoteServer() return false for timeouts, surfacing the error instead of
 * automatically failing over.
 *
 * Pre-send connection errors (IOException, etc.) are still safe to retry since the request
 * never reached the server.
 */
class VotingServerFailoverPostTimeoutTest {
    /**
     * Test that shouldTryNextVoteServer returns false for TimeoutCancellationException.
     */
    @Test
    fun testTimeoutDoesNotTriggerFailover() {
        val timeout = fakeTimeoutException()
        val shouldRetry = shouldTryNextVoteServer(timeout)

        assertEquals(
            "F-06 fix: TimeoutCancellationException should NOT trigger failover (may have delivered)",
            false,
            shouldRetry
        )
    }

    /**
     * Test that shouldTryNextVoteServer returns true for connection errors (safe to retry).
     */
    @Test
    fun testConnectionErrorTriggersFailover() {
        val connectionError = IOException("Connection refused")
        val shouldRetry = shouldTryNextVoteServer(connectionError)

        assertEquals(
            "Connection errors are pre-send failures, safe to retry on next server",
            true,
            shouldRetry
        )
    }

    /**
     * Test that withVoteServerFailover does NOT try second server after timeout.
     */
    @Test
    fun testFailoverDoesNotRetryAfterTimeout() =
        runTest {
            val servers = listOf("https://server1.example.com", "https://server2.example.com")
            var attemptedServers = mutableListOf<String>()

            val result =
                runCatching {
                    withVoteServerFailover(
                        path = "/shielded-vote/v1/delegate-vote",
                        serverUrls = servers,
                        operation = { serverUrl ->
                            attemptedServers.add(serverUrl)
                            // First server times out after possibly delivering the request
                            throw fakeTimeoutException()
                        }
                    )
                }
            // The timeout itself surfaces, not VotingServerFailoverException: that wrapper means every
            // server was tried, so downstream reconcile can tell "may have landed" from "never reached".
            assertTrue("Failover should fail after timeout", result.isFailure)
            assertTrue(
                "Should surface the raw timeout instead of retrying",
                result.exceptionOrNull() is TimeoutCancellationException
            )

            assertEquals(
                "F-06 fix: After timeout on first server, should NOT try second server",
                1,
                attemptedServers.size
            )
            assertEquals("https://server1.example.com", attemptedServers[0])
        }

    /**
     * Test that withVoteServerFailover DOES try second server after connection error.
     */
    @Test
    fun testFailoverRetriesAfterConnectionError() =
        runTest {
            val servers = listOf("https://server1.example.com", "https://server2.example.com")
            var attemptedServers = mutableListOf<String>()

            val result =
                runCatching {
                    withVoteServerFailover(
                        path = "/shielded-vote/v1/delegate-vote",
                        serverUrls = servers,
                        operation = { serverUrl ->
                            attemptedServers.add(serverUrl)
                            // First server fails with connection error (pre-send, safe to retry)
                            if (attemptedServers.size == 1) {
                                throw IOException("Connection refused")
                            }
                            // Second server also fails (to trigger VotingServerFailoverException)
                            throw IOException("All servers down")
                        }
                    )
                }
            assertTrue("Failover should fail after all servers tried", result.isFailure)
            assertTrue(
                "Should fail with VotingServerFailoverException",
                result.exceptionOrNull() is VotingServerFailoverException
            )

            assertEquals(
                "Connection error on first server should trigger retry on second server",
                2,
                attemptedServers.size
            )
            assertEquals("https://server1.example.com", attemptedServers[0])
            assertEquals("https://server2.example.com", attemptedServers[1])
        }

    /**
     * Test that withVoteServerFailover succeeds on second server after connection error on first.
     */
    @Test
    fun testFailoverSucceedsOnSecondServerAfterConnectionError() =
        runTest {
            val servers = listOf("https://server1.example.com", "https://server2.example.com")
            var attemptedServers = mutableListOf<String>()

            val result =
                withVoteServerFailover(
                    path = "/shielded-vote/v1/cast-vote",
                    serverUrls = servers,
                    operation = { serverUrl ->
                        attemptedServers.add(serverUrl)
                        if (attemptedServers.size == 1) {
                            // First server fails with connection error (pre-send, safe to retry)
                            throw IOException("Connection refused")
                        }
                        "success"
                    }
                )

            assertEquals(
                "Should have tried both servers (first failed, second succeeded)",
                2,
                attemptedServers.size
            )
            assertEquals("success", result)
        }

    /**
     * Test that 4xx errors still trigger failover (server-specific validation failures).
     */
    @Test
    fun testServerValidationErrorTriggersFailover() {
        val badRequest = Exception("HTTP 400: Invalid request")
        val shouldRetry = shouldTryNextVoteServer(badRequest)

        assertEquals(
            "Generic exceptions (including HTTP errors) should trigger failover",
            true,
            shouldRetry
        )
    }
}
