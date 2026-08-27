package co.electriccoin.zcash.ui.common.provider

import android.util.Log
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.ChainActiveRoundResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainCommitmentTreeLatestResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainCommitmentTreeLeavesResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundsResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainTallyResultsResponse
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.RoundAuthStatus
import co.electriccoin.zcash.ui.common.model.voting.RoundAuthenticator
import co.electriccoin.zcash.ui.common.model.voting.ShareConfirmationResult
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfigHashMismatchException
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxEvent
import co.electriccoin.zcash.ui.common.model.voting.TxEventAttribute
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingConfigException
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundAuthenticationException
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.ZodlEndorsedRoundsResponse
import co.electriccoin.zcash.ui.common.model.voting.retainingRoundsWithValidSignatures
import co.electriccoin.zcash.ui.common.model.voting.toBase64String
import co.electriccoin.zcash.ui.common.model.voting.toTallyResults
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import kotlin.math.max

data class RoundsListResult(
    val rounds: List<VotingRound>,
    val sessionsByRoundId: Map<String, VotingSession>
) {
    companion object {
        val EMPTY = RoundsListResult(rounds = emptyList(), sessionsByRoundId = emptyMap())
    }
}

interface VotingApiProvider {
    suspend fun validateConfigSource(source: PinnedConfigSource)

    /**
     * Drops any in-memory resolved-config cache so the next caller (typically
     * [fetchServiceConfig]) re-fetches both the static and dynamic configs from
     * the network. Mirrors iOS's `prepareForServiceConfigRefresh` (see
     * `VotingStore+Session.swift:644-647`) which nulls out `serviceConfig` on
     * every voting flow entry.
     */
    suspend fun invalidateConfigCache()

    suspend fun fetchServiceConfig(): VotingServiceConfig

    suspend fun fetchActiveVotingSession(): VotingSession?

    /**
     * Fetches `/rounds` and returns both the user-facing [VotingRound] cards and
     * the underlying authenticated [VotingSession]s keyed by lower-cased round id.
     * The session map lets downstream voting flows resolve the explicit round id
     * from navigation without consulting `/rounds/active` — the iOS pattern of
     * "rounds list is authoritative" (`VotingStore+Session.swift:69-77`, `:588-640`).
     */
    suspend fun fetchAllRounds(): RoundsListResult

    suspend fun fetchZodlEndorsedRoundIds(): Set<String>

    suspend fun submitDelegation(registration: DelegationRegistration): TxResult

    suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ): TxResult

    suspend fun fetchTallyResults(roundIdHex: String): TallyResults

    suspend fun delegateShares(shares: List<SharePayload>): List<DelegatedShareInfo>

    suspend fun fetchShareStatus(
        helperBaseUrl: String,
        roundIdHex: String,
        nullifierHex: String
    ): ShareConfirmationResult

    suspend fun resubmitShare(
        payload: SharePayload,
        candidateUrls: List<String>,
        excludeUrls: List<String>
    ): List<String>

    suspend fun fetchTxConfirmation(txHash: String): TxConfirmation?

    suspend fun fetchCommitmentTreeLatest(roundIdHex: String): CommitmentTreeLatest

    suspend fun fetchCommitmentTreeLeafPage(
        roundIdHex: String,
        fromHeight: Long,
        toHeight: Long
    ): CommitmentTreeLeafPage
}

