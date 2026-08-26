package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.toLowerHex
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
    fun invalidConfiguredSourceFallsBackToBundledPinnedSources() {
        val sources = resolvePinnedConfigSource("not a url")

        assertEquals(StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES, sources)
    }

    @Test
    fun emptyConfiguredSourceFallsBackToBundledPinnedSources() {
        val sources = resolvePinnedConfigSource("")

        assertEquals(StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES, sources)
    }

    @Test
    fun validConfiguredSourceCanBeUnpinned() {
        val sources = resolvePinnedConfigSource("https://override.example.com/static-voting-config.json?foo=bar")

        assertEquals(1, sources.size)
        assertEquals("https://override.example.com/static-voting-config.json?foo=bar", sources.single().url)
        assertEquals(null, sources.single().sha256)
    }

    @Test
    fun bundledMirrorEqualOverrideResolvesToFullBundledWalk() {
        val sources = resolvePinnedConfigSource(StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR)

        assertEquals(StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES, sources)
    }

    @Test
    fun bundledUrlWithDifferentChecksumStaysSingleSource() {
        val differentChecksum = "0a".repeat(32)
        val overrideUrl =
            StaticVotingConfig.BUNDLED_PINNED_SOURCE.substringBefore("checksum=") + "checksum=sha256:$differentChecksum"

        val sources = resolvePinnedConfigSource(overrideUrl)

        assertEquals(1, sources.size)
        assertEquals(differentChecksum, sources.single().sha256?.toLowerHex())
    }
}
