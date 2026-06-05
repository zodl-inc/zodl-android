package co.electriccoin.zcash.ui.screen.request.model

import android.content.Context
import cash.z.ecc.android.sdk.ext.convertUsdToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.sdk.extension.floor
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.getPreferredLocale
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.screen.request.ext.toBigDecimalLocalized
import java.math.BigDecimal
import java.math.MathContext

data class Request(
    val amountState: AmountState,
    val memoState: MemoState,
    val qrCodeState: QrCodeState,
)

data class AmountState(
    val amount: String,
    val currency: RequestCurrency,
    val isValid: Boolean?
) {
    fun toZecString(conversion: FiatCurrencyConversion, context: Context): String {
        val locale = context.resources.configuration.getPreferredLocale()
        return stringResByNumber(
            amount.toBigDecimalLocalized(locale).convertUsdToZec(conversion.priceOfZec.toBigDecimal()),
            minDecimals = 3,
            maxDecimals = 8
        ).getString(context)
    }

    fun toZecStringFloored(conversion: FiatCurrencyConversion, context: Context): String {
        val locale = context.resources.configuration.getPreferredLocale()
        return stringRes(
            amount
                .toBigDecimalLocalized(locale)
                .convertUsdToZec(conversion.priceOfZec.toBigDecimal())
                .convertZecToZatoshi()
                .floor(),
            tickerLocation = TickerLocation.HIDDEN
        ).getString(context)
    }

    fun toFiatString(context: Context, conversion: FiatCurrencyConversion): String {
        val locale = context.resources.configuration.getPreferredLocale()
        val zecAmount = amount.toBigDecimalLocalized(locale) ?: return ""
        val priceOfZec = BigDecimal(conversion.priceOfZec)
        val fiat = zecAmount.multiply(priceOfZec, MathContext.DECIMAL128)
        return stringResByNumber(fiat, maxDecimals = 2).getString(context)
    }
}

sealed class MemoState(
    open val text: String,
    open val byteSize: Int,
    open val zecAmount: String
) {
    fun isValid(): Boolean = this is Valid

    data class Valid(
        override val text: String,
        override val byteSize: Int,
        override val zecAmount: String
    ) : MemoState(text, byteSize, zecAmount)

    data class InValid(
        override val text: String,
        override val byteSize: Int,
        override val zecAmount: String
    ) : MemoState(text, byteSize, zecAmount)

    companion object {
        fun new(memo: String, amount: String): MemoState {
            val bytesCount = Memo.countLength(memo)
            return if (bytesCount > Memo.MAX_MEMO_LENGTH_BYTES) {
                InValid(memo, bytesCount, amount)
            } else {
                Valid(memo, bytesCount, amount)
            }
        }
    }
}

data class QrCodeState(
    val requestUri: String,
    val zecAmount: String,
    val memo: String
)
