@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.design.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalContext
import cash.z.ecc.android.sdk.ext.convertZatoshiToZecString
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Immutable
sealed interface StringResource {
    @Immutable
    data class ByResource(
        @StringRes val resource: Int,
        val args: List<Any>
    ) : StringResource

    @JvmInline
    @Immutable
    value class ByString(
        val value: String
    ) : StringResource

    @Immutable
    data class ByZatoshi(
        val zatoshi: Zatoshi,
        val tickerLocation: TickerLocation
    ) : StringResource

    @Immutable
    data class ByDateTime(
        val zonedDateTime: ZonedDateTime,
        val useFullFormat: Boolean
    ) : StringResource

    @Immutable
    data class ByYearMonth(
        val yearMonth: YearMonth
    ) : StringResource

    @Immutable
    data class ByTransactionId(
        val transactionId: String,
        val abbreviated: Boolean
    ) : StringResource

    @Immutable
    data class ByAddress(
        val address: String,
        val middle: Boolean
    ) : StringResource

    @Immutable
    data class ByCurrencyNumber(
        val amount: Number,
        val ticker: String,
        val tickerLocation: TickerLocation,
        val minDecimals: Int
    ) : StringResource

    @Immutable
    data class ByDynamicCurrencyNumber(
        val amount: Number,
        val ticker: String,
        val includeDecimalSeparator: Boolean,
        val tickerLocation: TickerLocation
    ) : StringResource

    @Immutable
    data class ByNumber(
        val number: Number,
        val minDecimals: Int
    ) : StringResource

    @Immutable
    data class ByDynamicNumber(
        val number: Number,
        val includeDecimalSeparator: Boolean
    ) : StringResource

    operator fun plus(other: StringResource): StringResource = CompositeStringResource(listOf(this, other))

    operator fun plus(other: String): StringResource = CompositeStringResource(listOf(this, stringRes(other)))

    fun isEmpty(): Boolean =
        when (val obj = this) {
            is ByString -> obj.value.isEmpty()
            else -> false
        }
}

@Immutable
private data class CompositeStringResource(
    val resources: List<StringResource>
) : StringResource

@Stable
fun stringRes(
    @StringRes resource: Int,
    vararg args: Any
): StringResource =
    StringResource.ByResource(resource, args.toList())

@Stable
fun stringRes(value: String): StringResource =
    StringResource.ByString(value)

@Stable
fun stringRes(zatoshi: Zatoshi, tickerLocation: TickerLocation = TickerLocation.AFTER): StringResource =
    StringResource.ByZatoshi(zatoshi, tickerLocation)

@Stable
fun stringResByDynamicCurrencyNumber(
    amount: Number,
    ticker: String,
    tickerLocation: TickerLocation =
        if (ticker == FiatCurrency.USD.symbol) TickerLocation.BEFORE else TickerLocation.AFTER,
    includeDecimalSeparator: Boolean = true
): StringResource =
    StringResource.ByDynamicCurrencyNumber(
        amount = amount,
        ticker = ticker,
        includeDecimalSeparator = includeDecimalSeparator,
        tickerLocation = tickerLocation
    )

@Stable
fun stringResByCurrencyNumber(
    amount: Number,
    ticker: String,
    tickerLocation: TickerLocation =
        if (ticker == FiatCurrency.USD.symbol) TickerLocation.BEFORE else TickerLocation.AFTER,
    minDecimals: Int = 2
): StringResource =
    StringResource.ByCurrencyNumber(
        amount = amount,
        ticker = ticker,
        tickerLocation = tickerLocation,
        minDecimals = minDecimals
    )

@Stable
fun stringResByDateTime(zonedDateTime: ZonedDateTime, useFullFormat: Boolean): StringResource =
    StringResource.ByDateTime(zonedDateTime, useFullFormat)

@Stable
fun stringRes(yearMonth: YearMonth): StringResource =
    StringResource.ByYearMonth(yearMonth)

@Stable
fun stringResByAddress(value: String, middle: Boolean = false): StringResource =
    StringResource.ByAddress(value, middle)

