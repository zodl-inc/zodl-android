@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.design.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalContext
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.currencyFormatter
import cash.z.ecc.sdk.extension.zatoshiFormatter
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DateFormat
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

sealed interface StringResource {
    data class ByResource(
        @param:StringRes val resource: Int,
        val args: List<Any>
    ) : StringResource

    @JvmInline
    value class ByString(
        val value: String
    ) : StringResource

    data class ByZatoshi(
        val zatoshi: Zatoshi,
        val tickerLocation: TickerLocation
    ) : StringResource

    data class ByDateTime(
        val zonedDateTime: ZonedDateTime,
        val useFullFormat: Boolean
    ) : StringResource

    data class ByYearMonth(
        val yearMonth: YearMonth
    ) : StringResource

    data class ByTransactionId(
        val transactionId: String,
        val abbreviated: Boolean
    ) : StringResource

    data class ByAddress(
        val address: String,
        val ellipsize: Ellipsize
    ) : StringResource

    data class ByCurrencyNumber(
        val amount: Number,
        val ticker: String,
        val tickerLocation: TickerLocation,
        val minDecimals: Int,
        val maxDecimals: Int?,
        val includeGroupingSeparator: Boolean
    ) : StringResource

    data class ByDynamicCurrencyNumber(
        val amount: Number,
        val ticker: String,
        val includeGroupingSeparator: Boolean,
        val tickerLocation: TickerLocation
    ) : StringResource

    data class ByNumber(
        val number: Number,
        val minDecimals: Int,
        val maxDecimals: Int?,
        val includeGroupingSeparator: Boolean
    ) : StringResource

    data class ByDynamicNumber(
        val number: Number,
        val includeGroupingSeparator: Boolean
    ) : StringResource

    operator fun plus(other: StringResource): StringResource = CompositeStringResource(listOf(this, other))

    operator fun plus(other: String): StringResource = CompositeStringResource(listOf(this, stringRes(other)))

    fun isEmpty(): Boolean = if (this is ByString) value.isEmpty() else false
}

private data class CompositeStringResource(
    val resources: List<StringResource>
) : StringResource

private data class PrivacySensitiveResource(
    val value: StringResource,
    val hiddenValue: StringResource = stringRes(R.string.hide_balance_placeholder)
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
    includeGroupingSeparator: Boolean = true
): StringResource =
    StringResource.ByDynamicCurrencyNumber(
        amount = amount,
        ticker = ticker,
        includeGroupingSeparator = includeGroupingSeparator,
        tickerLocation = tickerLocation
    )

@Stable
fun stringResByCurrencyNumber(
    amount: Number,
    ticker: String,
    tickerLocation: TickerLocation =
        if (ticker == FiatCurrency.USD.symbol) TickerLocation.BEFORE else TickerLocation.AFTER,
    minDecimals: Int = 2,
    maxDecimals: Int? = null,
    includeGroupingSeparator: Boolean = true
): StringResource =
    StringResource.ByCurrencyNumber(
        amount = amount,
        ticker = ticker,
        tickerLocation = tickerLocation,
        minDecimals = minDecimals,
        maxDecimals = maxDecimals,
        includeGroupingSeparator = includeGroupingSeparator
    )

@Stable
fun stringResByDateTime(zonedDateTime: ZonedDateTime, useFullFormat: Boolean): StringResource =
    StringResource.ByDateTime(zonedDateTime, useFullFormat)

@Stable
fun stringRes(yearMonth: YearMonth): StringResource =
    StringResource.ByYearMonth(yearMonth)

@Stable
fun stringResByAddress(value: String, ellipsize: Ellipsize = Ellipsize.MIDDLE): StyledStringResource =
    StringResource.ByAddress(value, ellipsize).styleAsAddress()

fun StringResource.styleAsAddress(): StyledStringResource =
    StyledStringResource.ByStringResource(this, StyledStringStyle(font = StyledStringFont.ROBOTO_MONO))

@Stable
fun stringResByTransactionId(value: String, abbreviated: Boolean): StringResource =
    StringResource.ByTransactionId(value, abbreviated)

