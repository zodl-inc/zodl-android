package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class VotingServiceConfig(
    @SerialName("config_version")
    val configVersion: Int = 1,
    @SerialName("vote_servers")
    val voteServers: List<ServiceEndpoint> = emptyList(),
    @SerialName("pir_endpoints")
    val pirEndpoints: List<ServiceEndpoint> = emptyList(),
    @SerialName("supported_versions")
    val supportedVersions: SupportedVersions = SupportedVersions(),
    val rounds: Map<String, RoundEntry> = emptyMap(),
) {
    @Serializable
    data class ServiceEndpoint(
        val url: String,
        val label: String
    )

    @Serializable
    data class SupportedVersions(
        val pir: List<String> = emptyList(),
        @SerialName("vote_protocol")
        val voteProtocol: String = "",
        val tally: String = "",
        @SerialName("vote_server")
        val voteServer: String = "",
    )

    @Serializable
    data class RoundEntry(
        @SerialName("auth_version")
        val authVersion: Int = 0,
        @SerialName("ea_pk")
        val eaPk: String = "",
        val signatures: List<Signature> = emptyList(),
    ) {
        fun eaPkBytes(): ByteArray =
            decodeBase64Field(eaPk, "rounds.ea_pk")
    }

    @Serializable
    data class Signature(
        @SerialName("key_id")
        val keyId: String = "",
        val alg: String = "",
        val sig: String = "",
    ) {
        fun sigBytes(): ByteArray =
            decodeBase64Field(sig, "rounds.signatures.sig")
    }

    fun validate() {
        requireVotingConfig(configVersion == 1) {
            "Unsupported config_version $configVersion"
        }
        requireVotingConfig(voteServers.isNotEmpty()) {
            "vote_servers must contain at least one entry"
        }
        requireVotingConfig(pirEndpoints.isNotEmpty()) {
            "pir_endpoints must contain at least one entry"
        }
        rounds.keys.forEach { roundId ->
            requireVotingConfig(roundId.length == ROUND_ID_HEX_LENGTH && roundId.isLowercaseHex()) {
                "rounds key must be 64 lowercase hex characters: $roundId"
            }
        }
        requireVotingConfig(WalletCapabilities.voteServer.contains(supportedVersions.voteServer)) {
                "Wallet does not support vote_server version " +
                    "\"${supportedVersions.voteServer}\". Please update the wallet."
        }
        requireVotingConfig(WalletCapabilities.voteProtocol.contains(supportedVersions.voteProtocol)) {
                "Wallet does not support vote_protocol version " +
                    "\"${supportedVersions.voteProtocol}\". Please update the wallet."
        }
        requireVotingConfig(WalletCapabilities.tally.contains(supportedVersions.tally)) {
                "Wallet does not support tally version " +
                    "\"${supportedVersions.tally}\". Please update the wallet."
        }
        requireVotingConfig(WalletCapabilities.pir.intersect(supportedVersions.pir.toSet()).isNotEmpty()) {
                "Wallet does not support pir version " +
                    "\"${supportedVersions.pir.joinToString(separator = ",")}\". Please update the wallet."
        }
    }

    fun encode(): String = votingConfigJson.encodeToString(this)

    companion object {
        val EMPTY = VotingServiceConfig()

        fun decode(raw: String): VotingServiceConfig =
            runCatching {
                votingConfigJson.decodeFromString<VotingServiceConfig>(raw)
            }.getOrElse { throwable ->
                val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
                throw VotingConfigException("Voting config decode failed: $detail")
            }

        private const val ROUND_ID_HEX_LENGTH = 64
    }
}

open class VotingConfigException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

internal inline fun requireVotingConfig(
    value: Boolean,
    lazyMessage: () -> String
) {
    if (!value) {
        failVotingConfig(lazyMessage())
    }
}

internal fun failVotingConfig(message: String): Nothing =
    throw VotingConfigException(message)

private object WalletCapabilities {
    val voteServer = setOf("v1")
    val voteProtocol = setOf("v0")
    val tally = setOf("v0")
    val pir = setOf("v0")
}

private val votingConfigJson =
    Json {
        ignoreUnknownKeys = true
    }

fun VotingServiceConfig.retainingRoundsWithValidSignatures(
    trustedKeys: List<StaticVotingConfig.TrustedKey>
): VotingServiceConfig =
    copy(
        rounds =
            rounds.filter { (_, entry) ->
                RoundAuthenticator.verifyEntrySignatures(entry = entry, trustedKeys = trustedKeys)
            }
    )
