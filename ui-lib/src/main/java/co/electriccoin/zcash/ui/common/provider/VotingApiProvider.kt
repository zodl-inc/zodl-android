package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.ChainActiveRoundResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainRoundsResponse
import co.electriccoin.zcash.ui.common.model.voting.ChainTxResponse
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface VotingApiProvider {
    suspend fun fetchServiceConfig(): VotingServiceConfig

    suspend fun fetchActiveVotingSession(): VotingSession?

    suspend fun fetchAllRounds(): List<VotingRound>

    suspend fun fetchRoundById(roundIdHex: String): VotingRound

    suspend fun fetchTallyResults(roundIdHex: String): TallyResults

    suspend fun submitDelegation(registration: DelegationRegistration)

    suspend fun submitVoteCommitment(bundle: VoteCommitmentBundle, signature: CastVoteSignature)

    suspend fun delegateShares(shares: List<SharePayload>, roundIdHex: String)

    suspend fun fetchTxConfirmation(txHash: String): Boolean
}

@Suppress("TooManyFunctions")
class KtorVotingApiProvider(
    private val httpClientProvider: HttpClientProvider
) : VotingApiProvider {
    private var cachedConfig: VotingServiceConfig? = null

    override suspend fun fetchServiceConfig(): VotingServiceConfig =
        execute {
            VotingServiceConfig.SERVERS.also { cachedConfig = it }
        }

    private val baseUrl: String
        get() =
            cachedConfig?.voteServers?.firstOrNull()?.url
                ?: VotingServiceConfig.SERVERS.voteServers
                    .first()
                    .url

    override suspend fun fetchActiveVotingSession(): VotingSession? =
        execute {
            runCatching {
                val resp = get("$baseUrl/shielded-vote/v1/rounds/active").body<ChainActiveRoundResponse>()
                resp.round?.let { dto ->
                    VotingSession(
                        voteRoundId = dto.voteRoundId.toByteArray(),
                        snapshotHeight = dto.snapshotHeight,
                        proposalsHash = ByteArray(0),
                        voteEndTime = java.time.Instant.ofEpochSecond(dto.voteEndTime),
                        ceremonyStart = java.time.Instant.EPOCH,
                        eaPK = dto.eaPkBytes(),
                        vkZkp1 = ByteArray(0),
                        vkZkp2 = ByteArray(0),
                        vkZkp3 = ByteArray(0),
                        ncRoot = dto.ncRootBytes(),
                        nullifierIMTRoot = dto.nullifierImtRootBytes(),
                        creator = dto.voteRoundId,
                        title = dto.title,
                        description = dto.description,
                        discussionUrl = null,
                        proposals = dto.proposals.map { it.toProposal() },
                        status = dto.toVotingRound().status,
                        createdAtHeight = dto.createdAtHeight,
                    )
                }
            }.getOrNull()
        }

    override suspend fun fetchAllRounds(): List<VotingRound> =
        execute {
            val resp = get("$baseUrl/shielded-vote/v1/rounds").body<ChainRoundsResponse>()
            resp.rounds?.map { it.toVotingRound() } ?: emptyList()
        }

    override suspend fun fetchRoundById(roundIdHex: String): VotingRound =
        execute {
            val resp = get("$baseUrl/shielded-vote/v1/round/$roundIdHex").body<ChainRoundResponse>()
            resp.round.toVotingRound()
        }

    override suspend fun fetchTallyResults(roundIdHex: String): TallyResults =
        execute {
            get("$baseUrl/shielded-vote/v1/round/$roundIdHex/results").body()
        }

    override suspend fun submitDelegation(registration: DelegationRegistration) =
        execute {
            post("$baseUrl/shielded-vote/v1/delegate-vote") {
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
            Unit
        }

    override suspend fun submitVoteCommitment(bundle: VoteCommitmentBundle, signature: CastVoteSignature) =
        execute {
            post("$baseUrl/shielded-vote/v1/cast-vote") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("bundle" to bundle, "signature" to signature))
            }
            Unit
        }

    override suspend fun delegateShares(shares: List<SharePayload>, roundIdHex: String) =
        execute {
            post("$baseUrl/shielded-vote/v1/round/$roundIdHex/shares") {
                contentType(ContentType.Application.Json)
                setBody(shares)
            }
            Unit
        }

    override suspend fun fetchTxConfirmation(txHash: String): Boolean =
        execute {
            runCatching {
                val resp = get("$baseUrl/shielded-vote/v1/tx/$txHash").body<ChainTxResponse>()
                resp.tx?.confirmed ?: false
            }.getOrDefault(false)
        }

    @Suppress("TooGenericExceptionCaught")
    @Throws(ResponseException::class)
    private suspend inline fun <T> execute(
        crossinline block: suspend HttpClient.() -> T
    ): T = withContext(Dispatchers.IO) { httpClientProvider.create().use { block(it) } }
}