@Stable
fun stringResByNumber(
    number: Number,
    minDecimals: Int = 2,
    maxDecimals: Int? = null,
    includeGroupingSeparator: Boolean = true
): StringResource =
    StringResource.ByNumber(number, minDecimals, maxDecimals, includeGroupingSeparator)

@Stable
fun stringResByDynamicNumber(number: Number, includeGroupingSeparator: Boolean = true): StringResource =
    StringResource.ByDynamicNumber(number, includeGroupingSeparator)

@Stable
infix fun StringResource.asPrivacySensitive(
    other: StringResource = stringRes(R.string.hide_balance_placeholder)
): StringResource = PrivacySensitiveResource(this, other)

@Stable
@Composable
fun StringResource.getValue(): String =
    getString(
        StringContext(
            context = LocalContext.current,
            locale = rememberDesiredFormatLocale(),
            isHideBalances = LocalBalancesAvailable.current.not()
        )
    )

data class StringContext(
    val context: Context,
    val locale: Locale,
    val isHideBalances: Boolean
)

fun StringResource.getString(
    context: Context,
    locale: Locale = context.resources.configuration.getPreferredLocale(),
    isHideBalances: Boolean = false,
) = getString(
    StringContext(
        context = context,
        locale = locale,
        isHideBalances = isHideBalances,
    )
)

fun StringResource.getString(
    context: StringContext
): String {
    val string =
        when (this) {
            is StringResource.ByResource -> convertResource(context)
            is StringResource.ByString -> value
            is StringResource.ByZatoshi -> convertZatoshi(context)
            is StringResource.ByCurrencyNumber -> convertCurrencyNumber(context)
            is StringResource.ByDynamicCurrencyNumber -> convertDynamicCurrencyNumber(context)
            is StringResource.ByDateTime -> convertDateTime(context)
            is StringResource.ByYearMonth -> convertYearMonth(context)
            is StringResource.ByAddress -> convertAddress()
            is StringResource.ByTransactionId -> convertTransactionId()
            is StringResource.ByNumber -> convertNumber(context)
            is StringResource.ByDynamicNumber -> convertDynamicNumber(context)
            is CompositeStringResource -> convertComposite(context)
            is PrivacySensitiveResource -> convertPrivacySensitive(context)
        }
    return string
}

private fun PrivacySensitiveResource.convertPrivacySensitive(context: StringContext) =
    if (context.isHideBalances) {
        hiddenValue.getString(context)
    } else {
        value.getString(context)
    }

private fun CompositeStringResource.convertComposite(
    context: StringContext
) = this.resources.joinToString(separator = "") { it.getString(context) }

@Suppress("SpreadOperator")
private fun StringResource.ByResource.convertResource(context: StringContext) =
    context.context.getString(
        resource,
        *args.map { if (it is StringResource) it.getString(context) else it }.toTypedArray()
    )

private fun StringResource.ByNumber.convertNumber(context: StringContext): String =
    convertNumberToString(number, context.locale, minDecimals, maxDecimals, includeGroupingSeparator)

private fun StringResource.ByZatoshi.convertZatoshi(context: StringContext): String {
    val zec = this.zatoshi.convertZatoshiToZec(scale = 8)
    val amount = zatoshiFormatter(context.locale).format(zec)
    return when (this.tickerLocation) {
        TickerLocation.BEFORE -> "ZEC $amount"
        TickerLocation.AFTER -> "$amount ZEC"
        TickerLocation.HIDDEN -> amount
    }
}

private fun StringResource.ByCurrencyNumber.convertCurrencyNumber(context: StringContext): String {
    val amount = convertNumberToString(amount, context.locale, minDecimals, maxDecimals, includeGroupingSeparator)
    return when (this.tickerLocation) {
        TickerLocation.BEFORE -> "$ticker$amount"
        TickerLocation.AFTER -> "$amount $ticker"
        TickerLocation.HIDDEN -> amount
    }
}

