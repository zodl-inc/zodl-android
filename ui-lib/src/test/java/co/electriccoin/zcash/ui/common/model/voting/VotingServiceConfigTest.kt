package co.electriccoin.zcash.ui.common.model.voting

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticVotingConfigTest {
    @Test
    fun pinnedConfigSourceParseAcceptsChecksumAndStripsIt() {
        val hex = "0a".repeat(32)

        val source =
            PinnedConfigSource.parse(
                "https://example.com/static-voting-config.json?foo=bar&checksum=sha256:$hex&baz=qux"
            )

        assertEquals("https://example.com/static-voting-config.json?foo=bar&baz=qux", source.url)
        assertEquals(32, source.sha256?.size)
        assertEquals(0x0a, source.sha256?.first()?.toInt())
    }

    @Test
    fun pinnedConfigSourceParseAcceptsMissingChecksum() {
        val source =
            PinnedConfigSource.parse(
                "https://example.com/static-voting-config.json?foo=bar"
            )

        assertEquals("https://example.com/static-voting-config.json?foo=bar", source.url)
        assertEquals(null, source.sha256)
    }

    @Test
    fun pinnedConfigSourceParseDoesNotDoubleEncodePreservedQuery() {
        val hex = "0a".repeat(32)

        val source =
            PinnedConfigSource.parse(
                "https://example.com/static-voting-config.json" +
                    "?redirect=https%3A%2F%2Fconfig.example%2Fa%3Fb%3Dc&checksum=sha256:$hex"
            )

        assertEquals(
            "https://example.com/static-voting-config.json?redirect=https%3A%2F%2Fconfig.example%2Fa%3Fb%3Dc",
            source.url
        )
    }

    @Test
    fun pinnedConfigSourceParseRejectsMalformedSources() {
        val validHex = "0a".repeat(32)
        val cases =
            listOf(
                "https://example.com/static-voting-config.json?checksum=sha512:$validHex",
                "https://example.com/static-voting-config.json?checksum",
                "https://example.com/static-voting-config.json?checksum=",
                "https://example.com/static-voting-config.json?checksum=sha256:${"0A".repeat(32)}",
                "https://example.com/static-voting-config.json?checksum=sha256:${"0g".repeat(32)}",
                "https://example.com/static-voting-config.json?checksum=sha256:${"0a".repeat(31)}",
                "http://example.com/static-voting-config.json?checksum=sha256:$validHex",
                "not a url?checksum=sha256:$validHex"
            )

        cases.forEach { raw ->
            assertFailsWith<VotingConfigException>(raw) {
                PinnedConfigSource.parse(raw)
            }
        }
    }

    @Test
    fun staticConfigDecodeAndVerifyAcceptsMatchingSHA256() {
        val data = makeStaticConfigJson().toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data)

        val config = StaticVotingConfig.decodeAndVerify(data = data, expectedSHA256 = sha256)

        assertEquals(1, config.staticConfigVersion)
        assertEquals("https://example.com/dynamic-voting-config.json", config.dynamicConfigURL)
        assertEquals(1, config.trustedKeys.size)
    }

    @Test
    fun staticConfigDecodeAndVerifySkipsHashCheckWhenChecksumIsMissing() {
        val data = makeStaticConfigJson().toByteArray(Charsets.UTF_8)

        val config = StaticVotingConfig.decodeAndVerify(data = data, expectedSHA256 = null)

        assertEquals(1, config.staticConfigVersion)
        assertEquals("https://example.com/dynamic-voting-config.json", config.dynamicConfigURL)
        assertEquals(1, config.trustedKeys.size)
    }

    @Test
    fun staticConfigDecodeAndVerifyRejectsHashMismatch() {
        val data = makeStaticConfigJson().toByteArray(Charsets.UTF_8)

        assertFailsWith<VotingConfigException> {
            StaticVotingConfig.decodeAndVerify(data = data, expectedSHA256 = ByteArray(32))
        }
    }

    @Test
    fun staticConfigValidationRejectsShortTrustedKey() {
        val data =
            makeStaticConfigJson(pubkey = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ==")
                .toByteArray(Charsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data)

        assertFailsWith<VotingConfigException> {
            StaticVotingConfig.decodeAndVerify(data = data, expectedSHA256 = sha256)
        }
    }

    private fun makeStaticConfigJson(
        pubkey: String = ADMIN_PUBKEY_BASE64
    ): String =
        """
        {
          "static_config_version": 1,
          "dynamic_config_url": "https://example.com/dynamic-voting-config.json",
          "trusted_keys": [
            {
              "key_id": "valar-test",
              "alg": "ed25519",
              "pubkey": "$pubkey"
            }
          ]
        }
        """.trimIndent()
}

