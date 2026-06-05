package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

@Serializable
data class StaticVotingConfig(
    @SerialName("static_config_version")
    val staticConfigVersion: Int = SUPPORTED_VERSION,
    @SerialName("dynamic_config_url")
    val dynamicConfigURL: String,
    @SerialName("trusted_keys")
    val trustedKeys: List<TrustedKey> = emptyList(),
) {
    @Serializable
    data class TrustedKey(
        @SerialName("key_id")
        val keyId: String,
        val alg: String,
        val pubkey: String,
        val notes: String? = null,
    ) {
        fun pubkeyBytes(): ByteArray =
            decodeBase64Field(pubkey, "trusted_keys[$keyId].pubkey")
    }

    fun validate() {
        if (staticConfigVersion != SUPPORTED_VERSION) {
            throw VotingConfigException("Unsupported static_config_version $staticConfigVersion")
        }
        if (trustedKeys.isEmpty()) {
            throw VotingConfigException("trusted_keys must contain at least one entry")
        }
        trustedKeys.forEach { key ->
            if (key.alg != ALG_ED25519) {
                throw VotingConfigException("trusted_keys[${key.keyId}].alg unsupported: ${key.alg}")
            }
            if (key.pubkeyBytes().size != ED25519_PUBLIC_KEY_BYTES) {
                throw VotingConfigException("trusted_keys[${key.keyId}].pubkey must decode to 32 bytes")
            }
        }
    }

    companion object {
        const val SUPPORTED_VERSION = 1
        const val ALG_ED25519 = "ed25519"

        private const val ED25519_PUBLIC_KEY_BYTES = 32

        const val BUNDLED_PINNED_SOURCE =
            "https://raw.githubusercontent.com/valargroup/token-holder-voting-config/" +
                "2785311d45758e85567d70a1f13709fa01b62c6b/prod/static-voting-config.json" +
                "?checksum=sha256:bed0116f961226b256a574b52461ce81d9f5294a57e190987dc155f07eb1e431"

        fun decodeAndVerify(data: ByteArray, expectedSHA256: ByteArray?): StaticVotingConfig {
            if (expectedSHA256 != null) {
                val actualSHA256 = MessageDigest.getInstance("SHA-256").digest(data)
                if (!actualSHA256.contentEquals(expectedSHA256)) {
                    throw VotingConfigException(
                        "Static voting config hash mismatch: expected ${expectedSHA256.toLowerHex()}, " +
                            "got ${actualSHA256.toLowerHex()}"
                    )
                }
            }

            val config =
                runCatching {
                    staticVotingConfigJson.decodeFromString<StaticVotingConfig>(data.toString(Charsets.UTF_8))
                }.getOrElse { throwable ->
                    val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
                    throw VotingConfigException("Static voting config decode failed: $detail")
                }

            config.validate()
            return config
        }
    }
}

class PinnedConfigSource private constructor(
    val url: String,
    val sha256: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is PinnedConfigSource &&
            url == other.url &&
            when {
                sha256 == null -> other.sha256 == null
                other.sha256 == null -> false
                else -> sha256.contentEquals(other.sha256)
            }

    override fun hashCode(): Int =
        31 * url.hashCode() + (sha256?.contentHashCode() ?: 0)

    override fun toString(): String =
        "PinnedConfigSource(url=$url, sha256=${sha256?.toLowerHex()})"

    companion object {
        private const val CHECKSUM_PREFIX = "sha256:"
        private const val SHA256_HEX_LENGTH = 64

        fun parse(raw: String): PinnedConfigSource {
            val uri =
                runCatching { URI(raw) }.getOrElse {
                    throw VotingConfigException("Static config source malformed: not a URL: $raw")
                }
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
                throw VotingConfigException("Static config source malformed: not an HTTPS URL: $raw")
            }

            val queryParts =
                uri.rawQuery
                    ?.split('&')
                    ?.filter(String::isNotEmpty)
                    .orEmpty()

            var checksumValue: String? = null
            var hasCapturedChecksumValue = false
            var hasChecksum = false
            val strippedQueryParts =
                queryParts.filterNot { part ->
                    val rawName = part.substringBefore('=')
                    val isChecksum =
                        runCatching { urlDecode(rawName) }
                            .getOrDefault(rawName) == "checksum"
                    if (isChecksum && !hasCapturedChecksumValue) {
                        checksumValue =
                            part
                                .substringAfter('=', missingDelimiterValue = "")
                                .takeIf(String::isNotEmpty)
                        hasCapturedChecksumValue = true
                    }
                    hasChecksum = hasChecksum || isChecksum
                    isChecksum
                }

            val sha256 =
                if (hasChecksum) {
                    val checksum =
                        checksumValue?.let(::urlDecode)
                            ?: throw VotingConfigException("Static config source malformed: missing checksum value")
                    if (!checksum.startsWith(CHECKSUM_PREFIX)) {
                        throw VotingConfigException("Static config source malformed: checksum must start with sha256:")
                    }

                    val hex = checksum.drop(CHECKSUM_PREFIX.length)
                    if (hex.length != SHA256_HEX_LENGTH || !hex.isLowercaseHex()) {
                        throw VotingConfigException(
                            "Static config source malformed: sha256 must be 64 lowercase hex chars"
                        )
                    }
                    hex.lowercaseHexToBytes()
                } else {
                    null
                }

            val strippedQuery = strippedQueryParts.joinToString("&").takeIf(String::isNotEmpty)
            val strippedUrl =
                buildString {
                    append(uri.scheme)
                    append("://")
                    append(uri.rawAuthority)
                    append(uri.rawPath.orEmpty())
                    if (strippedQuery != null) {
                        append('?')
                        append(strippedQuery)
                    }
                    if (uri.rawFragment != null) {
                        append('#')
                        append(uri.rawFragment)
                    }
                }
            return PinnedConfigSource(url = strippedUrl, sha256 = sha256)
        }

        private fun urlDecode(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}

internal fun decodeBase64Field(value: String, fieldName: String): ByteArray =
    runCatching { Base64.getDecoder().decode(value) }.getOrElse {
        throw VotingConfigException("$fieldName must be base64")
    }

internal fun String.lowercaseHexToBytes(): ByteArray {
    if (length % 2 != 0 || !isLowercaseHex()) {
        throw VotingConfigException("Expected lowercase hex")
    }
    return chunked(2).map { chunk -> chunk.toInt(16).toByte() }.toByteArray()
}

internal fun String.isLowercaseHex(): Boolean =
    isNotEmpty() && all { character -> character in '0'..'9' || character in 'a'..'f' }

internal fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private val staticVotingConfigJson =
    Json {
        ignoreUnknownKeys = true
    }