private fun convertNumberToString(
    amount: Number,
    locale: Locale,
    minDecimals: Int,
    maxDecimals: Int?,
    includeGroupingSeparator: Boolean
): String {
    val bigDecimalAmount = amount.toBigDecimal().stripTrailingZeros()
    val maxFractionDigits = maxDecimals ?: bigDecimalAmount.scale().coerceAtLeast(minDecimals)
    val formatter =
        currencyFormatter(
            locale,
            maximumFractionDigits = maxFractionDigits,
            minimumFractionDigits = minDecimals,
        ).apply {
            roundingMode = RoundingMode.HALF_EVEN
            minimumIntegerDigits = 1
            isGroupingUsed = includeGroupingSeparator
        }
    return formatter.format(bigDecimalAmount)
}

private fun StringResource.ByDynamicCurrencyNumber.convertDynamicCurrencyNumber(context: StringContext): String {
    val amount = convertDynamicNumberToString(amount, includeGroupingSeparator, context.locale)
    return when (this.tickerLocation) {
        TickerLocation.BEFORE -> "$ticker$amount"
        TickerLocation.AFTER -> "$amount $ticker"
        TickerLocation.HIDDEN -> amount
    }
}

private fun StringResource.ByDynamicNumber.convertDynamicNumber(context: StringContext): String =
    convertDynamicNumberToString(number, includeGroupingSeparator, context.locale)

private fun convertDynamicNumberToString(
    number: Number,
    includeGroupingSeparator: Boolean,
    locale: Locale
): String {
    val bigDecimalAmount = number.toBigDecimal().stripTrailingZeros()
    val dynamicAmount = bigDecimalAmount.stripFractionsDynamically()
    val maxDecimals = if (bigDecimalAmount.scale() > 0) bigDecimalAmount.scale() else 0
    val formatter =
        currencyFormatter(
            locale,
            minimumFractionDigits = 2,
            maximumFractionDigits = maxDecimals.coerceAtLeast(2)
        ).apply {
            roundingMode = RoundingMode.DOWN
            minimumIntegerDigits = 1
            isGroupingUsed = includeGroupingSeparator
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

private fun StringResource.ByDateTime.convertDateTime(context: StringContext): String {
    if (useFullFormat) {
        return DateFormat
            .getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                context.locale
            ).format(
                Date.from(zonedDateTime.toInstant())
            )
    } else {
        val pattern = DateTimeFormatter.ofPattern("MMM dd", context.locale)
        val start = zonedDateTime.format(pattern).orEmpty()
        val end =
            DateFormat
                .getTimeInstance(DateFormat.SHORT, context.locale)
                .format(Date.from(zonedDateTime.toInstant()))

        return "$start $end"
    }
}

private fun StringResource.ByYearMonth.convertYearMonth(context: StringContext): String {
    val pattern = DateTimeFormatter.ofPattern("MMMM yyyy", context.locale)
    return yearMonth.format(pattern).orEmpty()
}

private fun StringResource.ByAddress.convertAddress(): String =
    when (ellipsize) {
        Ellipsize.MIDDLE if address.length > ADDRESS_MAX_LENGTH_ABBREVIATED -> {
            address.ellipsizeMiddle(ADDRESS_MAX_LENGTH_ABBREVIATED)
        }

        Ellipsize.END if address.length > ADDRESS_MAX_LENGTH_ABBREVIATED -> {
            address.ellipsizeEnd(ADDRESS_MAX_LENGTH_ABBREVIATED)
        }

        else -> {
            address
        }
    }

private fun String.ellipsizeMiddle(size: Int): String {
    val half = size / 2
    if (this.length <= size) return this
    return "${this.take(half)}$DOTS${this.takeLast(half)}"
}

private fun String.ellipsizeEnd(size: Int) = "${this.take(size)}$DOTS"

private fun StringResource.ByTransactionId.convertTransactionId(): String =
    if (abbreviated) transactionId.ellipsizeMiddle(TRANSACTION_MAX_PREFIX_SUFFIX_LENGTH) else transactionId

private const val DOTS = "..."

private const val TRANSACTION_MAX_PREFIX_SUFFIX_LENGTH = 5

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

enum class Ellipsize {
    NONE,
    MIDDLE,
    END
}
