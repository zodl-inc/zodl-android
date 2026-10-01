package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.ui.common.model.voting.EncryptedShare
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression tests for C-04 (Medium-High): Share operations (delegateShares, fetchShareStatus,
 * resubmitShare, fetchTxConfirmation) must route through OHTTP for predefined vote servers
 * when Tor is disabled, not go direct to clearnet.
 */
class VotingApiProviderOhttpRoutingTest {
    @Test
    fun postShareUsesOhttpForPredefinedServerWhenTorDisabled() =
        runBlocking {
            val ohttpRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    ohttpRequests = ohttpRequests,
                    torEnabled = false,
                    isCustomServer = false
                )

            val share =
                SharePayload(
                    sharesHash = ByteArray(32) { 1 },
                    proposalId = 3,
                    voteDecision = 1,
                    encShare = EncryptedShare(c1 = ByteArray(32) { 2 }, c2 = ByteArray(32) { 3 }, shareIndex = 0),
                    treePosition = 42L,
                    voteRoundId = "01".repeat(32)
                )

            runCatching {
                provider.delegateShares(listOf(share))
            }

            // Verify that OHTTP was used (requests were routed through OHTTP client)
            assertTrue(
                ohttpRequests.any { it.contains("/shielded-vote/v1/shares") },
                "postShare should route through OHTTP for predefined server when Tor is off"
            )
        }

    @Test
    fun fetchShareStatusUsesOhttpForPredefinedServerWhenTorDisabled() =
        runBlocking {
            val ohttpRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    ohttpRequests = ohttpRequests,
                    torEnabled = false,
                    isCustomServer = false
                )

            runCatching {
                provider.fetchShareStatus(
                    helperBaseUrl = "https://vote.example.com",
                    roundIdHex = "round-id",
                    nullifierHex = "nullifier"
                )
            }

            // Verify that OHTTP was used
            assertTrue(
                ohttpRequests.any { it.contains("/shielded-vote/v1/share-status") },
                "fetchShareStatus should route through OHTTP for predefined server when Tor is off"
            )
        }

    @Test
    fun fetchTxConfirmationUsesOhttpForPredefinedServerWhenTorDisabled() =
        runBlocking {
            val ohttpRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    ohttpRequests = ohttpRequests,
                    torEnabled = false,
                    isCustomServer = false
                )

            runCatching {
                provider.fetchTxConfirmation(txHash = "abc123")
            }

            // Verify that OHTTP was used
            assertTrue(
                ohttpRequests.any { it.contains("/shielded-vote/v1/tx/") },
                "fetchTxConfirmation should route through OHTTP for predefined server when Tor is off"
            )
        }

    @Test
    fun fetchShareStatusFailsClosedWhenOhttpCannotHandlePredefinedServerAndTorDisabled() =
        runBlocking {
            val ohttpRequests = mutableListOf<String>()
            val directRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    ohttpRequests = ohttpRequests,
                    directRequests = directRequests,
                    torEnabled = false,
                    isCustomServer = false,
                    ohttpCanHandle = false
                )

            assertFailsWith<IllegalStateException> {
                provider.fetchShareStatus(
                    helperBaseUrl = "https://vote.example.com",
                    roundIdHex = "round-id",
                    nullifierHex = "nullifier"
                )
            }

            assertTrue(directRequests.isEmpty(), "No direct request may be made, got $directRequests")
            assertTrue(ohttpRequests.isEmpty(), "No OHTTP request may be made, got $ohttpRequests")
        }

    @Test
    fun resubmitShareFailsClosedWhenOhttpCannotHandlePredefinedServerAndTorDisabled() =
        runBlocking {
            val ohttpRequests = mutableListOf<String>()
            val directRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    ohttpRequests = ohttpRequests,
                    directRequests = directRequests,
                    torEnabled = false,
                    isCustomServer = false,
                    ohttpCanHandle = false
                )
            val share =
                SharePayload(
                    sharesHash = ByteArray(32) { 1 },
                    proposalId = 3,
                    voteDecision = 1,
                    encShare = EncryptedShare(c1 = ByteArray(32) { 2 }, c2 = ByteArray(32) { 3 }, shareIndex = 0),
                    treePosition = 42L,
                    voteRoundId = "01".repeat(32)
                )

            // postShare reports a refused route as "not accepted" instead of throwing, but it must
            // never fall back to a direct request.
            val accepted =
                provider.resubmitShare(
                    payload = share,
                    candidateUrls = listOf("https://vote.example.com"),
                    excludeUrls = emptyList()
                )

            assertTrue(accepted.isEmpty(), "No server may accept the share, got $accepted")
            assertTrue(directRequests.isEmpty(), "No direct request may be made, got $directRequests")
            assertTrue(ohttpRequests.isEmpty(), "No OHTTP request may be made, got $ohttpRequests")
        }

    @Test
    fun shareOperationsUseDirectForCustomServerWhenTorDisabled() =
        runBlocking {
            val directRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    directRequests = directRequests,
                    torEnabled = false,
                    isCustomServer = true
                )

            runCatching {
                provider.fetchShareStatus(
                    helperBaseUrl = "https://custom.example.com",
                    roundIdHex = "round-id",
                    nullifierHex = "nullifier"
                )
            }

            // Verify that direct client was used, not OHTTP
            assertTrue(
                directRequests.any { it.contains("/shielded-vote/v1/share-status") },
                "Custom servers should use direct client, not OHTTP"
            )
        }

    @Test
    fun shareOperationsUseDirectWhenTorEnabled() =
        runBlocking {
            val directRequests = mutableListOf<String>()
            val provider =
                providerWithOhttpTracking(
                    directRequests = directRequests,
                    torEnabled = true,
                    isCustomServer = false
                )

            runCatching {
                provider.fetchShareStatus(
                    helperBaseUrl = "https://vote.example.com",
                    roundIdHex = "round-id",
                    nullifierHex = "nullifier"
                )
            }

            // Verify that direct (Tor) client was used, not OHTTP
            assertTrue(
                directRequests.any { it.contains("/shielded-vote/v1/share-status") },
                "When Tor is enabled, should use direct Tor client, not OHTTP"
            )
        }

    private fun providerWithOhttpTracking(
        ohttpRequests: MutableList<String> = mutableListOf(),
        directRequests: MutableList<String> = mutableListOf(),
        torEnabled: Boolean,
        isCustomServer: Boolean,
        ohttpCanHandle: Boolean = !torEnabled && !isCustomServer
    ): KtorVotingApiProvider =
        KtorVotingApiProvider(
            httpClientProvider =
                object : HttpClientProvider {
                    override suspend fun supportsKtorTimeouts(): Boolean = true

                    override suspend fun isTorEnabled(): Boolean = torEnabled

                    override suspend fun createTor(): HttpClient = create()

                    override suspend fun createOhttp(): OhttpHttpClient =
                        fakeOhttpClient(ohttpRequests, isSupported = ohttpCanHandle)

                    override suspend fun create(): HttpClient =
                        HttpClient(
                            MockEngine { request ->
                                val path = request.url.encodedPath
                                directRequests += path
                                when {
                                    path.contains("/static-voting-config.json") -> {
                                        respond(
                                            content = staticConfigJson(),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                                        )
                                    }

                                    path.contains("/dynamic-voting-config.json") -> {
                                        respond(
                                            content = dynamicConfigJson(),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                                        )
                                    }

                                    path.contains("/share-status") -> {
                                        respond(
                                            content = """{"status":"confirmed"}""",
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                                        )
                                    }

                                    path.contains("/shielded-vote/v1/shares") -> {
                                        respond(
                                            content = "{}",
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                                        )
                                    }

                                    path.contains("/tx/") -> {
                                        respond(
                                            content = """{"height":12,"code":0,"log":"","events":[]}""",
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                                        )
                                    }

                                    else -> {
                                        respond(content = "not found", status = HttpStatusCode.NotFound)
                                    }
                                }
                            }
                        ) {
                            expectSuccess = true
                            install(ContentNegotiation) { json() }
                        }
                },
            configurationRepository = TestConfigurationRepository(),
            votingChainConfigRepository = TestVotingChainConfigRepository(isCustom = isCustomServer),
            votingCryptoClient = unusedVotingCryptoClient()
        )

    /**
     * `OhttpHttpClient` is a final class with a private constructor (only buildable via its own
     * `create(codec, sender, ...)` factory, which needs the native `OhttpCodec`) — it can't
     * be subclassed, and a JVM test cannot load the native codec. MockK mocks it directly instead,
     * stubbing `canHandle`/`execute` at the exact
     * boundary `VotingApiProvider` calls, so this test only exercises the routing decision (OHTTP
     * vs direct), not the OHTTP wire protocol itself (covered separately at the SDK level).
     */
    private fun fakeOhttpClient(
        requests: MutableList<String>,
        isSupported: Boolean
    ): OhttpHttpClient =
        mockk {
            every { canHandle(any()) } returns isSupported
            coEvery { execute(any()) } answers {
                val request = firstArg<OhttpHttpClient.Request>()
                requests += request.url
                OhttpHttpClient.Response(
                    status = 200,
                    body =
                        when {
                            request.url.contains("/share-status") -> {
                                """{"status":"confirmed"}""".toByteArray()
                            }

                            request.url.contains("/shielded-vote/v1/shares") -> {
                                "{}".toByteArray()
                            }

                            request.url.contains("/tx/") -> {
                                """{"height":12,"code":0,"log":"","events":[]}""".toByteArray()
                            }

                            else -> {
                                "{}".toByteArray()
                            }
                        }
                )
            }
        }

    private class TestConfigurationRepository : ConfigurationRepository {
        override val configurationFlow: StateFlow<Configuration?> = MutableStateFlow(null)
        override val isFlexaAvailable: StateFlow<Boolean?> = MutableStateFlow(false)

        override suspend fun isFlexaAvailable(): Boolean = false
    }

    private class TestVotingChainConfigRepository(
        private val isCustom: Boolean
    ) : VotingChainConfigRepository {
        override val state: StateFlow<VotingChainConfigState> =
            MutableStateFlow(
                VotingChainConfigState(
                    selected =
                        if (isCustom) {
                            VotingChainConfigSelection.Custom("custom-chain")
                        } else {
                            VotingChainConfigSelection.Default
                        },
                    customChains =
                        if (isCustom) {
                            listOf(
                                VotingCustomChainConfig(
                                    id = "custom-chain",
                                    name = "Custom Chain",
                                    pinnedSource = "https://custom.example.com/config.json"
                                )
                            )
                        } else {
                            emptyList()
                        }
                )
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
        const val ADMIN_PUBKEY_BASE64 = "rKDbmhkoW9ja7dMiCV+1uTao7wXWV6xN/57erkrOuiQ="

        fun staticConfigJson(): String =
            """
            {
              "static_config_version": 1,
              "dynamic_config_url": "https://example.com/dynamic-voting-config.json",
              "trusted_keys": [
                {
                  "key_id": "valar-test",
                  "alg": "ed25519",
                  "pubkey": "$ADMIN_PUBKEY_BASE64"
                }
              ]
            }
            """.trimIndent()

        fun dynamicConfigJson(): String =
            """
            {
              "config_version": 1,
              "vote_servers": [{"url": "https://vote.example.com", "label": "vote"}],
              "pir_endpoints": [{"url": "https://pir.example.com", "label": "pir"}],
              "supported_versions": {
                "pir": ["v0"],
                "vote_protocol": "v0",
                "tally": "v0",
                "vote_server": "v1"
              },
              "rounds": {}
            }
            """.trimIndent()
    }
}

private fun unusedVotingCryptoClient(): VotingCryptoClient =
    Proxy.newProxyInstance(
        VotingCryptoClient::class.java.classLoader,
        arrayOf(VotingCryptoClient::class.java)
    ) { _, method, _ ->
        error("Unexpected VotingCryptoClient call: ${method.name}")
    } as VotingCryptoClient
