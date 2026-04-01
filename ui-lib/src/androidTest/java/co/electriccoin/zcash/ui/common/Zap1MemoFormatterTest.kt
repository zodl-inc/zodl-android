package co.electriccoin.zcash.ui.common

import androidx.test.filters.SmallTest
import co.electriccoin.zcash.ui.common.util.Zap1MemoFormatter
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Zap1MemoFormatterTest {
    @Test
    @SmallTest
    fun parse_program_entry() {
        val attestation =
            Zap1MemoFormatter.parse(
                "ZAP1:01:075b00df286038a7b3f6bb70054df61343e3481fba579591354a00214e9e019b"
            )

        requireNotNull(attestation)
        assertThat(attestation.prefix, equalTo("ZAP1"))
        assertThat(attestation.typeHex, equalTo("01"))
        assertThat(attestation.event, equalTo("PROGRAM_ENTRY"))
        assertThat(attestation.shortHash, equalTo("075b00df2860..."))
        assertFalse(attestation.isLegacy)
    }

    @Test
    @SmallTest
    fun parse_legacy_nsm1() {
        val attestation =
            Zap1MemoFormatter.parse(
                "NSM1:04:f265b9a06a61b2b8c6eeed7fc00c7aa686ad511053467815bf1f1037d460e1f1"
            )

        requireNotNull(attestation)
        assertThat(attestation.prefix, equalTo("NSM1"))
        assertThat(attestation.event, equalTo("DEPLOYMENT"))
        assertTrue(attestation.isLegacy)
    }

    @Test
    @SmallTest
    fun parse_governance_uppercase_hex() {
        val attestation =
            Zap1MemoFormatter.parse(
                "ZAP1:0D:A487C25F5867A9E3760C45AE7EED24D84E771568F1826A889CCD94B3C7C3A5B5"
            )

        requireNotNull(attestation)
        assertThat(attestation.typeHex, equalTo("0d"))
        assertThat(attestation.event, equalTo("GOVERNANCE_PROPOSAL"))
    }

    @Test
    @SmallTest
    fun rejects_invalid_memos() {
        assertNull(Zap1MemoFormatter.parse("Hello world"))
        assertNull(Zap1MemoFormatter.parse("ZAP1:xx:notahash"))
        assertNull(Zap1MemoFormatter.parse("ZAP1:01:tooshort"))
        assertNull(Zap1MemoFormatter.parse(""))
        assertNull(
            Zap1MemoFormatter.parse(
                "ZAP2:01:075b00df286038a7b3f6bb70054df61343e3481fba579591354a00214e9e019b"
            )
        )
    }

    @Test
    @SmallTest
    fun format_returns_readable_string() {
        val formatted =
            Zap1MemoFormatter.format(
                "ZAP1:09:024e36515ea30efc15a0a7962dd8f677455938079430b9eab174f46a4328a07a"
            )

        requireNotNull(formatted)
        assertThat(formatted, equalTo("ZAP1: MERKLE_ROOT  024e36515ea3..."))
    }

    @Test
    @SmallTest
    fun format_returns_null_for_non_zap1() {
        assertNull(Zap1MemoFormatter.format("Just a regular memo"))
    }
}
