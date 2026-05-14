package cash.z.ecc.sdk.extension

import android.os.Build
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val ZEC_MAXIMUM_FRACTION_DIGITS = 6
private const val ZEC_MINIMUM_FRACTION_DIGITS = 0
private const val ZATOSHI_MAXIMUM_FRACTION_DIGITS = 8
private const val ZATOSHI_MINIMUM_FRACTION_DIGITS = 3

fun zatoshiFormatter(locale: Locale): DecimalFormat =
    currencyFormatter(
        locale = locale,
        maximumFractionDigits = ZATOSHI_MAXIMUM_FRACTION_DIGITS,
        minimumFractionDigits = ZATOSHI_MINIMUM_FRACTION_DIGITS
    )

fun Zatoshi.toZecString(locale: Locale): String =
    currencyFormatter(
        locale = locale,
        maximumFractionDigits = ZATOSHI_MAXIMUM_FRACTION_DIGITS,
        minimumFractionDigits = ZATOSHI_MAXIMUM_FRACTION_DIGITS
    ).format(convertZatoshiToZec(scale = ZATOSHI_MAXIMUM_FRACTION_DIGITS))

fun currencyFormatter(
    locale: Locale,
    maximumFractionDigits: Int? = ZEC_MAXIMUM_FRACTION_DIGITS,
    minimumFractionDigits: Int? = ZEC_MINIMUM_FRACTION_DIGITS
): DecimalFormat {
    val symbols = ZcashDecimalFormatSymbols(locale)
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

class ZcashDecimalFormatSymbols(
    locale: Locale
) : DecimalFormatSymbols(locale) {
    init {
        val originalDecimalSeparator = decimalSeparator
        val originalGroupingSeparator = groupingSeparator

        groupingSeparator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                monetaryGroupingSeparator
            } else {
                originalGroupingSeparator
            }

        decimalSeparator =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> monetaryDecimalSeparator
                originalGroupingSeparator == monetaryDecimalSeparator -> originalDecimalSeparator
                else -> monetaryDecimalSeparator
            }
    }
}
