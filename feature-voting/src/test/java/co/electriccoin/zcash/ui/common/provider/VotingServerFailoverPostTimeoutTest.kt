package co.electriccoin.zcash.ui.common.provider

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
        val timeout = TimeoutCancellationException("Request timed out")
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
    fun testFailoverDoesNotRetryAfterTimeout() = runTest {
        val servers = listOf("https://server1.example.com", "https://server2.example.com")
        var attemptedServers = mutableListOf<String>()

        try {
            withVoteServerFailover(
                path = "/shielded-vote/v1/delegate-vote",
                serverUrls = servers,
                operation = { serverUrl ->
                    attemptedServers.add(serverUrl)
                    // First server times out after possibly delivering the request
                    throw TimeoutCancellationException("Server took too long")
                }
            )
        } catch (e: VotingServerFailoverException) {
            // Expected: failover should give up after first timeout
        }

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
    fun testFailoverRetriesAfterConnectionError() = runTest {
        val servers = listOf("https://server1.example.com", "https://server2.example.com")
        var attemptedServers = mutableListOf<String>()

        try {
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
        } catch (e: VotingServerFailoverException) {
            // Expected: all servers tried
        }

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
    fun testFailoverSucceedsOnSecondServerAfterConnectionError() = runTest {
        val servers = listOf("https://server1.example.com", "https://server2.example.com")
        var attemptedServers = mutableListOf<String>()

        val result = withVoteServerFailover(
            path = "/shielded-vote/v1/cast-vote",
            serverUrls = servers,
            operation = { serverUrl ->
                attemptedServers.add(serverUrl)
                if (attemptedServers.size == 1) {
                    // First server fails with connection error (pre-send, safe to retry)
                    throw IOException("Connection refused")
                }
                // Second server succeeds
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
     * Document the problem scenario: timeout after delivery risks double-submission.
     */
    @Test
    fun testProblemScenario() {
        // Problem scenario WITHOUT the F-06 fix:
        //
        // 1. User submits a vote delegation via submitDelegation()
        // 2. Request goes to server1.example.com via executeWithVoteServerFailover()
        // 3. Server1 receives the POST and processes it (delegation recorded)
        // 4. Server1's response is slow (network congestion)
        // 5. Client timeout fires (voteServerFailoverTimeoutMillis = 30s)
        // 6. withVoteServerFailover() catches TimeoutCancellationException
        // 7. WITHOUT fix: shouldTryNextVoteServer(timeout) returns true
        // 8. Failover logic tries server2.example.com with the SAME delegation
        // 9. Server2 processes the duplicate delegation
        // 10. Result: delegation may be counted twice (if servers don't de-duplicate)
        //
        // WITH the F-06 fix:
        // - Step 7: shouldTryNextVoteServer(timeout) returns false
        // - Step 8: withVoteServerFailover() throws VotingServerFailoverException
        // - Step 9: App surfaces timeout error to user instead of silently double-submitting
        // - User can check status or re-submit manually if needed

        assertTrue(
            "This test documents the double-submission risk fixed by F-06",
            true
        )
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
