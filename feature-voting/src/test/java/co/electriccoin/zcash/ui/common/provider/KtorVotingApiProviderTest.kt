package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class KtorVotingApiProviderTest {
    @Test
    fun validateConfigSourceOnlyFetchesStaticConfig() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider = newProvider(requests)

            provider.validateConfigSource(PinnedConfigSource.parse(STATIC_CONFIG_URL))

            assertEquals(listOf("/static-voting-config.json"), requests)
        }

    @Test
    fun fetchServiceConfigStillFetchesAndValidatesDynamicConfig() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider = newProvider(requests)

            assertFailsWith<VotingConfigException> {
                provider.fetchServiceConfig()
            }

            assertEquals(listOf("/static-voting-config.json", "/dynamic-voting-config.json"), requests)
        }

    @Test
    fun fetchServiceConfigFallsBackToSecondDynamicConfigUrlWhenFirstFails() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider =
                newProviderWithResponses(
                    requests = requests,
                    responses =
                        mapOf(
                            "/static-voting-config.json" to
                                MockResponse(
                                    content =
                                        staticConfigJsonV2(
                                            dynamicConfigUrls =
                                                listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL)
                                        ),
                                    status = HttpStatusCode.OK
                                ),
                            "/first-dynamic-voting-config.json" to
                                MockResponse(
                                    content = "temporary dynamic config failure",
                                    status = HttpStatusCode.InternalServerError
                                ),
                            "/second-dynamic-voting-config.json" to
                                MockResponse(content = validDynamicServiceConfigJson(), status = HttpStatusCode.OK)
                        )
                )

            val serviceConfig = provider.fetchServiceConfig()

            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/first-dynamic-voting-config.json",
                    "/second-dynamic-voting-config.json"
                ),
                requests
            )
            assertEquals(1, serviceConfig.voteServers.size)
        }

    @Test
    fun fetchServiceConfigThrowsWhenAllDynamicConfigUrlsFail() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider =
                newProviderWithResponses(
                    requests = requests,
                    responses =
                        mapOf(
                            "/static-voting-config.json" to
                                MockResponse(
                                    content =
                                        staticConfigJsonV2(
                                            dynamicConfigUrls =
                                                listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL)
                                        ),
                                    status = HttpStatusCode.OK
                                ),
                            "/first-dynamic-voting-config.json" to
                                MockResponse(
                                    content = "temporary dynamic config failure",
                                    status = HttpStatusCode.InternalServerError
                                ),
                            "/second-dynamic-voting-config.json" to
                                MockResponse(
                                    content = "temporary dynamic config failure",
                                    status = HttpStatusCode.InternalServerError
                                )
                        )
                )

            assertFailsWith<VotingConfigException> {
                provider.fetchServiceConfig()
            }

            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/first-dynamic-voting-config.json",
                    "/second-dynamic-voting-config.json"
                ),
                requests
            )
        }

    @Test
    fun fetchShareStatusUsesHelperTimeoutWhenSupported() =
        runBlocking {
            val requests = mutableListOf<String>()
            val requestTimeoutCapabilities = mutableListOf<Boolean>()
            val provider =
                newProvider(
                    requests = requests,
                    supportsKtorTimeouts = true,
                    requestTimeoutCapabilities = requestTimeoutCapabilities
                )

            runCatching {
                provider.fetchShareStatus(
                    helperBaseUrl = "https://example.com",
                    roundIdHex = "round-id",
                    nullifierHex = "nullifier"
                )
            }

            assertEquals(listOf("/shielded-vote/v1/share-status/round-id/nullifier"), requests)
            assertEquals(listOf(true), requestTimeoutCapabilities)
        }

    @Test
    fun fetchShareStatusSkipsHelperTimeoutWhenUnsupported() =
        runBlocking {
            val requests = mutableListOf<String>()
            val requestTimeoutCapabilities = mutableListOf<Boolean>()
            val provider =
                newProvider(
                    requests = requests,
                    supportsKtorTimeouts = false,
                    requestTimeoutCapabilities = requestTimeoutCapabilities
                )

            runCatching {
                provider.fetchShareStatus(
                    helperBaseUrl = "https://example.com",
                    roundIdHex = "round-id",
                    nullifierHex = "nullifier"
                )
            }

            assertEquals(listOf("/shielded-vote/v1/share-status/round-id/nullifier"), requests)
            assertEquals(listOf(false), requestTimeoutCapabilities)
            assertFalse(requestTimeoutCapabilities.single())
        }

    private fun newProvider(
        requests: MutableList<String>,
        supportsKtorTimeouts: Boolean = true,
        requestTimeoutCapabilities: MutableList<Boolean> = mutableListOf()
    ) =
        KtorVotingApiProvider(
            httpClientProvider = TestHttpClientProvider(requests, supportsKtorTimeouts, requestTimeoutCapabilities),
            configurationRepository = TestConfigurationRepository(),
            votingChainConfigRepository = TestVotingChainConfigRepository(),
            votingCryptoClient = unusedVotingCryptoClient()
        )

    private fun newProviderWithResponses(
        requests: MutableList<String>,
        responses: Map<String, MockResponse>
    ) = KtorVotingApiProvider(
        httpClientProvider = ResponseMapHttpClientProvider(requests, responses),
        configurationRepository = TestConfigurationRepository(),
        votingChainConfigRepository = TestVotingChainConfigRepository(),
        votingCryptoClient = unusedVotingCryptoClient()
    )

    private class TestHttpClientProvider(
        private val requests: MutableList<String>,
        private val supportsKtorTimeouts: Boolean,
        private val requestTimeoutCapabilities: MutableList<Boolean>
    ) : HttpClientProvider {
        override suspend fun supportsKtorTimeouts(): Boolean = supportsKtorTimeouts

        override suspend fun createTor(): HttpClient = create()

        override suspend fun create(): HttpClient =
            HttpClient(
                MockEngine { request ->
                    requests += request.url.encodedPath
                    requestTimeoutCapabilities += (request.getCapabilityOrNull(HttpTimeoutCapability) != null)
                    when (request.url.encodedPath) {
                        "/static-voting-config.json" -> {
                            respond(
                                content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/dynamic-voting-config.json" -> {
                            respond(
                                content = "temporary dynamic config failure",
                                status = HttpStatusCode.InternalServerError
                            )
                        }

                        "/shielded-vote/v1/share-status/round-id/nullifier" -> {
                            respond(
                                content = """{"status":"confirmed"}""",
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
            }
    }

    private class ResponseMapHttpClientProvider(
        private val requests: MutableList<String>,
        private val responses: Map<String, MockResponse>
    ) : HttpClientProvider {
        override suspend fun supportsKtorTimeouts(): Boolean = true

        override suspend fun createTor(): HttpClient = create()

        override suspend fun create(): HttpClient =
            HttpClient(
                MockEngine { request ->
                    val path = request.url.encodedPath
                    requests += path
                    val response = responses[path]
                    if (response == null) {
                        respond(content = "not found", status = HttpStatusCode.NotFound)
                    } else {
                        respond(
                            content = response.content,
                            status = response.status,
                            headers =
                                if (response.status == HttpStatusCode.OK) {
                                    headersOf(HttpHeaders.ContentType, "application/json")
                                } else {
                                    headersOf()
                                }
                        )
                    }
                }
            ) {
                expectSuccess = true
            }
    }

    private class TestConfigurationRepository : ConfigurationRepository {
        override val configurationFlow: StateFlow<Configuration?> = MutableStateFlow(null)
        override val isFlexaAvailable: StateFlow<Boolean?> = MutableStateFlow(false)

        override suspend fun isFlexaAvailable(): Boolean = false
    }

    private class TestVotingChainConfigRepository : VotingChainConfigRepository {
        override val state: StateFlow<VotingChainConfigState> = MutableStateFlow(TEST_CHAIN_CONFIG_STATE)

        override suspend fun get(): VotingChainConfigState = TEST_CHAIN_CONFIG_STATE

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
        const val STATIC_CONFIG_URL = "https://example.com/static-voting-config.json"
        const val DYNAMIC_CONFIG_URL = "https://example.com/dynamic-voting-config.json"
        const val FIRST_DYNAMIC_CONFIG_URL = "https://example.com/first-dynamic-voting-config.json"
        const val SECOND_DYNAMIC_CONFIG_URL = "https://example.com/second-dynamic-voting-config.json"
        val TEST_CHAIN_CONFIG_STATE =
            VotingChainConfigState(
                selected = VotingChainConfigSelection.Custom("test-chain"),
                customChains =
                    listOf(
                        VotingCustomChainConfig(
                            id = "test-chain",
                            name = "Test Chain",
                            pinnedSource = STATIC_CONFIG_URL
                        )
                    )
            )
    }
}

private const val ADMIN_PUBKEY_BASE64 = "rKDbmhkoW9ja7dMiCV+1uTao7wXWV6xN/57erkrOuiQ="

private fun staticConfigJson(dynamicConfigUrl: String): String =
    """
    {
      "static_config_version": 1,
      "dynamic_config_url": "$dynamicConfigUrl",
      "trusted_keys": [
        {
          "key_id": "valar-test",
          "alg": "ed25519",
          "pubkey": "$ADMIN_PUBKEY_BASE64"
        }
      ]
    }
    """.trimIndent()

private fun staticConfigJsonV2(dynamicConfigUrls: List<String>): String =
    """
    {
      "static_config_version": 2,
      "dynamic_config_urls": [${dynamicConfigUrls.joinToString(",") { url -> "\"$url\"" }}],
      "trusted_keys": [
        {
          "key_id": "valar-test",
          "alg": "ed25519",
          "pubkey": "$ADMIN_PUBKEY_BASE64"
        }
      ]
    }
    """.trimIndent()

private fun validDynamicServiceConfigJson(): String =
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

private data class MockResponse(
    val content: String,
    val status: HttpStatusCode
)

private fun unusedVotingCryptoClient(): VotingCryptoClient =
    Proxy.newProxyInstance(
        VotingCryptoClient::class.java.classLoader,
        arrayOf(VotingCryptoClient::class.java)
    ) { _, method, _ ->
        error("Unexpected VotingCryptoClient call: ${method.name}")
    } as VotingCryptoClient
