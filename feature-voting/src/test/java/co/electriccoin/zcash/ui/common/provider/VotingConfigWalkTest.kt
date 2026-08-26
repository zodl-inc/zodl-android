package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfigHashMismatchException
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the MOB-1809 mirror-failover machinery: the generic [walkConfigSources] algorithm
 * shared by the static and dynamic config legs, the static/dynamic failure classification
 * predicates, and the dynamic walk's end-to-end behavior through [KtorVotingApiProvider]
 * (order, 4xx/5xx handling, decode-failure handling, first-error retention, and
 * raw.githubusercontent.com cache-busting).
 *
 * The static walk's end-to-end mirror behavior is deliberately NOT re-exercised here through
 * [KtorVotingApiProvider.fetchServiceConfig] against the real bundled sources: those sources'
 * checksums are fixed, real SHA-256 pins, so a test cannot fabricate mismatched-then-matching
 * mock bytes for them without a SHA-256 preimage. Instead, [walkConfigSources] (the exact
 * mechanism [KtorVotingApiProvider] composes for both legs) and
 * [isRetryableStaticVotingConfigFailure] are tested directly below, together with
 * [co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfigTest]'s direct coverage of
 * [co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig.decodeAndVerify]'s hash-vs-decode
 * exception typing — the same combination [KtorVotingApiProvider.fetchStaticConfigWalk] relies on.
 */
class VotingConfigWalkTest {
    // region generic walkConfigSources

    @Test
    fun walkShortCircuitsOnFirstHealthySource() =
        runBlocking {
            val attempted = mutableListOf<String>()

            val result =
                walkConfigSources(
                    sources = listOf("a", "b"),
                    emptyMessage = "empty",
                    describe = { it },
                    shouldTryNext = { true }
                ) { source ->
                    attempted += source
                    "ok-$source"
                }

            assertEquals("ok-a", result)
            assertEquals(listOf("a"), attempted)
        }

    @Test
    fun walkFallsThroughRetryableFailureToNextSource() =
        runBlocking {
            val attempted = mutableListOf<String>()

            val result =
                walkConfigSources(
                    sources = listOf("a", "b"),
                    emptyMessage = "empty",
                    describe = { it },
                    shouldTryNext = { true }
                ) { source ->
                    attempted += source
                    if (source == "a") throw VotingConfigException("a unreachable")
                    "ok-$source"
                }

            assertEquals("ok-b", result)
            assertEquals(listOf("a", "b"), attempted)
        }

    @Test
    fun walkStopsImmediatelyOnAuthoritativeFailure() {
        val attempted = mutableListOf<String>()
        val authoritative = VotingConfigException("authoritative failure")

        val exception =
            assertFailsWith<VotingConfigException> {
                runBlocking {
                    walkConfigSources(
                        sources = listOf("a", "b"),
                        emptyMessage = "empty",
                        describe = { it },
                        shouldTryNext = { false }
                    ) { source ->
                        attempted += source
                        throw authoritative
                    }
                }
            }

        assertSame(authoritative, exception)
        assertEquals(listOf("a"), attempted)
    }

    @Test
    fun walkThrowsFirstErrorWhenEverySourceFails() {
        val firstError = VotingConfigException("first mirror: 503")
        val secondError = VotingConfigException("second mirror: 502")

        val exception =
            assertFailsWith<VotingConfigException> {
                runBlocking {
                    var attempt = 0
                    walkConfigSources(
                        sources = listOf("a", "b"),
                        emptyMessage = "empty",
                        describe = { it },
                        shouldTryNext = { true }
                    ) {
                        attempt += 1
                        throw if (attempt == 1) firstError else secondError
                    }
                }
            }

        assertSame(firstError, exception)
        assertEquals("first mirror: 503", exception.message)
    }

    @Test
    fun walkThrowsEmptyMessageForEmptySourceList() {
        val exception =
            assertFailsWith<VotingConfigException> {
                runBlocking {
                    walkConfigSources<String, String>(
                        sources = emptyList(),
                        emptyMessage = "no sources configured",
                        describe = { it },
                        shouldTryNext = { true }
                    ) { "unused" }
                }
            }

        assertEquals("no sources configured", exception.message)
    }

    // endregion

    // region failure classification

    @Test
    fun staticFailureClassificationTreatsFetchFailureAndHashMismatchAsRetryable() {
        assertTrue(isRetryableStaticVotingConfigFailure(StaticVotingConfigFetchFailedException("HTTP 503")))
        assertTrue(isRetryableStaticVotingConfigFailure(StaticVotingConfigHashMismatchException("mismatch")))
    }

    @Test
    fun staticFailureClassificationTreatsDecodeOrValidateFailureAsAuthoritative() {
        assertFalse(isRetryableStaticVotingConfigFailure(VotingConfigException("decode failed")))
    }

