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
    val staticConfigVersion: Int,
    @SerialName("dynamic_config_url")
    val dynamicConfigURL: String? = null,
    @SerialName("dynamic_config_urls")
    val dynamicConfigURLs: List<String> = emptyList(),
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
        if (staticConfigVersion !in SUPPORTED_VERSIONS) {
            throw VotingConfigException("Unsupported static_config_version $staticConfigVersion")
        }
        when (staticConfigVersion) {
            STATIC_CONFIG_VERSION_V1 -> {
                if (dynamicConfigURL.isNullOrBlank()) {
                    throw VotingConfigException("dynamic_config_url must not be blank")
                }
                requireHttpsScheme(dynamicConfigURL, "dynamic_config_url")
            }

            STATIC_CONFIG_VERSION_V2 -> {
                if (dynamicConfigURLs.isEmpty()) {
                    throw VotingConfigException("dynamic_config_urls must contain at least one entry")
                }
                if (dynamicConfigURLs.any(String::isBlank)) {
                    throw VotingConfigException("dynamic_config_urls must not contain blank entries")
                }
                dynamicConfigURLs.forEach { url -> requireHttpsScheme(url, "dynamic_config_urls") }
            }
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

    /**
     * Unifies the v1 singular `dynamic_config_url` and the v2 `dynamic_config_urls` array
     * behind one accessor, so callers never need to branch on [staticConfigVersion]: for v2 this
     * is [dynamicConfigURLs] verbatim, for v1 it is [dynamicConfigURL] wrapped in a single-item
     * list.
     */
    fun resolvedDynamicConfigUrls(): List<String> =
        if (staticConfigVersion == STATIC_CONFIG_VERSION_V2) {
            dynamicConfigURLs
        } else {
            listOfNotNull(dynamicConfigURL)
        }

    companion object {
        const val STATIC_CONFIG_VERSION_V1 = 1
        const val STATIC_CONFIG_VERSION_V2 = 2
        const val ALG_ED25519 = "ed25519"

        private val SUPPORTED_VERSIONS = setOf(STATIC_CONFIG_VERSION_V1, STATIC_CONFIG_VERSION_V2)
        private const val ED25519_PUBLIC_KEY_BYTES = 32

        /**
         * Content-addressed pin (checksum 28fc9b63) via the resilient voting.valargroup.dev
         * gateway — MOB-1678 hardening (analog of iOS zodl-ios#1996), bumped to static config
         * version 2 for MOB-1806 (analog of iOS MOB-1801): the only schema change from v1 is
         * `dynamic_config_url` (singular) becoming `dynamic_config_urls` (an array — currently
         * the valargroup.dev URL plus a raw.githubusercontent.com mirror), which is Valar's
         * defense against an ISP blocking `voting.valargroup.dev` outright; `trusted_keys` is
         * unchanged. The gateway serves this exact content-addressed path (/pins/prod/<sha>/...)
         * immutably, same as the previous raw.githubusercontent.com blob pin, but reads from
         * GitHub normally and falls back to an automatically published Cloudflare copy during a
         * GitHub outage — so a GitHub outage no longer blocks default-configured users from
         * loading their voting trust anchor. This is NOT the mutable
         * https://voting.valargroup.dev/prod/static-voting-config.json URL: that one gets
         * republished on every new round/key rotation, which would break this bundled checksum
         * for every default-configured user on the very next republish and brick voting until an
         * app update. Bump the checksum (in both the path and the query param) whenever this
         * bundled fallback needs to move forward to a newer trusted_keys set.
         */
        const val BUNDLED_PINNED_SOURCE =
            "https://voting.valargroup.dev/pins/prod/" +
                "28fc9b631091ae8bc2f8635d8930489238ce144174cbd15a03efb0530b301ebe/v2-static-voting-config.json" +
                "?checksum=sha256:28fc9b631091ae8bc2f8635d8930489238ce144174cbd15a03efb0530b301ebe"

        /**
         * Mirror of [BUNDLED_PINNED_SOURCE] on raw.githubusercontent.com, serving the identical
         * content-addressed bytes (same checksum, both in the path segment and the query param).
         * Trust here is carried entirely by the checksum, not by which origin served the bytes —
         * this mirror is exactly as trustworthy as the gateway itself. It exists for networks
         * where the voting.valargroup.dev domain is blocked outright (the gateway's own
         * GitHub-outage fallback does not help if the gateway domain cannot be reached at all).
         * Bump alongside [BUNDLED_PINNED_SOURCE] whenever the checksum moves forward.
         */
        const val BUNDLED_PINNED_SOURCE_MIRROR =
            "https://raw.githubusercontent.com/valargroup/token-holder-voting-config/main/pins/prod/" +
                "28fc9b631091ae8bc2f8635d8930489238ce144174cbd15a03efb0530b301ebe/v2-static-voting-config.json" +
                "?checksum=sha256:28fc9b631091ae8bc2f8635d8930489238ce144174cbd15a03efb0530b301ebe"

        /**
         * The static-config trust anchor's full mirror list, canonical origin
         * ([BUNDLED_PINNED_SOURCE]) first. Walked in order when no override is configured (or
         * the configured override resolves to one of these entries), falling through to the
         * next mirror on transport failure, a non-200 response, or a checksum mismatch.
         */
        val BUNDLED_PINNED_SOURCES: List<String> = listOf(BUNDLED_PINNED_SOURCE, BUNDLED_PINNED_SOURCE_MIRROR)

        /** [BUNDLED_PINNED_SOURCES], pre-parsed. */
        val BUNDLED_PINNED_CONFIG_SOURCES: List<PinnedConfigSource> by lazy {
            BUNDLED_PINNED_SOURCES.map(PinnedConfigSource::parse)
        }

        /**
         * Per-attempt request timeout for both the static and dynamic config fetch legs: a
         * blackholed route must fail fast enough to fall through to the next mirror, rather
         * than hanging for the client's session-wide default (two minutes).
         */
        const val CONFIG_REQUEST_TIMEOUT_MS = 15_000L

        fun decodeAndVerify(data: ByteArray, expectedSHA256: ByteArray?): StaticVotingConfig {
            if (expectedSHA256 != null) {
                val actualSHA256 = MessageDigest.getInstance("SHA-256").digest(data)
                if (!actualSHA256.contentEquals(expectedSHA256)) {
                    throw StaticVotingConfigHashMismatchException(
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

/**
 * Thrown by [StaticVotingConfig.decodeAndVerify] specifically for a checksum mismatch against
 * the pinned expected hash, as distinct from a decode or [StaticVotingConfig.validate] failure.
 * A mirror walk (see `KtorVotingApiProvider`) must retry the next mirror on this exception but
 * treat every other [VotingConfigException] from the same call as authoritative — decode or
 * validation failing after the hash matched means the mirror served identical, malformed bytes,
 * and every other pinned mirror serves the same bytes by definition.
 */
class StaticVotingConfigHashMismatchException(
    message: String
) : VotingConfigException(message)

private fun requireHttpsScheme(
    url: String,
    fieldName: String
) {
    val scheme = runCatching { URI(url).scheme }.getOrNull()
    if (!scheme.equals("https", ignoreCase = true)) {
        throw VotingConfigException("$fieldName must use https; got $url")
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
    return chunked(2).map { chunk -> chunk.toInt(HEX_RADIX).toByte() }.toByteArray()
}

internal fun String.isLowercaseHex(): Boolean =
    isNotEmpty() && all { character -> character in '0'..'9' || character in 'a'..'f' }

internal fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff

private val staticVotingConfigJson =
    Json {
        ignoreUnknownKeys = true
    }
