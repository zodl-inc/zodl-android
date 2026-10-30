package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpPirSnapshotResolverTest {
    @Test
    fun predefinedPirProbeUsesOhttpWithTorOff() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttp = mutableListOf<String>()
            val resolver = resolver(torEnabled = false, direct = direct, ohttp = ohttp)

            assertEquals(
                PIR_URL,
                resolver.resolve(VotingRouteDecision(isTor = false, isCustomServer = false), listOf(PIR_URL), HEIGHT)
            )
            assertEquals(listOf("$PIR_URL/root"), ohttp)
            assertTrue(direct.isEmpty(), "predefined PIR probe must not go direct with Tor off")
        }

    @Test
    fun predefinedPirProbeFailsClosedWhenGatewayCannotReachHost() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttpClient = ohttpClient(mutableListOf(), canHandle = false)
            val resolver = resolver(torEnabled = false, direct = direct, ohttpClient = ohttpClient)

            assertFailsWith<PirSnapshotResolverException.NoMatchingEndpoint> {
                resolver.resolve(VotingRouteDecision(isTor = false, isCustomServer = false), listOf(PIR_URL), HEIGHT)
            }
            assertTrue(direct.isEmpty(), "an unsupported predefined PIR host must not fall back to direct")
            coVerify(exactly = 0) { ohttpClient.execute(any()) }
        }

    @Test
    fun customPirProbeGoesDirectWithTorOff() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttp = mutableListOf<String>()
            val resolver = resolver(torEnabled = false, direct = direct, ohttp = ohttp)

            assertEquals(
                PIR_URL,
                resolver.resolve(VotingRouteDecision(isTor = false, isCustomServer = true), listOf(PIR_URL), HEIGHT)
            )
            assertEquals(listOf("/root"), direct)
            assertTrue(ohttp.isEmpty(), "a custom PIR server must never go through the gateway")
        }

    @Test
    fun pirProbeUsesTorClientWithTorOn() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttp = mutableListOf<String>()
            val resolver = resolver(torEnabled = true, direct = direct, ohttp = ohttp)

            assertEquals(
                PIR_URL,
                resolver.resolve(VotingRouteDecision(isTor = true, isCustomServer = false), listOf(PIR_URL), HEIGHT)
            )
            assertEquals(listOf("/root"), direct)
            assertTrue(ohttp.isEmpty(), "with Tor on the PIR probe goes over Tor, not OHTTP")
        }

    @Test
    fun decisionWithTorOffBeatsTorOnAndCustomPreference() =
        runBlocking {
            withTimeout(TEST_TIMEOUT_MS) {
                val direct = mutableListOf<String>()
                val ohttp = mutableListOf<String>()
                val torFlags = mutableListOf<Boolean>()
                // Preference says Tor on and a custom chain; the operation's decision says Tor off, predefined.
                val resolver =
                    resolver(torEnabled = true, direct = direct, ohttp = ohttp, torFlags = torFlags)

                assertEquals(
                    PIR_URL,
                    resolver.resolve(
                        VotingRouteDecision(isTor = false, isCustomServer = false),
                        listOf(PIR_URL),
                        HEIGHT
                    )
                )
                assertEquals(listOf("$PIR_URL/root"), ohttp)
                assertTrue(direct.isEmpty(), "the decision (Tor off, predefined) wins: OHTTP, never direct or Tor")
                assertTrue(torFlags.isEmpty(), "no Ktor client may be built for an OHTTP probe")
            }
        }

    @Test
    fun decisionWithTorOnBeatsTorOffPreference() =
        runBlocking {
            withTimeout(TEST_TIMEOUT_MS) {
                val direct = mutableListOf<String>()
                val ohttp = mutableListOf<String>()
                val torFlags = mutableListOf<Boolean>()
                // Preference says Tor off (predefined, so OHTTP would be chosen); the decision says Tor on.
                val resolver =
                    resolver(torEnabled = false, direct = direct, ohttp = ohttp, torFlags = torFlags)

                assertEquals(
                    PIR_URL,
                    resolver.resolve(
                        VotingRouteDecision(isTor = true, isCustomServer = false),
                        listOf(PIR_URL),
                        HEIGHT
                    )
                )
                assertEquals(listOf(true), torFlags, "the probe client must be the Tor client")
                assertEquals(listOf("/root"), direct)
                assertTrue(ohttp.isEmpty(), "the decision (Tor on) wins over the Tor-off preference: no OHTTP")
            }
        }

    private fun resolver(
        torEnabled: Boolean,
        direct: MutableList<String>,
        ohttp: MutableList<String> = mutableListOf(),
        ohttpClient: OhttpHttpClient = ohttpClient(ohttp, canHandle = true),
        torFlags: MutableList<Boolean> = mutableListOf()
    ) = HttpPirSnapshotResolver(
        httpClientProvider =
            object : HttpClientProvider {
                override suspend fun create(): HttpClient =
                    HttpClient(
                        MockEngine { request ->
                            direct += request.url.encodedPath
                            respond(
                                content = ROOT_JSON,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                    )

                override suspend fun createTor(): HttpClient = create()

                override suspend fun create(tor: Boolean): HttpClient {
                    torFlags += tor
                    return if (tor) createTor() else create()
                }

                override suspend fun createOhttp(): OhttpHttpClient = ohttpClient

                override suspend fun isTorEnabled(): Boolean = torEnabled
            }
    )

    private fun ohttpClient(
        requests: MutableList<String>,
        canHandle: Boolean
    ): OhttpHttpClient =
        mockk {
            every { canHandle(any()) } returns canHandle
            coEvery { execute(any()) } answers {
                requests += firstArg<OhttpHttpClient.Request>().url
                OhttpHttpClient.Response(status = 200, body = ROOT_JSON.toByteArray())
            }
        }

    private companion object {
        const val PIR_URL = "https://lb-pir-primary.valargroup.org"
        const val HEIGHT = 3_000_000L
        const val ROOT_JSON = """{"height":3000000}"""
    }
}

/** Upper bound for a routing test; a retry loop that never ends fails here instead of hanging CI. */
private const val TEST_TIMEOUT_MS = 60_000L