@Stable
fun stringResByTransactionId(value: String, abbreviated: Boolean): StringResource =
    StringResource.ByTransactionId(value, abbreviated)

@Stable
fun stringResByNumber(number: Number, minDecimals: Int = 2): StringResource =
    StringResource.ByNumber(number, minDecimals)

@Stable
fun stringResByDynamicNumber(number: Number, includeDecimalSeparator: Boolean = true): StringResource =
    StringResource.ByDynamicNumber(number, includeDecimalSeparator)

@Stable
@Composable
fun StringResource.getValue(): String =
    getString(
        context = LocalContext.current,
        locale = rememberDesiredFormatLocale()
    )

fun StringResource.getString(
    context: Context,
    locale: Locale = context.resources.configuration.getPreferredLocale()
): String =
    when (this) {
        is StringResource.ByResource -> convertResource(context)
        is StringResource.ByString -> value
        is StringResource.ByZatoshi -> convertZatoshi()
        is StringResource.ByCurrencyNumber -> convertCurrencyNumber(locale)
        is StringResource.ByDynamicCurrencyNumber -> convertDynamicCurrencyNumber(locale)
        is StringResource.ByDateTime -> convertDateTime(locale)
        is StringResource.ByYearMonth -> convertYearMonth(locale)
        is StringResource.ByAddress -> convertAddress()
        is StringResource.ByTransactionId -> convertTransactionId()
        is StringResource.ByNumber -> convertNumber(locale)
        is StringResource.ByDynamicNumber -> convertDynamicNumber(locale)
        is CompositeStringResource -> convertComposite(context, locale)
    }

private fun CompositeStringResource.convertComposite(
    context: Context,
    locale: Locale
) = this.resources.joinToString(separator = "") { it.getString(context, locale) }

@Suppress("SpreadOperator")
private fun StringResource.ByResource.convertResource(context: Context) =
    context.getString(
        resource,
        *args.map { if (it is StringResource) it.getString(context) else it }.toTypedArray()
    )

private fun StringResource.ByNumber.convertNumber(locale: Locale): String =
    convertNumberToString(number, locale, minDecimals)

private fun StringResource.ByZatoshi.convertZatoshi(): String {
    val amount = this.zatoshi.convertZatoshiToZecString(maxDecimals = 8, minDecimals = 3)
    return when (this.tickerLocation) {
        TickerLocation.BEFORE -> "ZEC $amount"
        TickerLocation.AFTER -> "$amount ZEC"
        TickerLocation.HIDDEN -> amount
    }
}

private fun StringResource.ByCurrencyNumber.convertCurrencyNumber(locale: Locale): String {
    val amount = convertNumberToString(amount, locale, minDecimals)
    return when (this.tickerLocation) {
        TickerLocation.BEFORE -> "$ticker$amount"
        TickerLocation.AFTER -> "$amount $ticker"
        TickerLocation.HIDDEN -> amount
    }
}

private fun convertNumberToString(amount: Number, locale: Locale, minDecimals: Int): String {
    val bigDecimalAmount = amount.toBigDecimal().stripTrailingZeros()
    val maxFractionDigits = bigDecimalAmount.scale().coerceAtLeast(minDecimals)
    val formatter =
        NumberFormat.getInstance(locale).apply {
            roundingMode = RoundingMode.HALF_EVEN
            maximumFractionDigits = maxFractionDigits
            minimumFractionDigits = minDecimals
            minimumIntegerDigits = 1
        }
    return formatter.format(bigDecimalAmount)
}

private fun StringResource.ByDynamicCurrencyNumber.convertDynamicCurrencyNumber(locale: Locale): String {
    val amount = convertDynamicNumberToString(amount, includeDecimalSeparator, locale)
    return when (this.tickerLocation) {
        TickerLocation.BEFORE -> "$ticker$amount"
        TickerLocation.AFTER -> "$amount $ticker"
        TickerLocation.HIDDEN -> amount
    }
}

private fun StringResource.ByDynamicNumber.convertDynamicNumber(locale: Locale): String =
    convertDynamicNumberToString(number, includeDecimalSeparator, locale)

