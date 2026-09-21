package co.electriccoin.zcash.ui.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ZapAttestationMemoTest {
    @Test
    fun `parses the canonical root memo from issue 2172`() {
        val result = assertNotNull(ZapAttestationMemo.parse("ZAP1:09:$VALID_HASH"))

        assertEquals(ZapAttestationMemo.Protocol.ZAP1, result.protocol)
        assertEquals("09", result.eventType)
        assertEquals("Merkle root", result.eventLabel)
        assertEquals(VALID_HASH, result.payloadHash)
    }

    @Test
    fun `parses a legacy NSM1 memo with the same wire format`() {
        val result = assertNotNull(ZapAttestationMemo.parse("NSM1:04:$VALID_HASH"))

        assertEquals(ZapAttestationMemo.Protocol.NSM1, result.protocol)
        assertEquals("04", result.eventType)
        assertEquals("Deployment", result.eventLabel)
        assertEquals(VALID_HASH, result.payloadHash)
    }

    @Test
    fun `tolerates surrounding whitespace as specified by the issue parser`() {
        val result = assertNotNull(ZapAttestationMemo.parse(" \tZAP1:09:$VALID_HASH \r\n"))

        assertEquals("09", result.eventType)
        assertEquals(VALID_HASH, result.payloadHash)
    }

    @Test
    fun `labels every assigned type in the v3 registry`() {
        val expected =
            mapOf(
                "01" to "Program entry",
                "02" to "Ownership attestation",
                "03" to "Contract anchor",
                "04" to "Deployment",
                "05" to "Hosting payment",
                "06" to "Shield renewal",
                "07" to "Transfer",
                "08" to "Exit",
                "09" to "Merkle root",
                "0a" to "Staking deposit",
                "0b" to "Staking withdrawal",
                "0c" to "Staking reward",
                "0d" to "Governance proposal",
                "0e" to "Governance vote",
                "0f" to "Governance result",
                "40" to "Agent register",
                "41" to "Agent policy",
                "42" to "Agent action",
            )

        for (prefix in listOf("ZAP1", "NSM1")) {
            for ((type, label) in expected) {
                val result = assertNotNull(ZapAttestationMemo.parse("$prefix:$type:$VALID_HASH"))
                assertEquals(type, result.eventType)
                assertEquals(label, result.eventLabel)
            }
        }
    }

    @Test
    fun `unknown byte values remain visibly unknown`() {
        for (type in listOf("00", "10", "3f", "43", "ff")) {
            val result = assertNotNull(ZapAttestationMemo.parse("ZAP1:$type:$VALID_HASH"))

            assertEquals(type, result.eventType)
            assertEquals("Unknown event (0x$type)", result.eventLabel)
        }
    }

    @Test
    fun `rejects event names and malformed type bytes`() {
        for (type in listOf("AGENT_ACTION", "MERKLE_ROOT", "9", "009", "0A", "GG", "0x09", " 09", "09 ", "")) {
            assertNull(ZapAttestationMemo.parse("ZAP1:$type:$VALID_HASH"), type)
        }
    }

    @Test
    fun `requires exactly 64 lowercase hexadecimal hash characters`() {
        for (length in listOf(0, 8, 32, 63, 65, 128)) {
            assertNull(ZapAttestationMemo.parse("ZAP1:09:${"a".repeat(length)}"), "hash length $length")
        }
        assertNull(ZapAttestationMemo.parse("ZAP1:09:${VALID_HASH.uppercase()}"))
        assertNull(ZapAttestationMemo.parse("ZAP1:09:${"g".repeat(64)}"))
        assertNull(ZapAttestationMemo.parse("ZAP1:09:${VALID_HASH.dropLast(1)}\u0000"))
    }

    @Test
    fun `rejects plain text and embedded markers`() {
        for (memo in listOf("Thanks for lunch", "", "See ZAP1:09:$VALID_HASH", "ZAP1:09:$VALID_HASH trailing text")) {
            assertNull(ZapAttestationMemo.parse(memo))
        }
    }

    @Test
    fun `rejects missing and extra fields`() {
        assertNull(ZapAttestationMemo.parse("ZAP1:09"))
        assertNull(ZapAttestationMemo.parse("ZAP1:09:$VALID_HASH:extra"))
        assertNull(ZapAttestationMemo.parse("ZAP1::$VALID_HASH"))
        assertNull(ZapAttestationMemo.parse("ZAP1:09::$VALID_HASH"))
    }

    @Test
    fun `rejects other or wrongly cased prefixes`() {
        for (prefix in listOf("ZAP2", "NSM2", "zap1", "nsm1", "")) {
            assertNull(ZapAttestationMemo.parse("$prefix:09:$VALID_HASH"))
        }
    }

    private companion object {
        const val VALID_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