class KtorVotingApiProvider(
    private val httpClientProvider: HttpClientProvider,
    private val configurationRepository: ConfigurationRepository,
    private val votingChainConfigRepository: VotingChainConfigRepository,
    private val votingCryptoClient: VotingCryptoClient,
    /**
     * Per-attempt timeout for both config-fetch legs (MOB-1809), defaulting to
     * [StaticVotingConfig.CONFIG_REQUEST_TIMEOUT_MS]. Overridable only so tests can inject a
     * short value when exercising the app-side [withConfigRequestTimeoutFallback] path — the
     * production default never changes, and DI (see `featureVotingModule`) never passes an
     * override, so every real caller gets the same 15s bound both the Ktor-level and app-side
     * timeout mechanisms share.
     */
    private val configRequestTimeoutMillis: Long = StaticVotingConfig.CONFIG_REQUEST_TIMEOUT_MS,
) : VotingApiProvider {
    private var cachedResolvedConfig: ResolvedVotingConfig? = null
    private val configMutex = Mutex()
    private val serverHealthTracker = VotingServerHealthTracker()

    override suspend fun validateConfigSource(source: PinnedConfigSource) {
        fetchStaticConfig(listOf(source))
    }

    override suspend fun invalidateConfigCache() {
        configMutex.withLock {
            cachedResolvedConfig = null
        }
    }

    override suspend fun fetchServiceConfig(): VotingServiceConfig =
        getResolvedConfig(forceRefresh = true).serviceConfig

    override suspend fun fetchActiveVotingSession(): VotingSession? =
        try {
            executeWithVoteServerFailover(ACTIVE_ROUNDS_PATH) { baseUrl ->
                val response = get("$baseUrl$ACTIVE_ROUNDS_PATH").body<ChainActiveRoundResponse>()
                response.round?.toVotingSession()?.let { session ->
                    authenticateVotingSession(session)
                }
            }
        } catch (exception: Exception) {
            if (exception.isNoActiveRoundFailure()) {
                null
            } else {
                throw exception
            }
        }

    override suspend fun fetchAllRounds(): RoundsListResult =
        executeWithVoteServerFailover(ROUNDS_PATH) { baseUrl ->
            val response =
                get("$baseUrl$ROUNDS_PATH") {
                    noCache()
                }.body<ChainRoundsResponse>()
            val dtos = response.rounds.orEmpty()
            val rounds = mutableListOf<VotingRound>()
            val sessions = mutableMapOf<String, VotingSession>()
            for (dto in dtos) {
                val session = authenticateVotingSessionOrNull(dto.toVotingSession()) ?: continue
                val round = dto.toVotingRound()
                rounds += round
                sessions[round.id.lowercase()] = session
            }
            RoundsListResult(rounds = rounds, sessionsByRoundId = sessions)
        }

    override suspend fun fetchZodlEndorsedRoundIds(): Set<String> {
        val failureStatuses = mutableListOf<HttpStatusCode?>()
        return try {
            executeWithVoteServerFailover(
                path = ENDORSED_ROUNDS_PATH,
                shouldTryNext = { throwable ->
                    failureStatuses += (throwable as? ResponseException)?.response?.status
                    shouldTryNextVoteServer(throwable)
                }
            ) { baseUrl ->
                get("$baseUrl$ENDORSED_ROUNDS_PATH") {
                    noCache()
                }.body<ZodlEndorsedRoundsResponse>()
                    .roundIdsHex()
            }
        } catch (exception: VotingServerFailoverException) {
            if (shouldTreatEndorsedRoundsFailoverFailuresAsEmpty(failureStatuses)) {
                emptySet()
            } else {
                throw exception
            }
        }
    }

    override suspend fun submitDelegation(registration: DelegationRegistration): TxResult =
        executeWithVoteServerFailover(DELEGATE_VOTE_PATH) { baseUrl ->
            postTxResult(
                url = "$baseUrl$DELEGATE_VOTE_PATH",
                body = registration.toApiBody()
            )
        }

    override suspend fun submitVoteCommitment(
        bundle: VoteCommitmentBundle,
        signature: CastVoteSignature
    ): TxResult =
        executeWithVoteServerFailover(CAST_VOTE_PATH) { baseUrl ->
            postTxResult(
                url = "$baseUrl$CAST_VOTE_PATH",
                body = bundle.toApiBody(signature)
            )
        }

    override suspend fun fetchTallyResults(roundIdHex: String): TallyResults {
        val ballotDivisorZatoshi = votingCryptoClient.ballotDivisorZatoshi()
        return executeWithVoteServerFailover(tallyResultsPath(roundIdHex)) { baseUrl ->
            get("$baseUrl${tallyResultsPath(roundIdHex)}")
                .body<ChainTallyResultsResponse>()
                .toTallyResults(roundIdHex, ballotDivisorZatoshi)
        }
    }

    override suspend fun delegateShares(shares: List<SharePayload>): List<DelegatedShareInfo> =
        executeWithKtorTimeoutSupport delegateShares@{ supportsKtorTimeouts ->
            if (shares.isEmpty()) {
                return@delegateShares emptyList()
            }

            val config = getResolvedConfig().serviceConfig
            val serverUrls =
                config.voteServers
                    .map { endpoint -> endpoint.url.trimEnd('/') }
                    .distinct()

            if (serverUrls.isEmpty()) {
                error("Voting server URL is not configured")
            }

            serverHealthTracker.remember(serverUrls)

            buildList {
                for (share in shares) {
                    val body = share.toApiBody()
                    val healthyServers = serverHealthTracker.healthyServers(serverUrls)
                    val quorum = max(1, (healthyServers.size + 1) / 2)
                    val targets = healthyServers.shuffled().take(quorum)
                    val acceptedByServers =
                        postShareToTargets(targets, body, supportsKtorTimeouts).toMutableList()
                    if (acceptedByServers.isEmpty()) {
                        val fallbackTargets =
                            serverHealthTracker
                                .healthyServers(serverUrls)
                                .filterNot { serverUrl -> serverUrl in targets }
                                .shuffled()
                        for (fallbackTarget in fallbackTargets) {
                            if (postShare(fallbackTarget, body, supportsKtorTimeouts)) {
                                acceptedByServers += fallbackTarget
                                break
                            }
                        }
                    }

                    if (acceptedByServers.isEmpty()) {
                        error("No voting server accepted share ${share.encShare.shareIndex}")
                    }

                    add(
                        DelegatedShareInfo(
                            shareIndex = share.encShare.shareIndex,
                            proposalId = share.proposalId,
                            acceptedByServers = acceptedByServers
                        )
                    )
                }
            }
        }

    override suspend fun fetchShareStatus(
        helperBaseUrl: String,
        roundIdHex: String,
        nullifierHex: String
    ): ShareConfirmationResult =
        executeWithKtorTimeoutSupport { supportsKtorTimeouts ->
            val normalizedHelperBaseUrl = helperBaseUrl.trimEnd('/')
            try {
                val responseJson =
                    get(
                        "$normalizedHelperBaseUrl/shielded-vote/v1/share-status/$roundIdHex/$nullifierHex"
                    ) {
                        header("Accept", "application/json")
                        header("X-Helper-Token", "voting-helper")
                        helperRequestTimeout(supportsKtorTimeouts)
                    }.bodyAsText()
                serverHealthTracker.recordSuccess(normalizedHelperBaseUrl)
                when (JSONObject(responseJson).optString("status")) {
                    "confirmed" -> ShareConfirmationResult.CONFIRMED
                    else -> ShareConfirmationResult.PENDING
                }
            } catch (throwable: Throwable) {
                serverHealthTracker.recordFailure(normalizedHelperBaseUrl)
                throw throwable
            }
        }

    override suspend fun resubmitShare(
        payload: SharePayload,
        candidateUrls: List<String>,
        excludeUrls: List<String>
    ): List<String> =
        executeWithKtorTimeoutSupport resubmitShare@{ supportsKtorTimeouts ->
            val allServers = candidateUrls.normalizeServerUrls()
            val excludedServers = excludeUrls.normalizeServerUrls().toSet()
            if (allServers.isEmpty()) {
                return@resubmitShare emptyList()
            }
            serverHealthTracker.remember(allServers)

            val healthyServers = serverHealthTracker.healthyServers(allServers)
            val candidateServers = healthyServers.filterNot { serverUrl -> serverUrl in excludedServers }
            val body = payload.withSubmitAt(0).toApiBody()

            if (candidateServers.isEmpty()) {
                for (serverUrl in healthyServers.shuffled()) {
                    if (postShare(serverUrl, body, supportsKtorTimeouts)) {
                        return@resubmitShare listOf(serverUrl)
                    }
                }
                return@resubmitShare emptyList()
            }

            val shuffledCandidates = candidateServers.shuffled()
            val quorum = max(1, (shuffledCandidates.size + 1) / 2)
            val primaryTargets = shuffledCandidates.take(quorum)
            val acceptedByPrimary = postShareToTargets(primaryTargets, body, supportsKtorTimeouts)
            if (acceptedByPrimary.isNotEmpty()) {
                return@resubmitShare acceptedByPrimary
            }

            val fallbackServers =
                shuffledCandidates.drop(quorum) +
                    healthyServers.filter { serverUrl -> serverUrl in excludedServers }.shuffled()
            for (serverUrl in fallbackServers) {
                if (postShare(serverUrl, body, supportsKtorTimeouts)) {
                    return@resubmitShare listOf(serverUrl)
                }
            }
            emptyList()
        }

    override suspend fun fetchTxConfirmation(txHash: String): TxConfirmation? =
        configuredVoteServerUrls().let { serverUrls ->
            execute {
                for (baseUrl in serverUrls) {
                    try {
                        return@execute get("$baseUrl${txConfirmationPath(txHash)}")
                            .bodyAsText()
                            .toTxConfirmation()
                    } catch (responseException: ResponseException) {
                        when (responseException.response.status) {
                            HttpStatusCode.NotFound -> {
                                Unit
                            }

                            HttpStatusCode.UnprocessableEntity -> {
                                return@execute responseException.response.bodyAsText().toTxConfirmation()
                            }

                            else -> {
                                Unit
                            }
                        }
                    } catch (exception: Exception) {
                        if (exception is CancellationException) {
                            throw exception
                        }
                    }
                }
                null
            }
        }

    override suspend fun fetchCommitmentTreeLatest(roundIdHex: String): CommitmentTreeLatest {
        val path = commitmentTreeLatestPath(roundIdHex)
        return executeWithVoteServerFailover(path) { baseUrl ->
            val tree = get("$baseUrl$path").body<ChainCommitmentTreeLatestResponse>().tree
            require(tree.height >= 0) { "Commitment tree height must be non-negative" }
            require(tree.nextIndex >= 0) { "Commitment tree next_index must be non-negative" }
            CommitmentTreeLatest(height = tree.height, nextIndex = tree.nextIndex)
        }
    }

    override suspend fun fetchCommitmentTreeLeafPage(
        roundIdHex: String,
        fromHeight: Long,
        toHeight: Long
    ): CommitmentTreeLeafPage {
        require(fromHeight >= 0) { "fromHeight must be non-negative" }
        require(toHeight >= fromHeight) { "toHeight must be at least fromHeight" }
        val path = commitmentTreeLeavesPath(roundIdHex, fromHeight, toHeight)
        return executeWithVoteServerFailover(path) { baseUrl ->
            get("$baseUrl$path")
                .body<ChainCommitmentTreeLeavesResponse>()
                .toModel()
        }
    }

    private suspend fun getResolvedConfig(forceRefresh: Boolean = false): ResolvedVotingConfig =
        configMutex.withLock {
            val sources = resolveConfigSources()
            val cached = cachedResolvedConfig
            if (!forceRefresh && cached?.sources == sources) {
                cached
            } else {
                fetchTrustedConfig(sources).also { resolved ->
                    cachedResolvedConfig = resolved
                }
            }
        }

    private suspend fun resolveConfigSources(): List<PinnedConfigSource> {
        val selectedPinnedSource = votingChainConfigRepository.get().selectedPinnedSource
        if (selectedPinnedSource != null) {
            return resolvePinnedConfigSource(selectedPinnedSource)
        }

        val configuration = configurationRepository.configurationFlow.value
        val configUrl = configuration?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue).orEmpty()
        return resolvePinnedConfigSource(configUrl)
    }

    private suspend fun fetchStaticConfig(sources: List<PinnedConfigSource>): StaticVotingConfig =
        executeWithKtorTimeoutSupport { supportsKtorTimeouts ->
            fetchStaticConfigWalk(sources, supportsKtorTimeouts)
        }

    /**
     * Walks [sources] — the bundled trust anchor's mirror list, or a single-element list for a
     * user override — deliberately WIDER than [fetchDynamicServiceConfigWalk]: since every
     * mirror is independently checksum-gated, a transport failure, ANY non-200 response
     * ([StaticVotingConfigFetchFailedException]), or a checksum mismatch
     * ([StaticVotingConfigHashMismatchException]) is retryable, falling through to the next
     * mirror. A decode or [StaticVotingConfig.validate] failure *after* the checksum matched is
     * authoritative and thrown immediately — the pin guarantees identical bytes from every
     * mirror, so no other mirror can do better. When every mirror fails, the first (canonical
     * origin's) error is thrown.
     */
    private suspend fun HttpClient.fetchStaticConfigWalk(
        sources: List<PinnedConfigSource>,
        supportsKtorTimeouts: Boolean
    ): StaticVotingConfig =
        walkConfigSources(
            sources = sources,
            emptyMessage = "Static voting config source list is empty",
            describe = { source -> source.url },
            shouldTryNext = ::isRetryableStaticVotingConfigFailure
        ) { source ->
            val bytes = fetchStaticConfigBytes(source.url, supportsKtorTimeouts)
            StaticVotingConfig.decodeAndVerify(data = bytes, expectedSHA256 = source.sha256)
        }

    private suspend fun HttpClient.fetchStaticConfigBytes(
        url: String,
        supportsKtorTimeouts: Boolean
    ): ByteArray =
        try {
            withConfigRequestTimeoutFallback(supportsKtorTimeouts, configRequestTimeoutMillis) {
                get(url) {
                    noCache()
                    configRequestTimeout(supportsKtorTimeouts, configRequestTimeoutMillis)
                    excludeFromClientRetry()
                }.bodyAsBytes()
            }
        } catch (responseException: ResponseException) {
            throw StaticVotingConfigFetchFailedException(
                "Static voting config fetch failed: HTTP ${responseException.response.status.value}"
            )
        } catch (_: TimeoutCancellationException) {
            throw StaticVotingConfigFetchFailedException(
                "Static voting config fetch failed: request timed out after ${configRequestTimeoutMillis}ms"
            )
        } catch (exception: Exception) {
            exception.rethrowIfCancellation()
            throw StaticVotingConfigFetchFailedException(
                "Static voting config fetch failed: ${exception.message ?: exception::class.simpleName}"
            )
        }

    private suspend fun fetchTrustedConfig(sources: List<PinnedConfigSource>): ResolvedVotingConfig {
        val staticConfig = fetchStaticConfig(sources)
        val rawServiceConfig = fetchDynamicServiceConfig(staticConfig.resolvedDynamicConfigUrls())
        val serviceConfig = rawServiceConfig.retainingRoundsWithValidSignatures(staticConfig.trustedKeys)

        return ResolvedVotingConfig(
            sources = sources,
            staticConfig = staticConfig,
            rawServiceConfig = rawServiceConfig,
            serviceConfig = serviceConfig
        )
    }

    private suspend fun fetchDynamicServiceConfig(urls: List<String>): VotingServiceConfig =
        executeWithKtorTimeoutSupport { supportsKtorTimeouts ->
            fetchDynamicServiceConfigWalk(urls, supportsKtorTimeouts)
        }

    /**
     * Tries every configured dynamic-config URL in order (v2's fallback mirror alongside the
     * primary valargroup.dev host — MOB-1806/MOB-1809) and returns the first one that fetches,
     * decodes, and validates successfully. Unlike [fetchStaticConfigWalk], this walk is
     * NARROWER: only a transport failure or an HTTP 5xx response
     * ([DynamicVotingConfigTransientException]) is retryable. An HTTP 4xx response, and any
     * decode or [VotingServiceConfig.validate] failure of successfully fetched bytes, is
     * authoritative and thrown immediately — the README-normative semantics mirroring iOS's
     * `fetchDynamicConfigData`. When every URL fails, the first (canonical origin's) error is
     * thrown, not a joined list.
     */
    private suspend fun HttpClient.fetchDynamicServiceConfigWalk(
        urls: List<String>,
        supportsKtorTimeouts: Boolean
    ): VotingServiceConfig {
        val normalizedUrls = urls.map(String::trim).filter(String::isNotEmpty).distinct()
        val cacheBustToken = UUID.randomUUID().toString()

        return walkConfigSources(
            sources = normalizedUrls,
            emptyMessage = "Static voting config does not list any dynamic_config_urls",
            describe = { url -> url },
            shouldTryNext = ::isRetryableDynamicVotingConfigFailure
        ) { url ->
            fetchSingleDynamicServiceConfig(url, cacheBustToken, supportsKtorTimeouts)
        }
    }

    private suspend fun HttpClient.fetchSingleDynamicServiceConfig(
        url: String,
        cacheBustToken: String,
        supportsKtorTimeouts: Boolean
    ): VotingServiceConfig {
        val body = fetchDynamicConfigBody(url, cacheBustToken, supportsKtorTimeouts)
        return VotingServiceConfig.decode(body).also(VotingServiceConfig::validate)
    }

    /**
     * Fetches the raw body for one dynamic-config URL, cache-busting only requests to
     * raw.githubusercontent.com (its branch paths are CDN-cached ~300s server-side and ignore
     * request cache headers — a plain host-equality check, never contains/endsWith, so this
     * never fires for the content-addressed static-config mirror). [cacheBustToken] is one
     * random value per walk invocation, shared by every busted request in that walk; the
     * cache-bust query parameter is applied to the request only — [url] itself, un-busted, is
     * what the caller logs and reports as the serving origin.
     *
     * The generic transport-failure branch below builds its message from that clean [url] plus
     * the exception's short class name — never from `exception.message` verbatim, since for a
     * Ktor timeout/connect failure that message can contain the full busted request URL,
     * including the cache-bust token.
     */
    private suspend fun HttpClient.fetchDynamicConfigBody(
        url: String,
        cacheBustToken: String,
        supportsKtorTimeouts: Boolean
    ): String =
        try {
            withConfigRequestTimeoutFallback(supportsKtorTimeouts, configRequestTimeoutMillis) {
                get(url.withCacheBustIfNeeded(cacheBustToken)) {
                    noCache()
                    configRequestTimeout(supportsKtorTimeouts, configRequestTimeoutMillis)
                    excludeFromClientRetry()
                }.bodyAsText()
            }
        } catch (responseException: ResponseException) {
            throw responseException.toDynamicVotingConfigException()
        } catch (_: TimeoutCancellationException) {
            throw DynamicVotingConfigTransientException(
                "Dynamic voting config fetch failed for $url: request timed out after ${configRequestTimeoutMillis}ms"
            )
        } catch (exception: Exception) {
            exception.rethrowIfCancellation()
            throw DynamicVotingConfigTransientException(
                "Dynamic voting config fetch failed for $url: ${exception::class.simpleName ?: "unknown error"}"
            )
        }

    private suspend fun authenticateVotingSession(session: VotingSession): VotingSession {
        val resolvedConfig = getResolvedConfig()
        val roundIdHex = session.voteRoundId.toLowerHex()
        val status =
            RoundAuthenticator.authenticate(
                chainEaPK = session.eaPK,
                roundIdHex = roundIdHex,
                rounds = resolvedConfig.rawServiceConfig.rounds,
                trustedKeys = resolvedConfig.staticConfig.trustedKeys,
                pirLayout = resolvedConfig.rawServiceConfig.pirLayout
            )
        if (status != RoundAuthStatus.AUTHENTICATED) {
            throw VotingRoundAuthenticationException(status = status, roundIdHex = roundIdHex)
        }
        return session
    }

    private suspend fun authenticateVotingSessionOrNull(session: VotingSession): VotingSession? =
        try {
            authenticateVotingSession(session)
        } catch (exception: VotingRoundAuthenticationException) {
            Log.w(
                TAG,
                "Skipping unauthenticated voting round ${exception.roundIdHex}: ${exception.status}"
            )
            null
        }

    private suspend fun configuredVoteServerUrls(): List<String> =
        getResolvedConfig()
            .serviceConfig
            .voteServers
            .map(VotingServiceConfig.ServiceEndpoint::url)
            .normalizedVoteServerUrls()

    private suspend fun <T> executeWithVoteServerFailover(
        path: String,
        shouldTryNext: (Throwable) -> Boolean = ::shouldTryNextVoteServer,
        block: suspend HttpClient.(String) -> T
    ): T {
        val serverUrls = configuredVoteServerUrls()
        return execute {
            withVoteServerFailover(
                path = path,
                serverUrls = serverUrls,
                shouldTryNext = shouldTryNext,
                operation = { serverUrl -> block(serverUrl) }
            )
        }
    }

    private suspend fun HttpClient.postTxResult(
        url: String,
        body: String
    ): TxResult =
        try {
            post(url) {
                setBody(TextContent(body, ContentType.Application.Json))
            }.bodyAsText().toTxResult()
        } catch (responseException: ResponseException) {
            if (responseException.response.status == HttpStatusCode.UnprocessableEntity) {
                responseException.response.bodyAsText().toTxResult()
            } else {
                throw responseException
            }
        }

    private suspend inline fun <T> execute(
        crossinline block: suspend HttpClient.() -> T
    ): T =
        executeWithKtorTimeoutSupport { block() }

    private suspend inline fun <T> executeWithKtorTimeoutSupport(
        crossinline block: suspend HttpClient.(Boolean) -> T
    ): T =
        withContext(Dispatchers.IO) {
            val supportsKtorTimeouts = httpClientProvider.supportsKtorTimeouts()
            httpClientProvider.create().use { httpClient ->
                block(httpClient, supportsKtorTimeouts)
            }
        }

    private suspend fun HttpClient.postShareToTargets(
        targetUrls: List<String>,
        body: String,
        supportsKtorTimeouts: Boolean
    ): List<String> =
        coroutineScope {
            targetUrls
                .map { targetUrl ->
                    async {
                        if (postShare(targetUrl, body, supportsKtorTimeouts)) {
                            targetUrl
                        } else {
                            null
                        }
                    }
                }.awaitAll()
                .filterNotNull()
        }

    private suspend fun HttpClient.postShare(
        serverUrl: String,
        body: String,
        supportsKtorTimeouts: Boolean
    ): Boolean =
        try {
            post("$serverUrl/shielded-vote/v1/shares") {
                setBody(TextContent(body, ContentType.Application.Json))
                helperRequestTimeout(supportsKtorTimeouts)
            }
            serverHealthTracker.recordSuccess(serverUrl)
            true
        } catch (_: Throwable) {
            serverHealthTracker.recordFailure(serverUrl)
            false
        }
}

