package co.electriccoin.zcash.ui.common.model.voting

import com.google.crypto.tink.subtle.Ed25519Sign
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
                  "pir_layout": {"pir_depth": 19, "tier0_layers": 12, "tier1_layers": 7, "poly_len": 4096},
                  "supported_versions": {
                    "pir": ["v0"],
                    "vote_protocol": "v0",
                    "tally": "v0",
                    "vote_server": "v1"
                  },
                  "rounds": {
                    "$ROUND_ID": {
                      "auth_version": 2,
                      "ea_pk": "$EA_PK_BASE64",
                      "signatures": [
                        {"key_id": "valar-test", "alg": "ed25519", "sig": "${validSignatureBase64()}"}
                      ]
                    }
                  }
                }
                """.trimIndent()
            )

        config.validate()

        assertEquals(setOf(ROUND_ID), config.rounds.keys)
        assertEquals(EA_PK_BASE64, config.rounds.getValue(ROUND_ID).eaPk)
        assertEquals(PIR_LAYOUT_FIXTURE, config.pirLayout)
    }

    @Test
    fun decodeDefaultsPolyLenToZeroWhenAbsent() {
        val config =
            VotingServiceConfig.decode(
                """
                {
                  "config_version": 1,
                  "vote_servers": [{"url": "https://vote.example.com", "label": "vote"}],
                  "pir_endpoints": [{"url": "https://pir.example.com", "label": "pir"}],
                  "pir_layout": {"pir_depth": 19, "tier0_layers": 12, "tier1_layers": 7},
                  "supported_versions": {
                    "pir": ["v0"],
                    "vote_protocol": "v0",
                    "tally": "v0",
                    "vote_server": "v1"
                  },
                  "rounds": {}
                }
                """.trimIndent()
            )

        assertEquals(0, config.pirLayout.polyLen)
    }

    @Test
    fun serviceConfigDropsOnlyRoundsWithoutValidSignatures() {
        val otherRoundId = "0".repeat(63) + "2"
        val v1RoundId = "b".repeat(64)
        val config =
            makeServiceConfig(
                rounds =
                    mapOf(
                        ROUND_ID to makeEntry(),
                        otherRoundId to makeEntry(),
                        v1RoundId to makeLegacyV1Entry()
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
    /**
     * Golden vector from zcash_voting 3.0.0-rc.2
     * (`dynamic_resolution_accepts_vote_sdk_ui_signed_round_entry`, config/mod.rs): a
     * vote-sdk admin-UI / Keplr-derived key signing the poly_len-bound v2 preimage (tag ||
     * round_id || ea_pk || 19/12/7/4096). Passing pins byte-for-byte compatibility with the
     * crate's verifier.
     */
    @Test
    fun authenticateAcceptsCrateGoldenVector() {
        val goldenRoundId = "06aae723e42cf615d174f338e8f30a72d2bf3275eb9d9e835cc894f197904b20"
        val goldenKeyId = "keplr:sv1mqts0klc9768rns9h2ykeaka5tve6ts39c2zu3"
        val goldenEaPkBase64 = "GpYa1sCGIMe2bp1O9UgrThrwkCdxu6oHDmhoBTw6EZ8="
        val goldenPubkeyBase64 = "NDygCpG+Y4T4uu8M1Sb/YG+74lUVj9XgYypUoMQMXT8="
        val goldenSigBase64 =
            "RHbpnj2a1VA+wadIQT3JM/r6ADH11VeA8UgT5dhwhixMcS5Bw5ispndM/ZYH/d2vxNBxTRtZwnLyXZjxcVD+Dg=="
        val entry =
            VotingServiceConfig.RoundEntry(
                authVersion = RoundAuthenticator.AUTH_VERSION_V2,
                eaPk = goldenEaPkBase64,
                signatures =
                    listOf(
                        VotingServiceConfig.Signature(keyId = goldenKeyId, alg = "ed25519", sig = goldenSigBase64)
                    )
            )
        val trustedKey =
            StaticVotingConfig.TrustedKey(keyId = goldenKeyId, alg = "ed25519", pubkey = goldenPubkeyBase64)

        val status =
            RoundAuthenticator.authenticate(
                chainEaPK = goldenEaPkBase64.base64Bytes(),
                roundIdHex = goldenRoundId,
                rounds = mapOf(goldenRoundId to entry),
                trustedKeys = listOf(trustedKey),
                pirLayout = PIR_LAYOUT_FIXTURE
            )

        assertEquals(RoundAuthStatus.AUTHENTICATED, status)
    }

    /**
     * Golden byte layout from zcash_voting 3.0.0-rc.2
     * (`encoding_matches_round_auth_v2_wire_format`, round_auth.rs): round_id [1u8; 32],
     * ea_pk [2u8; 32], layout 19/12/7/4096, every u32 little-endian.
     */
    @Test
    fun signingPayloadV2MatchesCrateWireFormat() {
        val payload =
            RoundAuthenticator.signingPayloadV2(
                roundIdHex = "01".repeat(32),
                eaPk = ByteArray(32) { 0x02 },
                pirLayout = PIR_LAYOUT_FIXTURE
            )

        val expected =
            "zcash-shielded-vote:round-auth:v2".toByteArray(Charsets.US_ASCII) +
                ByteArray(32) { 0x01 } +
                ByteArray(32) { 0x02 } +
                byteArrayOf(19, 0, 0, 0) +
                byteArrayOf(12, 0, 0, 0) +
                byteArrayOf(7, 0, 0, 0) +
                byteArrayOf(0x00, 0x10, 0x00, 0x00)

        assertEquals(113, payload?.size)
        assertTrue(payload.contentEquals(expected))
    }

    @Test
    fun authenticateAcceptsV2EntrySignedOverV2Payload() {
        assertEquals(
            RoundAuthStatus.AUTHENTICATED,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry()),
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
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
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
            )
        )
    }

    /**
     * v1 policy pin (MOB-1678, deliberate + overturnable — see [RoundAuthenticator]'s doc
     * comment): a *valid* v1-style signature over the raw `ea_pk` must no longer
     * authenticate, matching the crate's own dynamic-resolution behavior.
     */
    @Test
    fun authenticateRejectsLegacyV1Entry() {
        assertEquals(
            RoundAuthStatus.UNKNOWN_AUTH_VERSION,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeLegacyV1Entry()),
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
            )
        )
    }

    @Test
    fun authenticateReportsInvalidSignatures() {
        assertEquals(
            RoundAuthStatus.INVALID_SIGNATURES,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to makeEntry(signature = validSignatureBase64().flipFirstBase64Byte())),
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
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
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
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
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
            )
        )
    }

    /**
     * The v2 signature binds the round id: replaying an entry signed for one round under a
     * different key in the `rounds` map must not authenticate.
     */
    @Test
    fun authenticateRejectsEntryReplayedUnderDifferentRoundId() {
        val otherRoundId = "0".repeat(63) + "2"
        val entry = makeEntry()

        assertEquals(
            RoundAuthStatus.INVALID_SIGNATURES,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = otherRoundId,
                rounds = mapOf(otherRoundId to entry),
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = PIR_LAYOUT_FIXTURE
            )
        )
    }

    /**
     * The v2 signature binds the full PIR layout: a config host swapping poly_len (or any
     * tier) after signing invalidates the attestation.
     */
    @Test
    fun authenticateRejectsWhenPolyLenChangedAfterSigning() {
        val entry = makeEntry()
        val swappedLayout = PIR_LAYOUT_FIXTURE.copy(polyLen = 2048)

        assertEquals(
            RoundAuthStatus.INVALID_SIGNATURES,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to entry),
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = swappedLayout
            )
        )
    }

    /**
     * A config that predates `pir_layout.poly_len` cannot carry v2 attestations: the
     * payload is unconstructible, so nothing authenticates (fail closed).
     */
    @Test
    fun authenticateRejectsWhenConfigLacksPolyLen() {
        val entry = makeEntry()
        val preBumpLayout = PIR_LAYOUT_FIXTURE.copy(polyLen = 0)

        assertEquals(
            RoundAuthStatus.INVALID_SIGNATURES,
            RoundAuthenticator.authenticate(
                chainEaPK = EA_PK_BASE64.base64Bytes(),
                roundIdHex = ROUND_ID,
                rounds = mapOf(ROUND_ID to entry),
                trustedKeys = listOf(makeTrustedKey()),
                pirLayout = preBumpLayout
            )
        )
    }

    /**
     * Crate parity: a round id that does not strict-hex-decode to exactly 32 bytes can never
     * authenticate.
     */
    @Test
    fun authenticateRejectsRoundIdsThatDoNotDecodeTo32Bytes() {
        val thirtyOneByteId = "ab".repeat(31)
        val nonHexId = "zz".repeat(32)

        listOf(thirtyOneByteId, nonHexId).forEach { badId ->
            val entry = makeEntry(roundIdHex = badId)

            assertEquals(
                RoundAuthStatus.INVALID_SIGNATURES,
                RoundAuthenticator.authenticate(
                    chainEaPK = EA_PK_BASE64.base64Bytes(),
                    roundIdHex = badId,
                    rounds = mapOf(badId to entry),
                    trustedKeys = listOf(makeTrustedKey()),
                    pirLayout = PIR_LAYOUT_FIXTURE
                ),
                "round id $badId must never authenticate"
            )
        }
    }

    @Test
    fun verifyEntrySignaturesRejectsMissingSignature() {
        assertFalse(
            RoundAuthenticator.verifyEntrySignatures(
                entry = makeEntry(signatures = emptyList()),
                roundIdHex = ROUND_ID,
                pirLayout = PIR_LAYOUT_FIXTURE,
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun verifyEntrySignaturesRejectsUnknownKeyId() {
        assertFalse(
            RoundAuthenticator.verifyEntrySignatures(
                entry = makeEntry(keyId = "unknown-key"),
                roundIdHex = ROUND_ID,
                pirLayout = PIR_LAYOUT_FIXTURE,
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
                            sig = ALTERNATE_INVALID_SIGNATURE_BASE64
                        ),
                        VotingServiceConfig.Signature(
                            keyId = "valar-test",
                            alg = "ed25519",
                            sig = validSignatureBase64()
                        )
                    )
            )

        assertTrue(
            RoundAuthenticator.verifyEntrySignatures(
                entry = entry,
                roundIdHex = ROUND_ID,
                pirLayout = PIR_LAYOUT_FIXTURE,
                trustedKeys = listOf(makeTrustedKey())
            )
        )
    }

    @Test
    fun signingPayloadV2ReturnsNullForUndecodableRoundId() {
        assertNull(
            RoundAuthenticator.signingPayloadV2(
                roundIdHex = "zz".repeat(32),
                eaPk = EA_PK_BASE64.base64Bytes(),
                pirLayout = PIR_LAYOUT_FIXTURE
            )
        )
    }

    @Test
    fun signingPayloadV2ReturnsNullForMissingPolyLen() {
        assertNull(
            RoundAuthenticator.signingPayloadV2(
                roundIdHex = ROUND_ID,
                eaPk = EA_PK_BASE64.base64Bytes(),
                pirLayout = PIR_LAYOUT_FIXTURE.copy(polyLen = 0)
            )
        )
    }
}

/** Layout mirroring the crate's `test_pir_layout()` (config/mod.rs tests): 19/12/7/4096. */
private val PIR_LAYOUT_FIXTURE = VotingPirLayout(pirDepth = 19, tier0Layers = 12, tier1Layers = 7, polyLen = 4096)

private const val ROUND_ID = "58d9319ac86933b81769a7c0972444fa39212ad3790646398de6ce6534de2225"
private const val EA_PK_BASE64 = "N72oXeIF96QwWBtChaCwde3tjTt75ZfAs455V4usYwM="
private const val ALTERNATE_INVALID_SIGNATURE_BASE64 =
    "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ=="

/**
 * Fresh per-test-run admin key; entries are signed the way the crate's own tests sign
 * theirs: ed25519 over the constructed v2 payload (round_auth.rs test recipe).
 */
private val adminKeyPair: Ed25519Sign.KeyPair by lazy { Ed25519Sign.KeyPair.newKeyPair() }
private val adminSigner: Ed25519Sign by lazy { Ed25519Sign(adminKeyPair.privateKey) }

private fun validSignatureBase64(): String {
    val payload =
        RoundAuthenticator.signingPayloadV2(
            roundIdHex = ROUND_ID,
            eaPk = EA_PK_BASE64.base64Bytes(),
            pirLayout = PIR_LAYOUT_FIXTURE
        ) ?: error("PIR_LAYOUT_FIXTURE must produce a signable v2 payload")
    return adminSigner.sign(payload).base64String()
}

private fun makeServiceConfig(
    rounds: Map<String, VotingServiceConfig.RoundEntry>,
    pirLayout: VotingPirLayout = PIR_LAYOUT_FIXTURE
): VotingServiceConfig =
    VotingServiceConfig(
        voteServers = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://vote.example.com", label = "vote")),
        pirEndpoints = listOf(VotingServiceConfig.ServiceEndpoint(url = "https://pir.example.com", label = "pir")),
        pirLayout = pirLayout,
        supportedVersions =
            VotingServiceConfig.SupportedVersions(
                pir = listOf("v0"),
                voteProtocol = "v0",
                tally = "v0",
                voteServer = "v1"
            ),
        rounds = rounds
    )

/**
 * Builds a registry entry signed exactly the way the crate's tests sign theirs: ed25519 over
 * [RoundAuthenticator.signingPayloadV2] for [roundIdHex], [eaPK], and [PIR_LAYOUT_FIXTURE].
 * For undecodable round ids the canonical payload cannot exist; a stand-in message is signed
 * instead so the entry still carries a well-formed 64-byte signature for the verifier to
 * refuse.
 */
private fun makeEntry(
    roundIdHex: String = ROUND_ID,
    eaPK: String = EA_PK_BASE64,
    keyId: String = "valar-test",
    signatureAlg: String = "ed25519",
    signature: String? = null,
    signatures: List<VotingServiceConfig.Signature>? = null
): VotingServiceConfig.RoundEntry {
    val resolvedSignature =
        signature
            ?: run {
                val payload =
                    RoundAuthenticator.signingPayloadV2(
                        roundIdHex = roundIdHex,
                        eaPk = eaPK.base64Bytes(),
                        pirLayout = PIR_LAYOUT_FIXTURE
                    ) ?: "undecodable-round-id-stand-in".toByteArray(Charsets.UTF_8)
                adminSigner.sign(payload).base64String()
            }
    return VotingServiceConfig.RoundEntry(
        authVersion = RoundAuthenticator.AUTH_VERSION_V2,
        eaPk = eaPK,
        signatures =
            signatures ?: listOf(
                VotingServiceConfig.Signature(
                    keyId = keyId,
                    alg = signatureAlg,
                    sig = resolvedSignature
                )
            )
    )
}

/** A legacy v1 entry: a valid signature over the raw 32-byte `ea_pk`, nothing else. */
private fun makeLegacyV1Entry(
    eaPK: String = EA_PK_BASE64,
    keyId: String = "valar-test"
): VotingServiceConfig.RoundEntry =
    VotingServiceConfig.RoundEntry(
        authVersion = 1,
        eaPk = eaPK,
        signatures =
            listOf(
                VotingServiceConfig.Signature(
                    keyId = keyId,
                    alg = "ed25519",
                    sig = adminSigner.sign(eaPK.base64Bytes()).base64String()
                )
            )
    )

private fun makeTrustedKey(): StaticVotingConfig.TrustedKey =
    StaticVotingConfig.TrustedKey(
        keyId = "valar-test",
        alg = "ed25519",
        pubkey = adminKeyPair.publicKey.base64String()
    )

private val ADMIN_PUBKEY_BASE64: String
    get() = adminKeyPair.publicKey.base64String()

private fun String.base64Bytes(): ByteArray =
    java.util.Base64
        .getDecoder()
        .decode(this)

private fun ByteArray.base64String(): String =
    java.util.Base64
        .getEncoder()
        .encodeToString(this)

private fun String.flipFirstBase64Byte(): String {
    val bytes = base64Bytes()
    bytes[0] = (bytes[0].toInt() xor 0xff).toByte()
    return bytes.base64String()
}