private fun convertDynamicNumberToString(
    number: Number,
    includeDecimalSeparator: Boolean,
    locale: Locale
): String {
    val bigDecimalAmount = number.toBigDecimal().stripTrailingZeros()
    val dynamicAmount = bigDecimalAmount.stripFractionsDynamically()
    val maxDecimals = if (bigDecimalAmount.scale() > 0) bigDecimalAmount.scale() else 0
    val formatter =
        NumberFormat.getInstance(locale).apply {
            roundingMode = RoundingMode.DOWN
            maximumFractionDigits = maxDecimals.coerceAtLeast(2)
            minimumFractionDigits = 2
            minimumIntegerDigits = 1
            isGroupingUsed = includeDecimalSeparator
        }
    return formatter.format(dynamicAmount)
}

private fun Number.toBigDecimal() =
    when (this) {
        is BigDecimal -> this
        is Int -> BigDecimal(this)
        is Long -> BigDecimal(this)
        is Float -> BigDecimal(this.toDouble())
        is Double -> BigDecimal(this)
        is Short -> BigDecimal(this.toInt())
        else -> BigDecimal(this.toDouble())
    }

private fun StringResource.ByDateTime.convertDateTime(locale: Locale): String {
    if (useFullFormat) {
        return DateFormat
            .getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                locale
            ).format(
                Date.from(zonedDateTime.toInstant())
            )
    } else {
        val pattern = DateTimeFormatter.ofPattern("MMM dd", locale)
        val start = zonedDateTime.format(pattern).orEmpty()
        val end =
            DateFormat
                .getTimeInstance(DateFormat.SHORT, locale)
                .format(Date.from(zonedDateTime.toInstant()))

        return "$start $end"
    }
}

private fun StringResource.ByYearMonth.convertYearMonth(locale: Locale): String {
    val pattern = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    return yearMonth.format(pattern).orEmpty()
}

private fun StringResource.ByAddress.convertAddress(): String {
    return when {
        middle && address.length > ADDRESS_MAX_LENGTH_ABBREVIATED -> {
            val fromSide = ADDRESS_MAX_LENGTH_ABBREVIATED / 2
            return "${address.take(fromSide)}...${address.takeLast(fromSide)}"
        }
        address.length > ADDRESS_MAX_LENGTH_ABBREVIATED -> {
            "${address.take(ADDRESS_MAX_LENGTH_ABBREVIATED)}..."
        }
        else -> {
            address
        }
    }
}

private fun StringResource.ByTransactionId.convertTransactionId(): String =
    if (abbreviated) {
        "${transactionId.take(TRANSACTION_MAX_PREFIX_SUFFIX_LENGHT)}...${
            transactionId.takeLast(TRANSACTION_MAX_PREFIX_SUFFIX_LENGHT)
        }"
    } else {
        transactionId
    }

private const val TRANSACTION_MAX_PREFIX_SUFFIX_LENGHT = 5

private const val ADDRESS_MAX_LENGTH_ABBREVIATED = 20

enum class TickerLocation { BEFORE, AFTER, HIDDEN }

/**
 * Formats a [BigDecimal] with the minimum number of decimal places >= 2 such that
 * the error relative to the original value is less than 0.05%. The scale of the
 * returned value is at least 8, so that subsequent arithmetic operations will not
 * lose precision.
 */
@Suppress("ReturnCount", "MagicNumber")
private fun BigDecimal.stripFractionsDynamically(): BigDecimal {
    val tolerance = BigDecimal(".0005")
    val minDecimals = 2
    val maxDecimals = 8

    val original = this.stripTrailingZeros()
    val originalScale = original.scale()
    if (originalScale <= minDecimals) return original.setScale(maxDecimals, RoundingMode.UNNECESSARY)

    for (scale in minDecimals..maxDecimals) {
        val rounded =
            original
                .setScale(scale, RoundingMode.HALF_EVEN)
                .setScale(maxDecimals, RoundingMode.UNNECESSARY)

        val diff =
            original
                .minus(rounded)
                .divide(original, MathContext.DECIMAL128)
                .abs(MathContext.DECIMAL128)

        if (diff <= tolerance) return rounded
    }

    return original
}
