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
    // Default (all-zero) matches zcash_voting::config::PirLayout::UNKNOWN — the sentinel for
    // configs published before the pir_layout handshake (zcash_voting 2.0.0-rc.4) existed.
    @SerialName("pir_layout")
    val pirLayout: VotingPirLayout = VotingPirLayout(),
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
        if (configVersion != 1) {
            throw VotingConfigException("Unsupported config_version $configVersion")
        }
        if (voteServers.isEmpty()) {
            throw VotingConfigException("vote_servers must contain at least one entry")
        }
        if (pirEndpoints.isEmpty()) {
            throw VotingConfigException("pir_endpoints must contain at least one entry")
        }
        rounds.keys.forEach { roundId ->
            if (roundId.length != ROUND_ID_HEX_LENGTH || !roundId.isLowercaseHex()) {
                throw VotingConfigException("rounds key must be 64 lowercase hex characters: $roundId")
            }
        }
        if (!WalletCapabilities.voteServer.contains(supportedVersions.voteServer)) {
            throw VotingConfigException(
                "Wallet does not support vote_server version " +
                    "\"${supportedVersions.voteServer}\". Please update the wallet."
            )
        }
        if (!WalletCapabilities.voteProtocol.contains(supportedVersions.voteProtocol)) {
            throw VotingConfigException(
                "Wallet does not support vote_protocol version " +
                    "\"${supportedVersions.voteProtocol}\". Please update the wallet."
            )
        }
        if (!WalletCapabilities.tally.contains(supportedVersions.tally)) {
            throw VotingConfigException(
                "Wallet does not support tally version " +
                    "\"${supportedVersions.tally}\". Please update the wallet."
            )
        }
        if (WalletCapabilities.pir.intersect(supportedVersions.pir.toSet()).isEmpty()) {
            throw VotingConfigException(
                "Wallet does not support pir version " +
                    "\"${supportedVersions.pir.joinToString(separator = ",")}\". Please update the wallet."
            )
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

@Serializable
data class VotingPirLayout(
    @SerialName("pir_depth")
    val pirDepth: Int = 0,
    @SerialName("tier0_layers")
    val tier0Layers: Int = 0,
    @SerialName("tier1_layers")
    val tier1Layers: Int = 0,
    /**
     * Absent in configs published before the v1.3.0 vote chain (zcash_voting 3.0.0-rc.3)
     * introduced the poly_len handshake field; decodes to 0, which keeps this layout at the
     * UNKNOWN sentinel below rather than silently assuming 2048.
     */
    @SerialName("poly_len")
    val polyLen: Int = 0,
)

/**
 * MOB-1678 (zcash_voting 3.0 bump): `pir_layout.poly_len` is load-bearing — the crate
 * validates it locally (must be 2048 or 4096) and the PIR connect handshake re-checks it
 * against the server. A dynamic config without the field predates the 3.0 service, so every
 * delegation entry point fails closed *before any FFI call* rather than fabricating a value.
 * The thrown [VotingConfigException] reuses the existing voting config-error copy — no new
 * strings are introduced for this guard.
 */
fun VotingPirLayout.requireKnownPolyLen(): VotingPirLayout {
    if (polyLen == 0) {
        throw VotingConfigException("Voting config is missing pir_layout.poly_len required for delegation")
    }
    return this
}

open class VotingConfigException(
    message: String
) : IllegalStateException(message)

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
            rounds.filter { (roundIdHex, entry) ->
                RoundAuthenticator.verifyEntrySignatures(
                    entry = entry,
                    roundIdHex = roundIdHex,
                    pirLayout = pirLayout,
                    trustedKeys = trustedKeys
                )
            }
    )
