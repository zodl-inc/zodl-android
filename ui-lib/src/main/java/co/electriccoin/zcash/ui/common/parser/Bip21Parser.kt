package co.electriccoin.zcash.ui.common.parser

import java.math.BigDecimal

internal object Bip21Parser {
    const val BITCOIN_CHAIN_TICKER = "btc"

    private const val SCHEME = "bitcoin:"
    private const val ADDRESS_PREFIX = "//"
    private const val QUERY_START = '?'
    private const val FRAGMENT_START = '#'
    private const val PARAMETER_SEPARATOR = '&'
    private const val VALUE_SEPARATOR = '='
    private const val AMOUNT_PARAMETER = "amount"
    private const val REQUIRED_PARAMETER_PREFIX = "req-"
    private val AMOUNT_PATTERN = Regex("""^\d+(\.\d{1,8})?$""")

    fun parse(raw: String): Bip21ParseResult {
        val trimmed = raw.trim()
        return if (trimmed.startsWith(SCHEME, ignoreCase = true)) {
            parseBitcoinUri(trimmed)
        } else {
            Bip21ParseResult.NotBitcoinUri
        }
    }

    private fun parseBitcoinUri(trimmed: String): Bip21ParseResult {
        val withoutFragment = trimmed.substring(SCHEME.length).substringBefore(FRAGMENT_START)
        val queryStart = withoutFragment.indexOf(QUERY_START)
        val addressPart =
            if (queryStart >= 0) {
                withoutFragment.substring(0, queryStart)
            } else {
                withoutFragment
            }
        val address = addressPart.trim().removePrefix(ADDRESS_PREFIX).trim()
        val query = if (queryStart >= 0) withoutFragment.substring(queryStart + 1) else ""
        val parameters = parseParameters(query)
        val amountValues = parameters.filter { it.name == AMOUNT_PARAMETER }.map { it.value }
        val amount = amountValues.singleOrNull()?.let { parseAmount(it) }
        val hasRequiredParameter =
            parameters.any { it.name.startsWith(REQUIRED_PARAMETER_PREFIX, ignoreCase = true) }
        val hasInvalidAmount = amountValues.isNotEmpty() && amount == null

        return when {
            address.isEmpty() -> Bip21ParseResult.Invalid
            hasRequiredParameter -> Bip21ParseResult.Invalid
            amountValues.size > 1 -> Bip21ParseResult.Invalid
            hasInvalidAmount -> Bip21ParseResult.Invalid
            else -> Bip21ParseResult.Valid(address = address, amount = amount)
        }
    }

    private fun parseParameters(query: String): List<QueryParameter> =
        query
            .split(PARAMETER_SEPARATOR)
            .filter(String::isNotEmpty)
            .map { parameter ->
                val valueStart = parameter.indexOf(VALUE_SEPARATOR)
                if (valueStart < 0) {
                    QueryParameter(name = parameter, value = null)
                } else {
                    QueryParameter(
                        name = parameter.substring(0, valueStart),
                        value = parameter.substring(valueStart + 1),
                    )
                }
            }

    private fun parseAmount(value: String?): BigDecimal? =
        value
            ?.takeIf(AMOUNT_PATTERN::matches)
            ?.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
}

internal sealed interface Bip21ParseResult {
    data object NotBitcoinUri : Bip21ParseResult

    data object Invalid : Bip21ParseResult

    data class Valid(
        val address: String,
        val amount: BigDecimal?,
    ) : Bip21ParseResult
}

private data class QueryParameter(
    val name: String,
    val value: String?,
)
