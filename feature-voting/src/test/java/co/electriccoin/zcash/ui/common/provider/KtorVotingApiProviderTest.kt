package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.configuration.model.entry.ConfigKey
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.ui.common.model.voting.ChainCommitmentTreeLatestResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainCommitmentTreeLeavesResponse
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.EncryptedShare
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRouteDecision
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class KtorVotingApiProviderTest {
    @Test
    fun commitmentTreePathsAreRoundScoped() {
        assertEquals(
            "/shielded-vote/v1/commitment-tree/round-id/latest",
            commitmentTreeLatestPath("round-id"),
        )
        assertEquals(
            "/shielded-vote/v1/commitment-tree/round-id/leaves?from_height=11&to_height=20",
            commitmentTreeLeavesPath("round-id", fromHeight = 11, toHeight = 20),
        )
    }

    @Test
    fun commitmentTreeResponsesParseLatestHeightAndAbsoluteIndices() {
        val latest =
            Json.decodeFromString<ChainCommitmentTreeLatestResponse>(
                """{"tree":{"height":123,"next_index":2,"root":"ignored"}}""",
            )
        val leaves =
            Json.decodeFromString<ChainCommitmentTreeLeavesResponse>(
                """
                {
                  "blocks": [
                    {"height":120,"leaves":["AQ=="],"root":"ignored"},
                    {"height":123,"start_index":1,"leaves":["Ag=="],"root":"ignored"}
                  ],
                  "next_from_height": 124
                }
                """.trimIndent(),
            )

        assertEquals(123, latest.tree.height)
        assertEquals(2, latest.tree.nextIndex)
        assertEquals(0, leaves.blocks[0].startIndex)
        assertEquals(1, leaves.blocks[1].startIndex)
        assertEquals(listOf("Ag=="), leaves.blocks[1].toModel().leavesBase64)
        assertEquals(124, leaves.toModel().nextFromHeight)
    }

    @Test
    fun emptyCommitmentTreeDefaultsOmittedProtoFieldsToZero() {
        val latest =
            Json.decodeFromString<ChainCommitmentTreeLatestResponse>(
                """{"tree":{}}""",
            )

        assertEquals(0, latest.tree.height)
        assertEquals(0, latest.tree.nextIndex)
    }

    @Test
    fun commitmentTreeProviderSendsCursorQueryParameters() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider =
                newProvider(
                    requests = requests,
                    responseOverrides =
                        mapOf(
                            "/dynamic-voting-config.json" to TestResponse(dynamicVotingConfigJson()),
                            "/shielded-vote/v1/commitment-tree/round-id/latest" to
                                TestResponse("""{"tree":{"height":20,"next_index":2}}"""),
                            "/shielded-vote/v1/commitment-tree/round-id/leaves?from_height=11&to_height=20" to
                                TestResponse("""{"blocks":[],"next_from_height":0}"""),
                        ),
                )

            assertEquals(
                CommitmentTreeLatest(height = 20, nextIndex = 2),
                provider.fetchCommitmentTreeLatest("round-id", decision = CUSTOM_DIRECT),
            )
            assertEquals(
                CommitmentTreeLeafPage(blocks = emptyList(), nextFromHeight = 0),
                provider.fetchCommitmentTreeLeafPage(
                    "round-id",
                    fromHeight = 11,
                    toHeight = 20,
                    decision = CUSTOM_DIRECT,
                ),
            )
            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/dynamic-voting-config.json",
                    "/shielded-vote/v1/commitment-tree/round-id/latest",
                    "/shielded-vote/v1/commitment-tree/round-id/leaves?from_height=11&to_height=20",
                ),
                requests,
            )
        }

    @Test
    fun transactionConfirmationParserPreservesSuccessfulEvents() {
        val confirmation =
            """
            {
              "height": "12",
              "code": 0,
              "log": "",
              "events": [
                {
                  "type": "delegate_vote",
                  "attributes": [
                    {"key": "leaf_index", "value": "7"}
                  ]
                }
              ]
            }
            """.trimIndent().toTxConfirmation()

        assertEquals(12, confirmation.height)
        assertEquals(0, confirmation.code)
        assertEquals("7", confirmation.event("delegate_vote")?.attribute("leaf_index"))
    }

    @Test
    fun transactionConfirmationParserPreservesRejectedStatus() {
        val confirmation =
            """
            {
              "height": 12,
              "code": 2,
              "log": "transaction failed",
              "events": []
            }
            """.trimIndent().toTxConfirmation()

        assertEquals(2, confirmation.code)
        assertEquals("transaction failed", confirmation.log)
    }

    @Test
    fun validateConfigSourceOnlyFetchesStaticConfig() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider = newProvider(requests)

            provider.validateConfigSource(PinnedConfigSource.parse(STATIC_CONFIG_URL), decision = CUSTOM_DIRECT)

            assertEquals(listOf("/static-voting-config.json"), requests)
        }

    @Test
    fun fetchServiceConfigStillFetchesAndValidatesDynamicConfig() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider = newProvider(requests)

            assertFailsWith<VotingConfigException> {
                provider.fetchServiceConfig(decision = CUSTOM_DIRECT)
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
                                                listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL),
                                        ),
                                    status = HttpStatusCode.OK,
                                ),
                            "/first-dynamic-voting-config.json" to
                                MockResponse(
                                    content = "temporary dynamic config failure",
                                    status = HttpStatusCode.InternalServerError,
                                ),
                            "/second-dynamic-voting-config.json" to
                                MockResponse(content = validDynamicServiceConfigJson(), status = HttpStatusCode.OK),
                        ),
                )

            val serviceConfig = provider.fetchServiceConfig(decision = CUSTOM_DIRECT)

            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/first-dynamic-voting-config.json",
                    "/second-dynamic-voting-config.json",
                ),
                requests,
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
                                                listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL),
                                        ),
                                    status = HttpStatusCode.OK,
                                ),
                            "/first-dynamic-voting-config.json" to
                                MockResponse(
                                    content = "temporary dynamic config failure",
                                    status = HttpStatusCode.InternalServerError,
                                ),
                            "/second-dynamic-voting-config.json" to
                                MockResponse(
                                    content = "temporary dynamic config failure",
                                    status = HttpStatusCode.InternalServerError,
                                ),
                        ),
                )

            assertFailsWith<VotingConfigException> {
                provider.fetchServiceConfig(decision = CUSTOM_DIRECT)
            }

            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/first-dynamic-voting-config.json",
                    "/second-dynamic-voting-config.json",
                ),
                requests,
            )
        }

    // region config-request timeout capability

    /**
     * The Ktor timeout capability only exists on the direct/Tor client. With Tor off the config legs go
     * through OHTTP instead, where the bound is applied app-side (see the OHTTP tests below).
     */
    @Test
    fun configRequestsCarryFifteenSecondTimeoutCapabilityWhenSupported() =
        runBlocking {
            val timeoutCapabilities = mutableListOf<Pair<String, Long?>>()
            val provider = multiEndpointProvider(timeoutCapabilities = timeoutCapabilities, torEnabled = true)

            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)

            val configPaths = setOf("/static-voting-config.json", "/dynamic-voting-config.json")
            val configCapabilities = timeoutCapabilities.filter { (path, _) -> path in configPaths }
            assertEquals(listOf(15_000L, 15_000L), configCapabilities.map { (_, millis) -> millis })
        }

    @Test
    fun ohttpStaticConfigFetchIsBoundedByTheConfigTimeout() =
        runBlocking {
            val provider = hangingConfigProvider(hangingPath = "/static-voting-config.json")

            val exception =
                assertFailsWith<StaticVotingConfigFetchFailedException> {
                    provider.fetchServiceConfig(
                        decision = DEFAULT_OHTTP,
                    )
                }

            assertTrue(exception.message.orEmpty().contains("timed out"), exception.message.orEmpty())
        }

    @Test
    fun ohttpDynamicConfigFetchIsBoundedByTheConfigTimeoutAndFallsThroughToTheNextMirror() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider = hangingConfigProvider(hangingPath = "/first-dynamic-voting-config.json", requests = requests)

            val serviceConfig = provider.fetchServiceConfig(decision = DEFAULT_OHTTP)

            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/first-dynamic-voting-config.json",
                    "/second-dynamic-voting-config.json",
                ),
                requests,
            )
            assertEquals(1, serviceConfig.voteServers.size)
        }

    @Test
    fun voteServerRequestDoesNotCarryConfigTimeoutCapability() =
        runBlocking {
            val timeoutCapabilities = mutableListOf<Pair<String, Long?>>()
            val provider = multiEndpointProvider(timeoutCapabilities = timeoutCapabilities)

            provider.fetchAllRounds(decision = CUSTOM_DIRECT)

            val roundsCapability = timeoutCapabilities.first { (path, _) -> path == ROUNDS_TEST_PATH }.second
            assertEquals(null, roundsCapability)
        }

    @Test
    fun appSideTimeoutFallbackFallsThroughToNextMirrorWhenKtorTimeoutsUnsupported() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = false

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(tor: Boolean): HttpClient =
                                if (tor) createTor() else create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        val path = request.url.encodedPath
                                        requests += path
                                        when (path) {
                                            "/static-voting-config.json" -> {
                                                respond(
                                                    content =
                                                        staticConfigJsonV2(
                                                            listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL),
                                                        ),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/first-dynamic-voting-config.json" -> {
                                                awaitCancellation()
                                            }

                                            else -> {
                                                respond(
                                                    content = validDynamicServiceConfigJson(),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }
                                        }
                                    },
                                ) { expectSuccess = true }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                    configRequestTimeoutMillis = TEST_TIMEOUT_MILLIS,
                )

            val serviceConfig = provider.fetchServiceConfig(decision = CUSTOM_TOR)

            assertEquals(
                listOf(
                    "/static-voting-config.json",
                    "/first-dynamic-voting-config.json",
                    "/second-dynamic-voting-config.json",
                ),
                requests,
            )
            assertEquals(1, serviceConfig.voteServers.size)
        }

    // endregion

    // region vote-server failover timeout (MOB-1811)

    @Test
    fun fetchAllRoundsAppSideTimeoutFallbackFiresWhenVoteServerHangsUnderTor() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = false

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(tor: Boolean): HttpClient =
                                if (tor) createTor() else create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        val path = request.url.encodedPath
                                        requests += path
                                        when (path) {
                                            "/static-voting-config.json" -> {
                                                respond(
                                                    content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/dynamic-voting-config.json" -> {
                                                respond(
                                                    content = validDynamicServiceConfigJson(),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            // Simulates a vote server dropping (not refusing) the
                                            // Tor connection - the exact MOB-1811 symptom.
                                            ROUNDS_TEST_PATH -> {
                                                awaitCancellation()
                                            }

                                            else -> {
                                                respond(content = "not found", status = HttpStatusCode.NotFound)
                                            }
                                        }
                                    },
                                ) { expectSuccess = true }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                    voteServerFailoverTimeoutMillis = TEST_TIMEOUT_MILLIS,
                )

            assertFailsWith<VotingServerFailoverException> {
                provider.fetchAllRounds(decision = CUSTOM_TOR)
            }

            assertEquals(
                listOf("/static-voting-config.json", "/dynamic-voting-config.json", ROUNDS_TEST_PATH),
                requests,
            )
        }

    // endregion

    // region memoization (MOB-1809)

    @Test
    fun resolvedConfigIsMemoizedAcrossConsecutiveCalls() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val provider = multiEndpointProvider(requestLog = requestLog)

            provider.fetchAllRounds(decision = CUSTOM_DIRECT)
            provider.fetchAllRounds(decision = CUSTOM_DIRECT)

            assertEquals(1, requestLog.count { path -> path == "/static-voting-config.json" })
            assertEquals(1, requestLog.count { path -> path == "/dynamic-voting-config.json" })
            assertEquals(2, requestLog.count { path -> path == ROUNDS_TEST_PATH })
        }

    @Test
    fun changingSelectedSourceRefetchesConfig() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val sourceUrlA = "https://example.com/static-config-a.json"
            val sourceUrlB = "https://example.com/static-config-b.json"
            val repository = SwitchableVotingChainConfigRepository(initialPinnedSource = sourceUrlA)
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = true

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        val path = request.url.encodedPath
                                        requestLog += path
                                        when (path) {
                                            "/static-config-a.json", "/static-config-b.json" -> {
                                                respond(
                                                    content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/dynamic-voting-config.json" -> {
                                                respond(
                                                    content = validDynamicServiceConfigJson(),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            ROUNDS_TEST_PATH -> {
                                                respond(
                                                    content = """{"rounds": []}""",
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            else -> {
                                                respond(content = "not found", status = HttpStatusCode.NotFound)
                                            }
                                        }
                                    },
                                ) {
                                    expectSuccess = true
                                    install(ContentNegotiation) { json() }
                                }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = repository,
                    votingCryptoClient = unusedVotingCryptoClient(),
                )

            provider.fetchAllRounds(decision = CUSTOM_DIRECT)
            repository.switchTo(sourceUrlB)
            provider.fetchAllRounds(decision = CUSTOM_DIRECT)

            assertEquals(1, requestLog.count { path -> path == "/static-config-a.json" })
            assertEquals(1, requestLog.count { path -> path == "/static-config-b.json" })
        }

    @Test
    fun invalidateConfigCacheForcesRefetch() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val provider = multiEndpointProvider(requestLog = requestLog)

            provider.fetchAllRounds(decision = CUSTOM_DIRECT)
            provider.invalidateConfigCache()
            provider.fetchAllRounds(decision = CUSTOM_DIRECT)

            assertEquals(2, requestLog.count { path -> path == "/static-voting-config.json" })
            assertEquals(2, requestLog.count { path -> path == "/dynamic-voting-config.json" })
        }

    // endregion

    // region TTL cache for fetchServiceConfig (MOB-1808)

    @Test
    fun fetchServiceConfigReusesCacheWithinTtlWindow() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val provider = multiEndpointProvider(requestLog = requestLog)

            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)
            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)

            // fetchServiceConfig() goes through getResolvedConfigWithTtl(), a DIFFERENT cache path
            // than fetchAllRounds()'s getResolvedConfig() (see resolvedConfigIsMemoizedAcrossConsecutiveCalls
            // above) - this pins the TTL-gated path specifically, not just memoization in general.
            assertEquals(1, requestLog.count { path -> path == "/static-voting-config.json" })
            assertEquals(1, requestLog.count { path -> path == "/dynamic-voting-config.json" })
        }

    @Test
    fun invalidateConfigCacheForcesRefetchOnTheTtlPath() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val provider = multiEndpointProvider(requestLog = requestLog)

            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)
            provider.invalidateConfigCache()
            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)

            // NOTE (Milan's #2483 round-2 review): this pins invalidation-forces-refetch on the
            // TTL path specifically (mirrors invalidateConfigCacheForcesRefetch above, which only
            // covers the non-TTL fetchAllRounds() path) - it does NOT pin the TTL-expiry
            // fallthrough itself. invalidateConfigCache() nulls cachedResolvedConfigFetchedAtMs
            // outright, which exercises resolveConfigCached()'s cache-MISS branch (fetchedAtMs ==
            // null); the old buggy code (getResolvedConfigWithTtl() falling through to
            // getResolvedConfig(forceRefresh = false) with no age check at all) would also have
            // passed this test, since a null fetchedAtMs was never the bug - a genuinely EXPIRED
            // but still-present entry was. See
            // fetchServiceConfigRefetchesAfterTtlExpiry below for the real expiry pin.
            assertEquals(2, requestLog.count { path -> path == "/static-voting-config.json" })
            assertEquals(2, requestLog.count { path -> path == "/dynamic-voting-config.json" })
        }

    @Test
    fun fetchServiceConfigRefetchesAfterTtlExpiry() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val provider =
                multiEndpointProvider(
                    requestLog = requestLog,
                    configCacheTtlMillis = SHORT_CONFIG_CACHE_TTL_MILLIS,
                )

            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)
            delay(SHORT_CONFIG_CACHE_TTL_MILLIS * 2)
            provider.fetchServiceConfig(decision = CUSTOM_DIRECT)

            // The real regression pin for the fallthrough bug an earlier review caught: unlike
            // invalidateConfigCacheForcesRefetchOnTheTtlPath above (cache-miss branch), this
            // entry is genuinely still PRESENT and source-matching when the TTL check runs - only
            // its age has crossed configCacheTtlMillis. getResolvedConfigWithTtl()'s old
            // fallthrough into getResolvedConfig(forceRefresh = false) had no age awareness at
            // all, so this exact scenario used to silently re-serve the stale entry forever.
            assertEquals(2, requestLog.count { path -> path == "/static-voting-config.json" })
            assertEquals(2, requestLog.count { path -> path == "/dynamic-voting-config.json" })
        }

    @Test
    fun concurrentColdCacheFetchServiceConfigCallsCoalesceOntoOneFetch() =
        runBlocking {
            val requestLog = mutableListOf<String>()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = true

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        val path = request.url.encodedPath
                                        requestLog += path
                                        when (path) {
                                            "/static-voting-config.json" -> {
                                                // Held open deliberately so a second concurrent caller's
                                                // configMutex.withLock has a real window to contend on
                                                // while the first is still resolving, not just win a
                                                // same-tick race.
                                                delay(CANCELLATION_DELAY_MILLIS)
                                                respond(
                                                    content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/dynamic-voting-config.json" -> {
                                                respond(
                                                    content = validDynamicServiceConfigJson(),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            else -> {
                                                respond(content = "not found", status = HttpStatusCode.NotFound)
                                            }
                                        }
                                    },
                                ) {
                                    expectSuccess = true
                                    install(ContentNegotiation) { json() }
                                }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                )

            // Regression pin for the race an earlier review caught alongside the TTL fallthrough bug:
            // two callers racing a cold/expired cache used to each independently decide "expired" and
            // both fire a real fetch. resolveConfigCached()'s single configMutex.withLock section now
            // makes the second, slightly-later caller wait for and reuse the first's in-flight fetch.
            val first = async { provider.fetchServiceConfig(decision = CUSTOM_DIRECT) }
            val second = async { provider.fetchServiceConfig(decision = CUSTOM_DIRECT) }
            first.await()
            second.await()

            assertEquals(1, requestLog.count { path -> path == "/static-voting-config.json" })
            assertEquals(1, requestLog.count { path -> path == "/dynamic-voting-config.json" })
        }

    // endregion

    // region delegateShares concurrency cap and per-share timeout handling (MOB-1808)

    @Test
    fun postShareTimeoutIsTreatedAsAFailureNotACancellation() =
        runBlocking {
            val share = makeDelegateSharePayload()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = false

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(tor: Boolean): HttpClient =
                                if (tor) createTor() else create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        when (request.url.encodedPath) {
                                            "/static-voting-config.json" -> {
                                                respond(
                                                    content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/dynamic-voting-config.json" -> {
                                                respond(
                                                    content = validDynamicServiceConfigJson(),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            // Never responds - with supportsKtorTimeouts = false, Ktor's
                                            // own HttpTimeout plugin is a no-op under Tor, so only
                                            // postShare()'s app-side withTorRequestTimeoutFallback bounds
                                            // this attempt (MOB-1808).
                                            "/shielded-vote/v1/shares" -> {
                                                awaitCancellation()
                                            }

                                            else -> {
                                                respond(content = "not found", status = HttpStatusCode.NotFound)
                                            }
                                        }
                                    },
                                ) {
                                    expectSuccess = true
                                    install(ContentNegotiation) { json() }
                                }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                    shareRequestTimeoutMillis = TEST_TIMEOUT_MILLIS,
                )

            // Regression pin for the postShare() catch-clause restructuring (MOB-1808 review): the
            // TimeoutCancellationException that withTorRequestTimeoutFallback's own deadline throws
            // must be treated as an ordinary failed attempt, not misidentified as delegateShares'
            // sibling-cancellation signal (see postShare's own TRAP doc). If it were, this would
            // surface as a raw CancellationException instead of the normal "no server accepted"
            // failure below.
            val exception =
                assertFailsWith<IllegalStateException> {
                    provider.delegateShares(listOf(share), decision = CUSTOM_TOR)
                }
            assertFalse(exception is CancellationException)
            assertEquals("No voting server accepted share ${share.encShare.shareIndex}", exception.message)
        }

    @Test
    fun delegateSharesBoundsConcurrentShareDelegationToTheSemaphoreCap() =
        runBlocking {
            val inFlight = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val capReached = CompletableDeferred<Unit>()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = true

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        when (request.url.encodedPath) {
                                            "/static-voting-config.json" -> {
                                                respond(
                                                    content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/dynamic-voting-config.json" -> {
                                                respond(
                                                    content = validDynamicServiceConfigJson(),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            "/shielded-vote/v1/shares" -> {
                                                val current = inFlight.incrementAndGet()
                                                maxObserved.updateAndGet { max -> maxOf(max, current) }
                                                // Hold every request until SEMAPHORE_CAP are in flight at once, so
                                                // a slow runner cannot finish one before the last one starts (a
                                                // fixed delay flaked on CI). Bounded, so a cap below 16 fails the
                                                // assertion below instead of hanging.
                                                if (current >= SEMAPHORE_CAP) capReached.complete(Unit)
                                                withTimeoutOrNull(SEMAPHORE_TEST_GATE_MILLIS) { capReached.await() }
                                                inFlight.decrementAndGet()
                                                respond(
                                                    content = "{}",
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            else -> {
                                                respond(content = "not found", status = HttpStatusCode.NotFound)
                                            }
                                        }
                                    },
                                ) {
                                    expectSuccess = true
                                    install(ContentNegotiation) { json() }
                                }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                )
            val shares = (0 until SHARE_COUNT_ABOVE_SEMAPHORE_CAP).map { index -> makeDelegateSharePayload(index) }

            val results = provider.delegateShares(shares, decision = CUSTOM_DIRECT)

            assertEquals(SHARE_COUNT_ABOVE_SEMAPHORE_CAP, results.size)
            // MAX_CONCURRENT_SHARE_DELEGATIONS in VotingApiProvider.kt is 16 and file-private, so its
            // value is duplicated here as SEMAPHORE_CAP - regression pin for the fan-out concurrency
            // cap (MOB-1808): with more shares than the cap all held open concurrently by the delay
            // above, the semaphore must let exactly SEMAPHORE_CAP requests in flight at once, never more.
            assertEquals(SEMAPHORE_CAP, maxObserved.get())
        }

    // endregion

    private fun makeDelegateSharePayload(shareIndex: Int = 0): SharePayload =
        SharePayload(
            sharesHash = ByteArray(32) { 1 },
            proposalId = 3,
            voteDecision = 1,
            encShare = EncryptedShare(c1 = ByteArray(32) { 2 }, c2 = ByteArray(32) { 3 }, shareIndex = shareIndex),
            treePosition = 42L,
            voteRoundId = "01".repeat(32),
        )

    // region cancellation propagation with the app-side timeout fallback in place (MOB-1809)

    @Test
    fun genuineCancellationDuringAppSideTimeoutWrappedAttemptIsNotConvertedToRetryableFailure() =
        runBlocking {
            val requests = mutableListOf<String>()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider =
                        object : HttpClientProvider {
                            override suspend fun isTorEnabled(): Boolean = false

                            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                            override suspend fun supportsKtorTimeouts(): Boolean = false

                            override suspend fun createTor(): HttpClient = create()

                            override suspend fun create(tor: Boolean): HttpClient =
                                if (tor) createTor() else create()

                            override suspend fun create(): HttpClient =
                                HttpClient(
                                    MockEngine { request ->
                                        val path = request.url.encodedPath
                                        requests += path
                                        when (path) {
                                            "/static-voting-config.json" -> {
                                                respond(
                                                    content =
                                                        staticConfigJsonV2(
                                                            listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL),
                                                        ),
                                                    status = HttpStatusCode.OK,
                                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                                )
                                            }

                                            else -> {
                                                awaitCancellation()
                                            }
                                        }
                                    },
                                ) { expectSuccess = true }
                        },
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                    configRequestTimeoutMillis = TEST_TIMEOUT_MILLIS,
                )

            val job = async { provider.fetchServiceConfig(decision = CUSTOM_DIRECT) }
            delay(CANCELLATION_DELAY_MILLIS)
            job.cancel()

            assertFailsWith<CancellationException> { job.await() }
            assertEquals(listOf("/static-voting-config.json", "/first-dynamic-voting-config.json"), requests)
        }

    // endregion

    // region client-level retry exclusion for config requests (MOB-1809)

    @Test
    fun configLegRequestIsNotRetriedByTheClientLevelRetryPolicy() =
        runBlocking {
            val attemptsByPath = mutableMapOf<String, Int>()
            val provider =
                KtorVotingApiProvider(
                    // The client-level retry exclusion (excludeFromClientRetry) only applies to the
                    // Ktor leg, which is used when Tor is on; with Tor off the config request goes
                    // through the OHTTP client instead.
                    httpClientProvider = retryingHttpClientProvider(attemptsByPath, torEnabled = true),
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                )

            assertFailsWith<VotingConfigException> { provider.fetchServiceConfig(decision = CUSTOM_TOR) }

            assertEquals(1, attemptsByPath["/dynamic-voting-config.json"])
        }

    @Test
    fun voteServerRequestIsRetriedByTheClientLevelRetryPolicy() =
        runBlocking {
            val attemptsByPath = mutableMapOf<String, Int>()
            val provider =
                KtorVotingApiProvider(
                    httpClientProvider = retryingHttpClientProvider(attemptsByPath, dynamicConfigFails = false),
                    configurationRepository = TestConfigurationRepository(),
                    votingChainConfigRepository = TestVotingChainConfigRepository(),
                    votingCryptoClient = unusedVotingCryptoClient(),
                )

            runCatching { provider.fetchAllRounds(decision = CUSTOM_DIRECT) }

            assertEquals(CLIENT_RETRY_MAX_RETRIES + 1, attemptsByPath[ROUNDS_TEST_PATH])
        }

    // endregion

    private fun retryingHttpClientProvider(
        attemptsByPath: MutableMap<String, Int>,
        dynamicConfigFails: Boolean = true,
        torEnabled: Boolean = false,
    ): HttpClientProvider =
        object : HttpClientProvider {
            override suspend fun isTorEnabled(): Boolean = torEnabled

            override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

            override suspend fun supportsKtorTimeouts(): Boolean = true

            override suspend fun createTor(): HttpClient = create()

            override suspend fun create(tor: Boolean): HttpClient =
                if (tor) createTor() else create()

            override suspend fun create(): HttpClient =
                HttpClient(
                    MockEngine { request ->
                        val path = request.url.encodedPath
                        attemptsByPath[path] = (attemptsByPath[path] ?: 0) + 1
                        when (path) {
                            "/static-voting-config.json" -> {
                                respond(
                                    content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }

                            "/dynamic-voting-config.json" -> {
                                if (dynamicConfigFails) {
                                    respond(content = "boom", status = HttpStatusCode.InternalServerError)
                                } else {
                                    respond(
                                        content = validDynamicServiceConfigJson(),
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )
                                }
                            }

                            ROUNDS_TEST_PATH -> {
                                respond(content = "boom", status = HttpStatusCode.InternalServerError)
                            }

                            else -> {
                                respond(content = "not found", status = HttpStatusCode.NotFound)
                            }
                        }
                    },
                ) {
                    expectSuccess = true
                    install(ContentNegotiation) { json() }
                    install(HttpRequestRetry) {
                        maxRetries = CLIENT_RETRY_MAX_RETRIES
                        retryIf { _, response -> response.status.value in 500..599 }
                        retryOnExceptionIf { _, _ -> true }
                        constantDelay(millis = 1, randomizationMs = 0)
                    }
                }
        }

    private fun multiEndpointProvider(
        requestLog: MutableList<String> = mutableListOf(),
        timeoutCapabilities: MutableList<Pair<String, Long?>> = mutableListOf(),
        configCacheTtlMillis: Long? = null,
        torEnabled: Boolean = false,
    ): KtorVotingApiProvider =
        KtorVotingApiProvider(
            httpClientProvider =
                object : HttpClientProvider {
                    override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                    override suspend fun isTorEnabled(): Boolean = torEnabled

                    override suspend fun supportsKtorTimeouts(): Boolean = true

                    override suspend fun createTor(): HttpClient = create()

                    override suspend fun create(tor: Boolean): HttpClient =
                        if (tor) createTor() else create()

                    override suspend fun create(): HttpClient =
                        HttpClient(
                            MockEngine { request ->
                                val path = request.url.encodedPath
                                requestLog += path
                                val requestTimeoutMillis =
                                    request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                                timeoutCapabilities += path to requestTimeoutMillis
                                when (path) {
                                    "/static-voting-config.json" -> {
                                        respond(
                                            content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    }

                                    "/dynamic-voting-config.json" -> {
                                        respond(
                                            content = validDynamicServiceConfigJson(),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    }

                                    ROUNDS_TEST_PATH -> {
                                        respond(
                                            content = """{"rounds": []}""",
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    }

                                    else -> {
                                        respond(content = "not found", status = HttpStatusCode.NotFound)
                                    }
                                }
                            },
                        ) {
                            expectSuccess = true
                            install(ContentNegotiation) { json() }
                        }
                },
            configurationRepository = TestConfigurationRepository(),
            votingChainConfigRepository = TestVotingChainConfigRepository(),
            votingCryptoClient = unusedVotingCryptoClient(),
            configCacheTtlMillis = configCacheTtlMillis ?: DEFAULT_TEST_CONFIG_CACHE_TTL_MILLIS,
        )

    /**
     * Tor off (so the config legs take the OHTTP path) with Ktor timeouts available, where the request on
     * [hangingPath] never answers: only the app-side bound in front of the OHTTP call can end it.
     */
    private fun hangingConfigProvider(
        hangingPath: String,
        requests: MutableList<String> = mutableListOf(),
    ): KtorVotingApiProvider =
        KtorVotingApiProvider(
            httpClientProvider =
                object : HttpClientProvider {
                    override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

                    override suspend fun isTorEnabled(): Boolean = false

                    override suspend fun supportsKtorTimeouts(): Boolean = true

                    override suspend fun createTor(): HttpClient = create()

                    override suspend fun create(tor: Boolean): HttpClient =
                        if (tor) createTor() else create()

                    override suspend fun create(): HttpClient =
                        HttpClient(
                            MockEngine { request ->
                                val path = request.url.encodedPath
                                requests += path
                                when (path) {
                                    hangingPath -> {
                                        awaitCancellation()
                                    }

                                    "/static-voting-config.json" -> {
                                        respond(
                                            content =
                                                staticConfigJsonV2(
                                                    listOf(FIRST_DYNAMIC_CONFIG_URL, SECOND_DYNAMIC_CONFIG_URL),
                                                ),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    }

                                    else -> {
                                        respond(
                                            content = validDynamicServiceConfigJson(),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    }
                                }
                            },
                        ) { expectSuccess = true }
                },
            // The default (non-custom) config: only that one goes through OHTTP with Tor off.
            configurationRepository = TestConfigurationRepository(votingConfigUrl = STATIC_CONFIG_URL),
            votingChainConfigRepository = TestVotingChainConfigRepository(VotingChainConfigState()),
            votingCryptoClient = unusedVotingCryptoClient(),
            configRequestTimeoutMillis = TEST_TIMEOUT_MILLIS,
        )

    private class SwitchableVotingChainConfigRepository(
        initialPinnedSource: String,
    ) : VotingChainConfigRepository {
        private var currentState =
            VotingChainConfigState(
                selected = VotingChainConfigSelection.Custom(SWITCHABLE_CHAIN_ID),
                customChains = listOf(switchableChain(initialPinnedSource)),
            )

        override val state: StateFlow<VotingChainConfigState>
            get() = MutableStateFlow(currentState)

        fun switchTo(pinnedSource: String) {
            currentState = currentState.copy(customChains = listOf(switchableChain(pinnedSource)))
        }

        override suspend fun get(): VotingChainConfigState = currentState

        override suspend fun selectDefault() = Unit

        override suspend fun selectCustom(id: String) = Unit

        override suspend fun addCustom(
            name: String,
            pinnedSource: String,
        ): VotingCustomChainConfig = error("unused")

        override suspend fun updateCustom(
            id: String,
            name: String,
            pinnedSource: String,
        ) = Unit

        override suspend fun deleteCustom(id: String) = Unit

        private fun switchableChain(pinnedSource: String) =
            VotingCustomChainConfig(id = SWITCHABLE_CHAIN_ID, name = "Switchable", pinnedSource = pinnedSource)

        private companion object {
            const val SWITCHABLE_CHAIN_ID = "switchable-chain"
        }
    }

    private fun newProvider(
        requests: MutableList<String>,
        supportsKtorTimeouts: Boolean = true,
        requestTimeoutCapabilities: MutableList<Boolean> = mutableListOf(),
        responseOverrides: Map<String, TestResponse> = emptyMap(),
    ) = KtorVotingApiProvider(
        httpClientProvider =
            TestHttpClientProvider(
                requests,
                supportsKtorTimeouts,
                requestTimeoutCapabilities,
                responseOverrides,
            ),
        configurationRepository = TestConfigurationRepository(),
        votingChainConfigRepository = TestVotingChainConfigRepository(),
        votingCryptoClient = unusedVotingCryptoClient(),
    )

    private fun newProviderWithResponses(
        requests: MutableList<String>,
        responses: Map<String, MockResponse>,
    ) = KtorVotingApiProvider(
        httpClientProvider = ResponseMapHttpClientProvider(requests, responses),
        configurationRepository = TestConfigurationRepository(),
        votingChainConfigRepository = TestVotingChainConfigRepository(),
        votingCryptoClient = unusedVotingCryptoClient(),
    )

    private class TestHttpClientProvider(
        private val requests: MutableList<String>,
        private val supportsKtorTimeouts: Boolean,
        private val requestTimeoutCapabilities: MutableList<Boolean>,
        private val responseOverrides: Map<String, TestResponse>,
    ) : HttpClientProvider {
        override suspend fun isTorEnabled(): Boolean = false

        override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

        override suspend fun supportsKtorTimeouts(): Boolean = supportsKtorTimeouts

        override suspend fun createTor(): HttpClient = create()

        override suspend fun create(): HttpClient =
            HttpClient(
                MockEngine { request ->
                    val requestTarget =
                        request.url.encodedPath +
                            request.url.encodedQuery
                                .takeIf(String::isNotEmpty)
                                ?.let { query -> "?$query" }
                                .orEmpty()
                    requests += requestTarget
                    requestTimeoutCapabilities += (request.getCapabilityOrNull(HttpTimeoutCapability) != null)
                    responseOverrides[requestTarget]?.let { response ->
                        return@MockEngine respond(
                            content = response.content,
                            status = response.status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    when (request.url.encodedPath) {
                        "/static-voting-config.json" -> {
                            respond(
                                content = staticConfigJson(dynamicConfigUrl = DYNAMIC_CONFIG_URL),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/dynamic-voting-config.json" -> {
                            respond(
                                content = "temporary dynamic config failure",
                                status = HttpStatusCode.InternalServerError,
                            )
                        }

                        "/shielded-vote/v1/share-status/round-id/nullifier" -> {
                            respond(
                                content = """{"status":"confirmed"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            respond(content = "not found", status = HttpStatusCode.NotFound)
                        }
                    }
                },
            ) {
                expectSuccess = true
                // Mirrors the production HttpClientProvider's content negotiation.
                install(ContentNegotiation) { json() }
            }
    }

    private class ResponseMapHttpClientProvider(
        private val requests: MutableList<String>,
        private val responses: Map<String, MockResponse>,
    ) : HttpClientProvider {
        override suspend fun isTorEnabled(): Boolean = false

        override suspend fun createOhttp(): OhttpHttpClient = passthroughOhttpClient { create() }

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
                                },
                        )
                    }
                },
            ) {
                expectSuccess = true
            }
    }

    private data class TestResponse(
        val content: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
    )

    /** [votingConfigUrl] set = the remote-config (default, non-custom) voting config source. */
    private class TestConfigurationRepository(
        votingConfigUrl: String? = null,
    ) : ConfigurationRepository {
        override val configurationFlow: StateFlow<Configuration?> =
            MutableStateFlow(votingConfigUrl?.let(::votingConfigUrlConfiguration))
        override val isFlexaAvailable: StateFlow<Boolean?> = MutableStateFlow(false)

        override suspend fun isFlexaAvailable(): Boolean = false
    }

    private class TestVotingChainConfigRepository(
        private val chainConfigState: VotingChainConfigState = TEST_CHAIN_CONFIG_STATE,
    ) : VotingChainConfigRepository {
        override val state: StateFlow<VotingChainConfigState> = MutableStateFlow(chainConfigState)

        override suspend fun get(): VotingChainConfigState = chainConfigState

        override suspend fun selectDefault() = Unit

        override suspend fun selectCustom(id: String) = Unit

        override suspend fun addCustom(
            name: String,
            pinnedSource: String,
        ): VotingCustomChainConfig = error("unused")

        override suspend fun updateCustom(
            id: String,
            name: String,
            pinnedSource: String,
        ) = Unit

        override suspend fun deleteCustom(id: String) = Unit
    }

    private companion object {
        const val STATIC_CONFIG_URL = "https://example.com/static-voting-config.json"
        const val DYNAMIC_CONFIG_URL = "https://example.com/dynamic-voting-config.json"
        const val FIRST_DYNAMIC_CONFIG_URL = "https://example.com/first-dynamic-voting-config.json"
        const val SECOND_DYNAMIC_CONFIG_URL = "https://example.com/second-dynamic-voting-config.json"
        const val ROUNDS_TEST_PATH = "/shielded-vote/v1/rounds"
        const val TEST_TIMEOUT_MILLIS = 100L
        const val CANCELLATION_DELAY_MILLIS = 50L

        // KtorVotingApiProvider's own default (CONFIG_CACHE_TTL_MS, VotingApiProvider.kt) is
        // file-private there too, same reasoning as SEMAPHORE_CAP below — this file's real
        // production-equivalent default for tests that don't care about TTL expiry timing.
        const val DEFAULT_TEST_CONFIG_CACHE_TTL_MILLIS = 600_000L
        const val SHORT_CONFIG_CACHE_TTL_MILLIS = 20L
        const val CLIENT_RETRY_MAX_RETRIES = 4

        // MAX_CONCURRENT_SHARE_DELEGATIONS in VotingApiProvider.kt is 16 and file-private (a
        // top-level `private const val` there is file-scoped, not visible here), so its value is
        // duplicated as SEMAPHORE_CAP for delegateSharesBoundsConcurrentShareDelegationToTheSemaphoreCap.
        const val SEMAPHORE_CAP = 16
        const val SHARE_COUNT_ABOVE_SEMAPHORE_CAP = 20
        const val SEMAPHORE_TEST_GATE_MILLIS = 5_000L
        val TEST_CHAIN_CONFIG_STATE =
            VotingChainConfigState(
                selected = VotingChainConfigSelection.Custom("test-chain"),
                customChains =
                    listOf(
                        VotingCustomChainConfig(
                            id = "test-chain",
                            name = "Test Chain",
                            pinnedSource = STATIC_CONFIG_URL,
                        ),
                    ),
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

private const val DYNAMIC_CONFIG_URL = "https://example.com/first-dynamic-voting-config.json"

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

private fun dynamicVotingConfigJson(): String =
    """
    {
      "config_version": 1,
      "vote_servers": [{"url":"https://vote.example.com","label":"vote"}],
      "pir_endpoints": [{"url":"https://pir.example.com","label":"pir"}],
      "pir_layout": {"pir_depth":1,"tier0_layers":1,"tier1_layers":1,"poly_len":4096},
      "supported_versions": {
        "pir":["v0"],
        "vote_protocol":"v0",
        "tally":"v0",
        "vote_server":"v1"
      },
      "rounds": {}
    }
    """.trimIndent()

private data class MockResponse(
    val content: String,
    val status: HttpStatusCode,
)

private fun unusedVotingCryptoClient(): VotingCryptoClient =
    Proxy.newProxyInstance(
        VotingCryptoClient::class.java.classLoader,
        arrayOf(VotingCryptoClient::class.java),
    ) { _, method, _ ->
        error("Unexpected VotingCryptoClient call: ${method.name}")
    } as VotingCryptoClient

/** ConfigKey is a value class, which MockK cannot match with any(), so a tiny fake. */
private fun votingConfigUrlConfiguration(url: String): Configuration =
    object : Configuration {
        override val updatedAt: Instant? = null

        override fun hasKey(key: ConfigKey): Boolean = key == ConfigurationEntries.VOTING_CONFIG_URL.key

        override fun getBoolean(
            key: ConfigKey,
            defaultValue: Boolean
        ): Boolean = defaultValue

        override fun getInt(
            key: ConfigKey,
            defaultValue: Int
        ): Int = defaultValue

        override fun getString(
            key: ConfigKey,
            defaultValue: String
        ): String = if (key == ConfigurationEntries.VOTING_CONFIG_URL.key) url else defaultValue
    }

// Routing the provider's fakes stand for: the test chain is a custom (non-bundled) source.
private val CUSTOM_DIRECT = VotingRouteDecision(isTor = false, isCustomServer = true)
private val CUSTOM_TOR = VotingRouteDecision(isTor = true, isCustomServer = true)
private val DEFAULT_OHTTP = VotingRouteDecision(isTor = false, isCustomServer = false)
