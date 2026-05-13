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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class KtorVotingApiProviderTest {
    @Test
    fun validateConfigSourceOnlyFetchesStaticConfig() = runBlocking {
        val requests = mutableListOf<String>()
        val provider = newProvider(requests)

        provider.validateConfigSource(PinnedConfigSource.parse(STATIC_CONFIG_URL))

        assertEquals(listOf("/static-voting-config.json"), requests)
    }

    @Test
    fun fetchServiceConfigStillFetchesAndValidatesDynamicConfig() = runBlocking {
        val requests = mutableListOf<String>()
        val provider = newProvider(requests)

        assertFailsWith<VotingConfigException> {
            provider.fetchServiceConfig()
        }

        assertEquals(listOf("/static-voting-config.json", "/dynamic-voting-config.json"), requests)
    }

    private fun newProvider(requests: MutableList<String>) =
        KtorVotingApiProvider(
            httpClientProvider = TestHttpClientProvider(requests),
            configurationRepository = TestConfigurationRepository(),
            votingChainConfigRepository = TestVotingChainConfigRepository(),
            votingCryptoClient = unusedVotingCryptoClient()
        )

    private class TestHttpClientProvider(
        private val requests: MutableList<String>
    ) : HttpClientProvider {
        override suspend fun create(): HttpClient =
            HttpClient(
                MockEngine { request ->
                    requests += request.url.encodedPath
                    when (request.url.encodedPath) {
                        "/static-voting-config.json" ->
                            respond(
                                content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                        "/dynamic-voting-config.json" ->
                            respond(
                                content = "temporary dynamic config failure",
                                status = HttpStatusCode.InternalServerError
                            )

                        else ->
                            respond(content = "not found", status = HttpStatusCode.NotFound)
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
        val TEST_CHAIN_CONFIG_STATE = VotingChainConfigState(
            selected = VotingChainConfigSelection.Custom("test-chain"),
            customChains = listOf(
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

private fun unusedVotingCryptoClient(): VotingCryptoClient =
    Proxy.newProxyInstance(
        VotingCryptoClient::class.java.classLoader,
        arrayOf(VotingCryptoClient::class.java)
    ) { _, method, _ ->
        error("Unexpected VotingCryptoClient call: ${method.name}")
    } as VotingCryptoClient