class ZodlEndorsedRoundsResponseTest {
    @Test
    fun roundIdsHexDecodesBase64RoundIdsToLowercaseHex() {
        val roundIdBytes = ByteArray(32) { index -> index.toByte() }
        val encodedRoundId =
            java.util.Base64
                .getEncoder()
                .encodeToString(roundIdBytes)

        val response =
            ZodlEndorsedRoundsResponse(
                voteRoundIds =
                    listOf(
                        encodedRoundId,
                        "not-base64",
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(ByteArray(31))
                    )
            )

        assertEquals(
            setOf("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"),
            response.roundIdsHex()
        )
    }
}

class VotingServiceConfigTest {
    @Test
    fun decodeAcceptsSignedRoundsWithoutLegacyProposalFields() {
        val config =
            VotingServiceConfig.decode(
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
                  "rounds": {
                    "$ROUND_ID": {
                      "auth_version": 1,
                      "ea_pk": "$EA_PK_BASE64",
                      "signatures": [
                        {"key_id": "valar-test", "alg": "ed25519", "sig": "$ADMIN_SIGNATURE_BASE64"}
                      ]
                    }
                  }
                }
                """.trimIndent()
            )

        config.validate()

        assertEquals(setOf(ROUND_ID), config.rounds.keys)
        assertEquals(EA_PK_BASE64, config.rounds.getValue(ROUND_ID).eaPk)
    }

    @Test
    fun serviceConfigDropsOnlyRoundsWithoutValidSignatures() {
        val invalidRoundId = "b".repeat(64)
        val config =
            makeServiceConfig(
                rounds =
                    mapOf(
                        ROUND_ID to makeEntry(),
                        invalidRoundId to makeEntry(signature = ADMIN_SIGNATURE_BASE64.flipFirstBase64Byte())
                    )
            )

        val filtered = config.retainingRoundsWithValidSignatures(listOf(makeTrustedKey()))

        assertEquals(setOf(ROUND_ID), filtered.rounds.keys)
    }

    @Test
    fun serviceConfigValidateRejectsMixedCaseRoundId() {
        val config = makeServiceConfig(rounds = mapOf(ROUND_ID.uppercase() to makeEntry()))

        assertFailsWith<VotingConfigException> {
            config.validate()
        }
    }
}

class RoundAuthenticatorTest {
    @Test
    fun authenticateAcceptsFixtureFromDynamicConfig() {
        assertEquals(
            RoundAuthStatus.AUTHENTICATED,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry()),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun authenticateReportsMissingRound() {
        assertEquals(
            RoundAuthStatus.MISSING_ROUND,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = emptyMap(),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun authenticateReportsInvalidSignature() {
        assertEquals(
            RoundAuthStatus.INVALID_SIGNATURES,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry(signature = ADMIN_SIGNATURE_BASE64.flipFirstBase64Byte())),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun authenticateReportsUnknownAuthVersion() {
        assertEquals(
            RoundAuthStatus.UNKNOWN_AUTH_VERSION,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry(authVersion = 2)),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun authenticateReportsInvalidSignaturesWhenEntryEaPkIsShort() {
        val shortEaPk =
            java.util.Base64
                .getEncoder()
                .encodeToString(ByteArray(31) { 1 })

        assertEquals(
            RoundAuthStatus.INVALID_SIGNATURES,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry(eaPK = shortEaPk)),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun authenticateReportsEaPkMismatch() {
        val chainEaPK = EA_PK_BASE64.base64Bytes().also { it[0] = (it[0].toInt() xor 0xff).toByte() }

        assertEquals(
            RoundAuthStatus.EA_PK_MISMATCH,
            RoundAuthenticator.authenticate(
                chainEaPK = chainEaPK,
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry()),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun verifyEntrySignaturesRejectsMissingSignature() {
        assertFalse(
            RoundAuthenticator.verifyEntrySignatures(
                entry = makeEntry(signatures = emptyList()),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun verifyEntrySignaturesRejectsUnknownKeyId() {
        assertFalse(
            RoundAuthenticator.verifyEntrySignatures(
                entry = makeEntry(keyId = "unknown-key"),
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun verifyEntrySignaturesAcceptsWhenAnySignatureIsValid() {
        val entry =
            makeEntry(
                signatures =
                    listOf(
                        VotingServiceConfig.Signature(
                            keyId = "valar-test",
                            alg = "ed25519",
                            sig = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ=="
                        ),
                        VotingServiceConfig.Signature(
                            keyId = "valar-test",
                            alg = "ed25519",
                            sig = ADMIN_SIGNATURE_BASE64
                        )
                    )
            )

        assertTrue(RoundAuthenticator.verifyEntrySignatures(entry, listOf(makeTrustedKey())))
    }
}

private const val ROUND_ID = "58d9319ac86933b81769a7c0972444fa39212ad3790646398de6ce6534de2225"
private const val EA_PK_BASE64 = "N72oXeIF96QwWBtChaCwde3tjTt75ZfAs455V4usYwM="
private const val ADMIN_PUBKEY_BASE64 = "rKDbmhkoW9ja7dMiCV+1uTao7wXWV6xN/57erkrOuiQ="
private const val ADMIN_SIGNATURE_BASE64 =
    "rnll+KsHIFt73GpyNoWrX57dlcX8hTi8GU5X/xpwg3vcE+jCARUXpD7LsK+OLw6R5q1kU/zccwNgzsmclt4WAg=="

private fun makeServiceConfig(
    rounds: Map<String, VotingServiceConfig.RoundEntry>
): VotingServiceConfig =
    VotingServiceConfig(
        voteServers = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example.com", label = "vote")),
        pirEndpoints = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://pir.example.com", label = "pir")),
        supportedVersions =
            VotingServiceConfig.SupportedVersions(
                pir = listOf("v0"),
                voteProtocol = "v0",
                tally = "v0",
                voteServer = "v1"
            ),
        rounds = rounds
    )

private fun makeEntry(
    authVersion: Int = 1,
    eaPK: String = EA_PK_BASE64,
    keyId: String = "valar-test",
    signatureAlg: String = "ed25519",
    signature: String = ADMIN_SIGNATURE_BASE64,
    signatures: List<VotingServiceConfig.Signature> =
        listOf(
            VotingServiceConfig.Signature(
                keyId = keyId,
                alg = signatureAlg,
                sig = signature
            )
        )
): VotingServiceConfig.RoundEntry =
    VotingServiceConfig.RoundEntry(
        authVersion = authVersion,
        eaPk = eaPK,
        signatures = signatures
    )

private fun makeTrustedKey(): StaticVotingConfig.TrustedKey =
    StaticVotingConfig.TrustedKey(
        keyId = "valar-test",
        alg = "ed25519",
        pubkey = ADMIN_PUBKEY_BASE64
    )

private fun String.base64Bytes(): ByteArray =
    java.util.Base64
        .getDecoder()
        .decode(this)

private fun String.flipFirstBase64Byte(): String {
    val bytes = base64Bytes()
    bytes[0] = (bytes[0].toInt() xor 0xff).toByte()
    return java.util.Base64
        .getEncoder()
        .encodeToString(bytes)
}
