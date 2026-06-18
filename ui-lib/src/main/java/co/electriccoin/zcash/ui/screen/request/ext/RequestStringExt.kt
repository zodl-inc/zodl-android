package co.electriccoin.zcash.ui.screen.request.ext

import co.electriccoin.zcash.ui.design.util.currencyFormatter
import java.math.BigDecimal
import java.text.ParseException

internal fun String.toBigDecimalLocalized(): BigDecimal? =
    try {
        val currencyFormatter =
            currencyFormatter(
                maximumFractionDigits = null,
                minimumFractionDigits = null
            ).apply {
                isParseBigDecimal = true
            }
        currencyFormatter.parse(this) as BigDecimal
    } catch (_: ParseException) {
        null
    }