    @Test
    fun dynamicFailureClassificationTreatsTransientFailureAsRetryable() {
        assertTrue(isRetryableDynamicVotingConfigFailure(DynamicVotingConfigTransientException("HTTP 503")))
    }

    @Test
    fun dynamicFailureClassificationTreatsPlainVotingConfigExceptionAsAuthoritative() {
        assertFalse(isRetryableDynamicVotingConfigFailure(VotingConfigException("HTTP 404")))
    }

    // endregion

    // region dynamic walk integration (through KtorVotingApiProvider)

    @Test
    fun dynamicWalkTriesUrlsInOrder() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val provider =
                newProvider(
                    dynamicConfigUrls = listOf(FIRST_URL, SECOND_URL, THIRD_URL),
                    responses =
                        mapOf(
                            FIRST_URL to WalkMockResponse(HttpStatusCode.InternalServerError, "boom"),
                            SECOND_URL to WalkMockResponse(HttpStatusCode.InternalServerError, "boom"),
                            THIRD_URL to WalkMockResponse(HttpStatusCode.OK, validDynamicServiceConfigJson())
                        ),
                    requestedUrls = requestedUrls
                )

            provider.fetchServiceConfig()

            assertEquals(listOf(FIRST_URL, SECOND_URL, THIRD_URL), requestedUrls.map { it.substringBefore('?') })
        }

    @Test
    fun dynamicWalkDoesNotFallThroughOn404() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val provider =
                newProvider(
                    dynamicConfigUrls = listOf(FIRST_URL, SECOND_URL),
                    responses =
                        mapOf(
                            FIRST_URL to WalkMockResponse(HttpStatusCode.NotFound, "not found"),
                            SECOND_URL to WalkMockResponse(HttpStatusCode.OK, validDynamicServiceConfigJson())
                        ),
                    requestedUrls = requestedUrls
                )

            assertFailsWith<VotingConfigException> {
                provider.fetchServiceConfig()
            }

            assertEquals(listOf(FIRST_URL), requestedUrls.map { it.substringBefore('?') })
        }

    @Test
    fun dynamicWalkDoesNotFallThroughOnDecodeFailure() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val provider =
                newProvider(
                    dynamicConfigUrls = listOf(FIRST_URL, SECOND_URL),
                    responses =
                        mapOf(
                            FIRST_URL to WalkMockResponse(HttpStatusCode.OK, "not json"),
                            SECOND_URL to WalkMockResponse(HttpStatusCode.OK, validDynamicServiceConfigJson())
                        ),
                    requestedUrls = requestedUrls
                )

            assertFailsWith<VotingConfigException> {
                provider.fetchServiceConfig()
            }

            assertEquals(listOf(FIRST_URL), requestedUrls.map { it.substringBefore('?') })
        }

    @Test
    fun dynamicWalkRetainsFirstErrorWithDistinguishablePayloads() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val provider =
                newProvider(
                    dynamicConfigUrls = listOf(FIRST_URL, SECOND_URL),
                    responses =
                        mapOf(
                            FIRST_URL to WalkMockResponse(HttpStatusCode.ServiceUnavailable, "first-mirror-payload"),
                            SECOND_URL to WalkMockResponse(HttpStatusCode.BadGateway, "second-mirror-payload")
                        ),
                    requestedUrls = requestedUrls
                )

            val exception =
                assertFailsWith<VotingConfigException> {
                    provider.fetchServiceConfig()
                }

            assertTrue(exception.message.orEmpty().contains("503"), exception.message.orEmpty())
            assertFalse(exception.message.orEmpty().contains("502"), exception.message.orEmpty())
            assertEquals(listOf(FIRST_URL, SECOND_URL), requestedUrls.map { it.substringBefore('?') })
        }

    @Test
    fun dynamicWalkCacheBustsOnlyRawGithubusercontentAndKeepsErrorMessageClean() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val provider =
                newProvider(
                    dynamicConfigUrls = listOf(RAW_GITHUB_URL, FIRST_URL),
                    responses =
                        mapOf(
                            RAW_GITHUB_URL to WalkMockResponse(HttpStatusCode.InternalServerError, "boom"),
                            FIRST_URL to WalkMockResponse(HttpStatusCode.InternalServerError, "boom")
                        ),
                    requestedUrls = requestedUrls
                )

            val exception =
                assertFailsWith<VotingConfigException> {
                    provider.fetchServiceConfig()
                }

            assertEquals(2, requestedUrls.size)
            assertTrue(requestedUrls[0].contains("zodl_cache_bust="), requestedUrls[0])
            assertFalse(requestedUrls[1].contains("zodl_cache_bust="), requestedUrls[1])
            assertFalse(exception.message.orEmpty().contains("zodl_cache_bust"), exception.message.orEmpty())
        }

    @Test
    fun dynamicWalkSendsNoCacheHeadersPerAttempt() =
        runBlocking {
            val noCacheHeaderSeen = mutableListOf<Boolean>()
            val provider =
                newProviderCapturingHeaders(
                    dynamicConfigUrls = listOf(FIRST_URL, SECOND_URL),
                    responses =
                        mapOf(
                            FIRST_URL to WalkMockResponse(HttpStatusCode.InternalServerError, "boom"),
                            SECOND_URL to WalkMockResponse(HttpStatusCode.OK, validDynamicServiceConfigJson())
                        ),
                    noCacheHeaderSeen = noCacheHeaderSeen
                )

            provider.fetchServiceConfig()

            assertEquals(listOf(true, true), noCacheHeaderSeen)
        }

    // endregion

    private fun newProvider(
        dynamicConfigUrls: List<String>,
        responses: Map<String, WalkMockResponse>,
        requestedUrls: MutableList<String>
    ): KtorVotingApiProvider =
        KtorVotingApiProvider(
            httpClientProvider =
                object : HttpClientProvider {
                    override suspend fun supportsKtorTimeouts(): Boolean = true

                    override suspend fun createTor(): HttpClient = create()

                    override suspend fun create(): HttpClient =
                        HttpClient(
                            MockEngine { request ->
                                val fullUrl = request.url.toString()
                                if (fullUrl.startsWith(STATIC_CONFIG_URL)) {
                                    respond(
                                        content = staticConfigJson(dynamicConfigUrls),
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                                    )
                                } else {
                                    requestedUrls += fullUrl
                                    respondFor(fullUrl, responses)
                                }
                            }
                        ) {
                            expectSuccess = true
                        }
                },
            configurationRepository = TestConfigurationRepository(),
            votingChainConfigRepository = TestVotingChainConfigRepository(),
            votingCryptoClient = unusedVotingCryptoClient()
        )

    private fun newProviderCapturingHeaders(
        dynamicConfigUrls: List<String>,
        responses: Map<String, WalkMockResponse>,
        noCacheHeaderSeen: MutableList<Boolean>
    ): KtorVotingApiProvider =
        KtorVotingApiProvider(
            httpClientProvider =
                object : HttpClientProvider {
                    override suspend fun supportsKtorTimeouts(): Boolean = true

                    override suspend fun createTor(): HttpClient = create()

                    override suspend fun create(): HttpClient =
                        HttpClient(
                            MockEngine { request ->
                                val fullUrl = request.url.toString()
                                if (fullUrl.startsWith(STATIC_CONFIG_URL)) {
                                    respond(
                                        content = staticConfigJson(dynamicConfigUrls),
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                                    )
                                } else {
                                    noCacheHeaderSeen += (request.headers[HttpHeaders.CacheControl] == "no-cache")
                                    respondFor(fullUrl, responses)
                                }
                            }
                        ) {
                            expectSuccess = true
                        }
                },
            configurationRepository = TestConfigurationRepository(),
            votingChainConfigRepository = TestVotingChainConfigRepository(),
            votingCryptoClient = unusedVotingCryptoClient()
        )

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
        const val FIRST_URL = "https://example.com/first-dynamic-voting-config.json"
        const val SECOND_URL = "https://example.com/second-dynamic-voting-config.json"
        const val THIRD_URL = "https://example.com/third-dynamic-voting-config.json"
        const val RAW_GITHUB_URL =
            "https://raw.githubusercontent.com/valargroup/token-holder-voting-config/main/dynamic-voting-config.json"

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

private data class WalkMockResponse(
    val status: HttpStatusCode,
    val content: String
)

private fun MockRequestHandleScope.respondFor(
    fullUrl: String,
    responses: Map<String, WalkMockResponse>
): HttpResponseData {
    val response = responses[fullUrl.substringBefore('?')]
    return if (response == null) {
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

private fun staticConfigJson(dynamicConfigUrls: List<String>): String =
    """
    {
      "static_config_version": 2,
      "dynamic_config_urls": [${dynamicConfigUrls.joinToString(",") { url -> "\"$url\"" }}],
      "trusted_keys": [
        {
          "key_id": "valar-test",
          "alg": "ed25519",
          "pubkey": "rKDbmhkoW9ja7dMiCV+1uTao7wXWV6xN/57erkrOuiQ="
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

private fun unusedVotingCryptoClient(): VotingCryptoClient =
    Proxy.newProxyInstance(
        VotingCryptoClient::class.java.classLoader,
        arrayOf(VotingCryptoClient::class.java)
    ) { _, method, _ ->
        error("Unexpected VotingCryptoClient call: ${method.name}")
    } as VotingCryptoClient
