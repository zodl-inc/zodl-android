package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VoteServerFailoverTest {
    @Test
    fun firstVoteServerFailureFallsThroughToSecondServer() =
        runBlocking {
            val triedServers = mutableListOf<String>()

            val result =
                withVoteServerFailover(
                    path = "/shielded-vote/v1/rounds",
                    serverUrls = listOf(" https://first.example.com/ ", "https://second.example.com")
                ) { serverUrl ->
                    triedServers += serverUrl
                    if (serverUrl == "https://first.example.com") {
                        error("first server unavailable")
                    }
                    "rounds"
                }

            assertEquals("rounds", result)
            assertEquals(
                listOf("https://first.example.com", "https://second.example.com"),
                triedServers
            )
        }

    @Test
    fun allVoteServersFailedReturnsStableTypedError() {
        val exception =
            assertFailsWith<VotingServerFailoverException> {
                runBlocking {
                    withVoteServerFailover(
                        path = "/shielded-vote/v1/tally-results/abc",
                        serverUrls = listOf("https://first.example.com", "https://second.example.com")
                    ) {
                        error("server unavailable")
                    }
                }
            }

        assertEquals("/shielded-vote/v1/tally-results/abc", exception.path)
        assertEquals(
            listOf("https://first.example.com", "https://second.example.com"),
            exception.serverUrls
        )
        assertEquals("server unavailable", exception.lastError?.message)
    }

    @Test
    fun nonRetryableConfigFailureDoesNotTryNextServer() {
        val triedServers = mutableListOf<String>()
        val expected = VotingConfigException("round authentication failed")

        val exception =
            assertFailsWith<VotingConfigException> {
                runBlocking {
                    withVoteServerFailover(
                        path = "/shielded-vote/v1/rounds/active",
                        serverUrls = listOf("https://first.example.com", "https://second.example.com")
                    ) { serverUrl ->
                        triedServers += serverUrl
                        throw expected
                    }
                }
            }

        assertSame(expected, exception)
        assertEquals(listOf("https://first.example.com"), triedServers)
    }

    @Test
    fun endorsedRoundsTreatsBadRequestAndNotFoundAsEmpty() {
        assertTrue(shouldTreatEndorsedRoundsStatusAsEmpty(HttpStatusCode.BadRequest))
        assertTrue(shouldTreatEndorsedRoundsStatusAsEmpty(HttpStatusCode.NotFound))
        assertFalse(shouldTreatEndorsedRoundsStatusAsEmpty(HttpStatusCode.InternalServerError))
    }

    @Test
    fun exhaustedEndorsedRoundsFailoverTreatsOnlyBadRequestAndNotFoundAsEmpty() {
        assertTrue(
            shouldTreatEndorsedRoundsFailoverFailuresAsEmpty(
                listOf(HttpStatusCode.BadRequest, HttpStatusCode.NotFound)
            )
        )
        assertFalse(shouldTreatEndorsedRoundsFailoverFailuresAsEmpty(emptyList()))
        assertFalse(
            shouldTreatEndorsedRoundsFailoverFailuresAsEmpty(
                listOf(HttpStatusCode.NotFound, HttpStatusCode.InternalServerError)
            )
        )
        assertFalse(
            shouldTreatEndorsedRoundsFailoverFailuresAsEmpty(
                listOf(HttpStatusCode.NotFound, null)
            )
        )
    }

    @Test
    fun invalidConfiguredSourceFallsBackToBundledPinnedSource() {
        val source = resolvePinnedConfigSource("not a url")
        val bundled = resolvePinnedConfigSource(StaticVotingConfig.BUNDLED_PINNED_SOURCE)

        assertEquals(bundled, source)
    }

    @Test
    fun validConfiguredSourceCanBeUnpinned() {
        val source = resolvePinnedConfigSource("https://override.example.com/static-voting-config.json?foo=bar")

        assertEquals("https://override.example.com/static-voting-config.json?foo=bar", source.url)
        assertEquals(null, source.sha256)
    }
}
