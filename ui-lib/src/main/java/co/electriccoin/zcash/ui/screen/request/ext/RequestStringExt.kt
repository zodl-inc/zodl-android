package co.electriccoin.zcash.ui.screen.request.ext

import cash.z.ecc.sdk.extension.currencyFormatter
import java.math.BigDecimal
import java.text.ParseException
import java.util.Locale

internal fun String.toBigDecimalLocalized(locale: Locale): BigDecimal? =
    try {
        val currencyFormatter =
            currencyFormatter(
                locale = locale,
                maximumFractionDigits = null,
                minimumFractionDigits = null
            ).apply {
                isParseBigDecimal = true
            }
        currencyFormatter.parse(this) as BigDecimal
    } catch (_: ParseException) {
        null
    }
