package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
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
            val resolver = resolver(torEnabled = false, isCustom = false, direct = direct, ohttp = ohttp)

            assertEquals(PIR_URL, resolver.resolve(listOf(PIR_URL), HEIGHT))
            assertEquals(listOf("$PIR_URL/root"), ohttp)
            assertTrue(direct.isEmpty(), "predefined PIR probe must not go direct with Tor off")
        }

    @Test
    fun predefinedPirProbeFailsClosedWhenGatewayCannotReachHost() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttpClient = ohttpClient(mutableListOf(), canHandle = false)
            val resolver = resolver(torEnabled = false, isCustom = false, direct = direct, ohttpClient = ohttpClient)

            assertFailsWith<PirSnapshotResolverException.NoMatchingEndpoint> {
                resolver.resolve(listOf(PIR_URL), HEIGHT)
            }
            assertTrue(direct.isEmpty(), "an unsupported predefined PIR host must not fall back to direct")
            coVerify(exactly = 0) { ohttpClient.execute(any()) }
        }

    @Test
    fun customPirProbeGoesDirectWithTorOff() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttp = mutableListOf<String>()
            val resolver = resolver(torEnabled = false, isCustom = true, direct = direct, ohttp = ohttp)

            assertEquals(PIR_URL, resolver.resolve(listOf(PIR_URL), HEIGHT))
            assertEquals(listOf("/root"), direct)
            assertTrue(ohttp.isEmpty(), "a custom PIR server must never go through the gateway")
        }

    @Test
    fun pirProbeUsesTorClientWithTorOn() =
        runBlocking {
            val direct = mutableListOf<String>()
            val ohttp = mutableListOf<String>()
            val resolver = resolver(torEnabled = true, isCustom = false, direct = direct, ohttp = ohttp)

            assertEquals(PIR_URL, resolver.resolve(listOf(PIR_URL), HEIGHT))
            assertEquals(listOf("/root"), direct)
            assertTrue(ohttp.isEmpty(), "with Tor on the PIR probe goes over Tor, not OHTTP")
        }

    private fun resolver(
        torEnabled: Boolean,
        isCustom: Boolean,
        direct: MutableList<String>,
        ohttp: MutableList<String> = mutableListOf(),
        ohttpClient: OhttpHttpClient = ohttpClient(ohttp, canHandle = true)
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

                override suspend fun createOhttp(): OhttpHttpClient = ohttpClient

                override suspend fun isTorEnabled(): Boolean = torEnabled
            },
        votingChainConfigRepository = FakeVotingChainConfigRepository(isCustom)
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

    private class FakeVotingChainConfigRepository(
        isCustom: Boolean
    ) : VotingChainConfigRepository {
        override val state: StateFlow<VotingChainConfigState> =
            MutableStateFlow(
                if (isCustom) {
                    VotingChainConfigState(
                        selected = VotingChainConfigSelection.Custom("custom"),
                        customChains =
                            listOf(VotingCustomChainConfig("custom", "Custom", "https://custom.example/config.json"))
                    )
                } else {
                    VotingChainConfigState()
                }
            )

        override suspend fun get(): VotingChainConfigState = state.value

        override suspend fun selectDefault() = Unit

        override suspend fun selectCustom(id: String) = Unit

        override suspend fun addCustom(
            name: String,
            pinnedSource: String
        ): VotingCustomChainConfig = error("unused")

        override suspend fun updateCustom(
            id: String,
            name: String,
            pinnedSource: String
        ) = Unit

        override suspend fun deleteCustom(id: String) = Unit
    }

    private companion object {
        const val PIR_URL = "https://lb-pir-primary.valargroup.org"
        const val HEIGHT = 3_000_000L
        const val ROOT_JSON = """{"height":3000000}"""
    }
}
