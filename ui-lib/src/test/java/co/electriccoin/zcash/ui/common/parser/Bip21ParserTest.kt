package co.electriccoin.zcash.ui.common.parser

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class Bip21ParserTest {
    @Test
    fun parsesAddressAndAmount() {
        val result = valid("bitcoin:$ADDRESS?amount=0.001")

        assertEquals(ADDRESS, result.address)
        assertEquals(0, BigDecimal("0.001").compareTo(result.amount))
    }

    @Test
    fun parsesAddressWithoutAmount() {
        val result = valid("bitcoin:$ADDRESS")

        assertEquals(ADDRESS, result.address)
        assertNull(result.amount)
    }

    @Test
    fun acceptsUppercaseScheme() {
        val result = valid("BITCOIN:$ADDRESS?amount=1")

        assertEquals(ADDRESS, result.address)
        assertEquals(0, BigDecimal.ONE.compareTo(result.amount))
    }

    @Test
    fun acceptsDoubleSlashAfterScheme() {
        val result = valid("bitcoin://$ADDRESS?amount=1")

        assertEquals(ADDRESS, result.address)
        assertEquals(0, BigDecimal.ONE.compareTo(result.amount))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        val result = valid("  \nbitcoin:$ADDRESS?amount=1\t ")

        assertEquals(ADDRESS, result.address)
        assertEquals(0, BigDecimal.ONE.compareTo(result.amount))
    }

    @Test
    fun stripsFragment() {
        val result = valid("bitcoin:$ADDRESS?amount=1#section")

        assertEquals(ADDRESS, result.address)
        assertEquals(0, BigDecimal.ONE.compareTo(result.amount))
    }

    @Test
    fun ignoresLabelMessageAndUnknownOptionalParameters() {
        val result =
            valid(
                "bitcoin:$ADDRESS?label=Shop&message=Thanks&lightning=lnbc1&custom=value&amount=0.001"
            )

        assertEquals(ADDRESS, result.address)
        assertEquals(0, BigDecimal("0.001").compareTo(result.amount))
    }

    @Test
    fun parsesAmountAfterOtherParameters() {
        val result = valid("bitcoin:$ADDRESS?label=Shop&amount=2")

        assertEquals(0, BigDecimal("2").compareTo(result.amount))
    }

    @Test
    fun acceptsExactlyEightDecimalPlaces() {
        val result = valid("bitcoin:$ADDRESS?amount=0.00000001")

        assertEquals(0, BigDecimal("0.00000001").compareTo(result.amount))
    }

    @Test
    fun rejectsInvalidAmounts() {
        listOf(
            "0",
            "-1",
            "+1",
            "1e-3",
            ".1",
            "1.",
            "0.123456789",
            "",
            "abc",
            "%30.1",
        ).forEach { amount ->
            assertInvalid("bitcoin:$ADDRESS?amount=$amount")
        }
    }

    @Test
    fun rejectsDuplicateAmount() {
        assertInvalid("bitcoin:$ADDRESS?amount=1&amount=2")
    }

    @Test
    fun rejectsAmountWithoutValue() {
        assertInvalid("bitcoin:$ADDRESS?amount")
    }

    @Test
    fun rejectsUnknownRequiredParameter() {
        assertInvalid("bitcoin:$ADDRESS?req-timeout=60")
    }

    @Test
    fun rejectsMissingAddress() {
        assertInvalid("bitcoin:?amount=1")
        assertInvalid("bitcoin://")
    }

    @Test
    fun treatsOtherSchemesAsNotBitcoin() {
        assertNotBitcoin("zcash:$ADDRESS?amount=1")
        assertNotBitcoin("ethereum:$ADDRESS?amount=1")
    }

    @Test
    fun treatsPlainAddressAsNotBitcoin() {
        assertNotBitcoin(ADDRESS)
    }

    private fun valid(raw: String): Bip21ParseResult.Valid =
        assertIs<Bip21ParseResult.Valid>(Bip21Parser.parse(raw), raw)

    private fun assertInvalid(raw: String) {
        assertIs<Bip21ParseResult.Invalid>(Bip21Parser.parse(raw), raw)
    }

    private fun assertNotBitcoin(raw: String) {
        assertIs<Bip21ParseResult.NotBitcoinUri>(Bip21Parser.parse(raw), raw)
    }

    private companion object {
        const val ADDRESS = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"
    }
}
