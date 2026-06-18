package co.electriccoin.zcash.ui.design.util

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private const val ZEC_MAXIMUM_FRACTION_DIGITS = 6
private const val ZEC_MINIMUM_FRACTION_DIGITS = 0
private const val ZATOSHI_MAXIMUM_FRACTION_DIGITS = 8
private const val ZATOSHI_MINIMUM_FRACTION_DIGITS = 3

fun zatoshiFormatter(): DecimalFormat =
    currencyFormatter(
        maximumFractionDigits = ZATOSHI_MAXIMUM_FRACTION_DIGITS,
        minimumFractionDigits = ZATOSHI_MINIMUM_FRACTION_DIGITS
    )

/**
 * Builds a [DecimalFormat] for monetary/number values. The locale is intentionally fixed to
 * [StringResource.NUMBER_FORMAT_LOCALE] so amounts always render with a period decimal separator and
 * comma grouping regardless of the device region/language. See MOB-1356 / MOB-1394.
 */
fun currencyFormatter(
    maximumFractionDigits: Int? = ZEC_MAXIMUM_FRACTION_DIGITS,
    minimumFractionDigits: Int? = ZEC_MINIMUM_FRACTION_DIGITS
): DecimalFormat {
    val locale = StringResource.NUMBER_FORMAT_LOCALE
    val symbols = DecimalFormatSymbols(locale)
    val pattern = (DecimalFormat.getInstance(locale) as? DecimalFormat)?.toPattern()

    return if (pattern != null) {
        DecimalFormat(pattern, symbols)
    } else {
        DecimalFormat().apply {
            decimalFormatSymbols = symbols
        }
    }.apply {
        roundingMode = RoundingMode.HALF_EVEN
        maximumFractionDigits?.let { this.maximumFractionDigits = it }
        minimumFractionDigits?.let { this.minimumFractionDigits = it }
    }
}