private class VotingServerHealthTracker {
    private val mutex = Mutex()
    private val statesByUrl = mutableMapOf<String, ServerState>()

    suspend fun remember(serverUrls: List<String>) {
        val normalizedUrls = serverUrls.normalizeServerUrls()
        mutex.withLock {
            normalizedUrls.forEach { serverUrl ->
                statesByUrl.putIfAbsent(serverUrl, ServerState())
            }
        }
    }

    suspend fun healthyServers(serverUrls: List<String>): List<String> {
        val normalizedUrls = serverUrls.normalizeServerUrls()
        if (normalizedUrls.isEmpty()) {
            return emptyList()
        }
        val nowMillis = System.currentTimeMillis()
        return mutex.withLock {
            val healthyServers =
                normalizedUrls.filter { serverUrl ->
                    val state = statesByUrl.getOrPut(serverUrl, ::ServerState)
                    when (val circuit = state.circuit) {
                        Circuit.Closed,
                        Circuit.HalfOpen -> {
                            true
                        }

                        is Circuit.Open -> {
                            if (nowMillis - circuit.sinceMillis >= COOLDOWN_INTERVAL_MILLIS) {
                                state.circuit = Circuit.HalfOpen
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            healthyServers.ifEmpty { normalizedUrls }
        }
    }

    suspend fun recordSuccess(serverUrl: String) {
        val normalizedServerUrl = serverUrl.trimEnd('/')
        if (normalizedServerUrl.isEmpty()) {
            return
        }
        mutex.withLock {
            statesByUrl.getOrPut(normalizedServerUrl, ::ServerState).apply {
                circuit = Circuit.Closed
                consecutiveFailures = 0
            }
        }
    }

    suspend fun recordFailure(serverUrl: String) {
        val normalizedServerUrl = serverUrl.trimEnd('/')
        if (normalizedServerUrl.isEmpty()) {
            return
        }
        val nowMillis = System.currentTimeMillis()
        mutex.withLock {
            val state = statesByUrl.getOrPut(normalizedServerUrl, ::ServerState)
            state.consecutiveFailures += 1
            when (state.circuit) {
                Circuit.HalfOpen -> {
                    state.circuit = Circuit.Open(nowMillis)
                }

                Circuit.Closed -> {
                    if (state.consecutiveFailures >= FAILURE_THRESHOLD) {
                        state.circuit = Circuit.Open(nowMillis)
                    }
                }

                is Circuit.Open -> {
                    Unit
                }
            }
        }
    }

    private class ServerState(
        var circuit: Circuit = Circuit.Closed,
        var consecutiveFailures: Int = 0
    )

    private sealed interface Circuit {
        data object Closed : Circuit

        data class Open(
            val sinceMillis: Long
        ) : Circuit

        data object HalfOpen : Circuit
    }

    private companion object {
        const val FAILURE_THRESHOLD = 3
        const val COOLDOWN_INTERVAL_MILLIS = 30_000L
    }
}

private data class ResolvedVotingConfig(
    val sources: List<PinnedConfigSource>,
    val staticConfig: StaticVotingConfig,
    val rawServiceConfig: VotingServiceConfig,
    val serviceConfig: VotingServiceConfig,
)

/**
 * Thrown only by the static-config walk's fetch step for a transport failure or ANY non-200
 * response. Together with [StaticVotingConfigHashMismatchException], this is one of the two
 * failure classes [isRetryableStaticVotingConfigFailure] treats as retryable. A decode or
 * [StaticVotingConfig.validate] failure of successfully fetched, checksum-matched bytes is
 * thrown as a plain [VotingConfigException] instead, so it is never retried.
 */
internal class StaticVotingConfigFetchFailedException(
    message: String
) : VotingConfigException(message)

internal fun isRetryableStaticVotingConfigFailure(throwable: Throwable): Boolean =
    throwable is StaticVotingConfigFetchFailedException || throwable is StaticVotingConfigHashMismatchException

/**
 * Thrown only by the dynamic-config walk's fetch step for a transport failure or an HTTP 5xx
 * response — the two failure classes [isRetryableDynamicVotingConfigFailure] treats as
 * retryable. An HTTP 4xx response, and a decode or `validate()` failure of successfully fetched
 * bytes, are thrown as a plain [VotingConfigException] instead, so the walk's narrower predicate
 * never lets them retry and they propagate as authoritative.
 */
internal class DynamicVotingConfigTransientException(
    message: String
) : VotingConfigException(message)

internal fun isRetryableDynamicVotingConfigFailure(throwable: Throwable): Boolean =
    throwable is DynamicVotingConfigTransientException

/**
 * Classifies a dynamic-config fetch's non-2xx response without throwing, so the single `throw`
 * at the call site is the only throw statement this classification contributes to that
 * function's count: an HTTP 5xx is [DynamicVotingConfigTransientException] (retryable), every
 * other status is a plain [VotingConfigException] (authoritative).
 */
private fun ResponseException.toDynamicVotingConfigException(): VotingConfigException {
    val statusValue = response.status.value
    val message = "Dynamic voting config fetch failed: HTTP $statusValue"
    return if (statusValue in TRANSIENT_HTTP_STATUS_MIN..TRANSIENT_HTTP_STATUS_MAX) {
        DynamicVotingConfigTransientException(message)
    } else {
        VotingConfigException(message)
    }
}

private fun HttpRequestBuilder.noCache() {
    header("Cache-Control", "no-cache")
    header("Pragma", "no-cache")
}

private fun HttpRequestBuilder.helperRequestTimeout(supportsKtorTimeouts: Boolean) {
    if (supportsKtorTimeouts) {
        timeout {
            requestTimeoutMillis = HELPER_REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = HELPER_SOCKET_TIMEOUT_MILLIS
            connectTimeoutMillis = HELPER_CONNECT_TIMEOUT_MILLIS
        }
    }
}

private fun HttpRequestBuilder.configRequestTimeout(
    supportsKtorTimeouts: Boolean,
    timeoutMillis: Long
) {
    if (supportsKtorTimeouts) {
        timeout {
            requestTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis
            connectTimeoutMillis = timeoutMillis
        }
    }
}

/**
 * Bounds a config-fetch attempt to [timeoutMillis] app-side when Ktor-level timeouts are
 * unavailable ([supportsKtorTimeouts] == false — the Tor client is built with
 * `installTimeouts = false`, so [configRequestTimeout] is a no-op there and a stalling origin
 * would otherwise hang the attempt forever). When [supportsKtorTimeouts] is true, [block] runs
 * unbounded here — Ktor's own per-request [io.ktor.client.plugins.HttpTimeout] already enforces
 * the same bound.
 *
 * TRAP: [kotlinx.coroutines.TimeoutCancellationException] EXTENDS [CancellationException].
 * kotlinx.coroutines guarantees it is delivered only to the [kotlinx.coroutines.withTimeout]
 * frame whose own deadline actually expired; a genuine OUTER cancellation manifests as a
 * *different* [CancellationException] and is never caught by a `catch (TimeoutCancellationException)`
 * clause here. Every caller MUST catch [TimeoutCancellationException] explicitly and convert it to
 * that leg's own retryable exception BEFORE any generic `catch (exception: Exception)` /
 * [rethrowIfCancellation] handling runs — letting it fall through to that generic handling would
 * rethrow it as a cancellation and abort the whole mirror walk instead of falling through to the
 * next mirror.
 */
private suspend fun <T> withConfigRequestTimeoutFallback(
    supportsKtorTimeouts: Boolean,
    timeoutMillis: Long,
    block: suspend () -> T
): T =
    if (supportsKtorTimeouts) {
        block()
    } else {
        withTimeout(timeoutMillis) { block() }
    }

/**
 * Marks a config-fetch request as exempt from the direct client's [HttpRequestRetry] policy
 * (MOB-1809): retry policy for a config request belongs entirely to [walkConfigSources]'s mirror
 * fallback, which classifies and fails fast per attempt. Left unmarked, the client's default
 * policy (`~5` attempts with exponential backoff, roughly 90s) would internally exhaust itself
 * against a single dead mirror before the walk's own classification — and
 * [withConfigRequestTimeoutFallback]'s fail-fast bound — ever runs. Uses Ktor's own per-request
 * retry configuration ([io.ktor.client.plugins.retry]); on a client where [HttpRequestRetry]
 * isn't installed at all (the Tor client), this is a harmless no-op.
 */
private fun HttpRequestBuilder.excludeFromClientRetry() {
    retry { noRetry() }
}

/**
 * Appends a unique `zodl_cache_bust` query parameter when, and only when, this URL's host is
 * exactly `raw.githubusercontent.com` (case-insensitive equality — never contains/endsWith, so
 * an unrelated host that merely mentions it in a path or query is never matched). GitHub's raw
 * CDN caches branch paths for roughly 300 seconds server-side and ignores request cache-control
 * headers, which matters during round rollover; a content-addressed pin never needs this since
 * its bytes are immutable by definition.
 *
 * This is only ever applied to the DYNAMIC config leg's URLs. The static leg's
 * raw.githubusercontent.com mirror ([StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR]) is
 * deliberately never cache-busted, matching iOS: that path is content-addressed (the checksum is
 * in the filename itself), so its bytes are immutable by construction and a pin bump changes the
 * URL rather than the content behind an existing one — there is nothing for a CDN cache to serve
 * stale.
 *
 * Rebuilt via URI components (scheme/authority/path/query/fragment) rather than blind string
 * concatenation, so the busted query parameter always lands before a fragment instead of being
 * appended after one and silently becoming part of it. Config URLs are not expected to carry a
 * fragment in practice, but this stays correct if one is ever present.
 */
private fun String.withCacheBustIfNeeded(token: String): String {
    val uri = runCatching { URI(this) }.getOrNull()
    if (uri == null || !uri.host.equals(RAW_GITHUBUSERCONTENT_HOST, ignoreCase = true)) {
        return this
    }
    val existingQuery = uri.rawQuery
    val bustedQuery =
        if (existingQuery.isNullOrEmpty()) {
            "$CACHE_BUST_QUERY_PARAM=$token"
        } else {
            "$existingQuery&$CACHE_BUST_QUERY_PARAM=$token"
        }
    return buildString {
        append(uri.scheme)
        append("://")
        append(uri.rawAuthority)
        append(uri.rawPath.orEmpty())
        append('?')
        append(bustedQuery)
        if (uri.rawFragment != null) {
            append('#')
            append(uri.rawFragment)
        }
    }
}

private const val HELPER_REQUEST_TIMEOUT_MILLIS = 5_000L
private const val HELPER_SOCKET_TIMEOUT_MILLIS = 10_000L
private const val HELPER_CONNECT_TIMEOUT_MILLIS = 5_000L
private const val TAG = "VotingApiProvider"
private const val ACTIVE_ROUNDS_PATH = "/shielded-vote/v1/rounds/active"
private const val ROUNDS_PATH = "/shielded-vote/v1/rounds"
private const val ENDORSED_ROUNDS_PATH = "/shielded-vote/v1/endorsed-rounds/zodl"
private const val DELEGATE_VOTE_PATH = "/shielded-vote/v1/delegate-vote"
private const val CAST_VOTE_PATH = "/shielded-vote/v1/cast-vote"
private const val RAW_GITHUBUSERCONTENT_HOST = "raw.githubusercontent.com"
private const val CACHE_BUST_QUERY_PARAM = "zodl_cache_bust"
private const val TRANSIENT_HTTP_STATUS_MIN = 500
private const val TRANSIENT_HTTP_STATUS_MAX = 599

private fun List<String>.normalizeServerUrls(): List<String> =
    map(String::trim)
        .filter(String::isNotEmpty)
        .map { serverUrl -> serverUrl.trimEnd('/') }
        .distinct()

/**
 * Builds the share-delegation POST body as `zcash_voting::wire::VoteShareWire::to_json()`
 * defines it (MOB-1678, zcash_voting 3.0): `shares_hash`, `enc_share`, `share_index`,
 * `tree_position`, `vote_round_id`, `share_comms`, `primary_blind`, `submit_at` — every
 * byte field base64, matching `VoteShareWire`'s `serde_base64_bytes` fields exactly.
 * `all_enc_shares` was dropped from this wire struct (2.0-rc.4) and must never be sent.
 *
 * `vote_round_id` is the crate-provided [SharePayload.voteRoundId] verbatim — the SDK's
 * `buildSharePayloadsNative`/`JniSharePayload` now carries it, so nothing is injected
 * app-side.
 */
internal fun SharePayload.toApiBody() =
    JSONObject()
        .put("shares_hash", sharesHash.toBase64String())
        .put("proposal_id", proposalId)
        .put("vote_decision", voteDecision)
        .put(
            "enc_share",
            JSONObject()
                .put("c1", encShare.c1.toBase64String())
                .put("c2", encShare.c2.toBase64String())
                .put("share_index", encShare.shareIndex)
        ).put("share_index", encShare.shareIndex)
        .put("tree_position", treePosition)
        .put("vote_round_id", voteRoundId)
        .put("share_comms", org.json.JSONArray(shareComms.map(ByteArray::toBase64String)))
        .put("primary_blind", primaryBlind.toBase64String())
        .put("submit_at", submitAt)
        .toString()

private fun DelegationRegistration.toApiBody(): String =
    JSONObject()
        .put("rk", rk.toBase64String())
        .put("spend_auth_sig", spendAuthSig.toBase64String())
        .put("tx1_effects", tx1Effects.toBase64String())
        .put("signed_note_nullifier", signedNoteNullifier.toBase64String())
        .put("cmx_new", cmxNew.toBase64String())
        .put("van_cmx", vanCmx.toBase64String())
        .put("gov_nullifiers", org.json.JSONArray(govNullifiers.map(ByteArray::toBase64String)))
        .put("proof", proof.toBase64String())
        .put("vote_round_id", voteRoundId.toBase64String())
        .toString()

private fun VoteCommitmentBundle.toApiBody(signature: CastVoteSignature): String =
    JSONObject()
        .put("van_nullifier", vanNullifier.toBase64String())
        .put("vote_authority_note_new", voteAuthorityNoteNew.toBase64String())
        .put("vote_commitment", voteCommitment.toBase64String())
        .put("proposal_id", proposalId)
        .put("proof", proof.toBase64String())
        .put("vote_round_id", voteRoundId.hexToBase64String())
        .put("vote_comm_tree_anchor_height", anchorHeight)
        .put("r_vpk", rVpkBytes.toBase64String())
        .put("vote_auth_sig", signature.voteAuthSig.toBase64String())
        .toString()

internal fun String.toTxResult(): TxResult {
    val json = JSONObject(this)
    return TxResult(
        txHash = json.optString("tx_hash"),
        code = json.optNumber("code").toInt(),
        log = json.optString("log")
    )
}

internal fun String.toTxConfirmation(): TxConfirmation {
    val json = JSONObject(this)
    val events = json.optJSONArray("events")
    return TxConfirmation(
        height = json.optNumber("height").toLong(),
        code = json.optNumber("code").toInt(),
        log = json.optString("log"),
        events =
            buildList {
                if (events == null) return@buildList
                for (index in 0 until events.length()) {
                    val event = events.optJSONObject(index) ?: continue
                    val attributes = event.optJSONArray("attributes")
                    add(
                        TxEvent(
                            type = event.optString("type"),
                            attributes =
                                buildList {
                                    if (attributes == null) return@buildList
                                    for (attributeIndex in 0 until attributes.length()) {
                                        val attribute = attributes.optJSONObject(attributeIndex) ?: continue
                                        add(
                                            TxEventAttribute(
                                                key = attribute.optString("key"),
                                                value = attribute.optString("value")
                                            )
                                        )
                                    }
                                }
                        )
                    )
                }
            }
    )
}

private fun JSONObject.optNumber(key: String): Number {
    val value = opt(key)
    return when (value) {
        is Number -> value
        is String -> value.toLongOrNull() ?: value.toIntOrNull() ?: 0
        else -> 0
    }
}

private fun String.hexToBase64String(): String =
    chunked(2)
        .map { chunk -> chunk.toInt(HEX_RADIX).toByte() }
        .toByteArray()
        .toBase64String()

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff

private fun tallyResultsPath(roundIdHex: String): String =
    "/shielded-vote/v1/tally-results/$roundIdHex"

private fun txConfirmationPath(txHash: String): String =
    "/shielded-vote/v1/tx/$txHash"

internal fun commitmentTreeLatestPath(roundIdHex: String): String =
    "/shielded-vote/v1/commitment-tree/$roundIdHex/latest"

internal fun commitmentTreeLeavesPath(
    roundIdHex: String,
    fromHeight: Long,
    toHeight: Long
): String =
    "/shielded-vote/v1/commitment-tree/$roundIdHex/leaves?from_height=$fromHeight&to_height=$toHeight"

internal fun shouldTreatEndorsedRoundsStatusAsEmpty(status: HttpStatusCode): Boolean =
    status == HttpStatusCode.BadRequest || status == HttpStatusCode.NotFound

internal fun shouldTreatEndorsedRoundsFailoverFailuresAsEmpty(
    statuses: List<HttpStatusCode?>
): Boolean =
    statuses.isNotEmpty() &&
        statuses.all { status ->
            status != null && shouldTreatEndorsedRoundsStatusAsEmpty(status)
        }

/**
 * Resolves an override URL (a custom chain's pinned source, or remote config's
 * `voting_config_url`) to its source list. An override that fails to parse, or an empty URL
 * (no override configured), resolves to the full bundled mirror list
 * ([StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES]) — canonical origin first, so a saved
 * copy of the default keeps its own mirror fallback. A valid override is a single-element list
 * (no mirrors) UNLESS it is byte-equal (URL and checksum, i.e. [PinnedConfigSource] equality)
 * to one of the bundled mirrors, in which case the full bundled list is used instead.
 */
internal fun resolvePinnedConfigSource(configUrl: String): List<PinnedConfigSource> {
    val parsedOverride =
        if (configUrl.isNotEmpty()) {
            runCatching { PinnedConfigSource.parse(configUrl) }.getOrNull()
        } else {
            null
        } ?: return StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES

    return if (parsedOverride in StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES) {
        StaticVotingConfig.BUNDLED_PINNED_CONFIG_SOURCES
    } else {
        listOf(parsedOverride)
    }
}

private suspend fun Throwable.isNoActiveRoundFailure(): Boolean =
    when (this) {
        is VotingServerFailoverException -> {
            lastError?.isNoActiveRoundFailure() == true
        }

        is ResponseException -> {
            response.status == HttpStatusCode.NotFound || isNoActiveRoundResponse()
        }

        else -> {
            false
        }
    }

private suspend fun ResponseException.isNoActiveRoundResponse(): Boolean {
    if (response.status != HttpStatusCode.InternalServerError) {
        return false
    }

    val responseText =
        runCatching { response.bodyAsText() }
            .getOrNull()
            ?.lowercase()
            ?: return false

    return "no active voting round" in responseText && "key not found" in responseText
}
