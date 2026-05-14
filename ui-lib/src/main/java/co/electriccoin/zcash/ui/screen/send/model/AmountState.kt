package co.electriccoin.zcash.ui.screen.send.model

import android.content.Context
import androidx.compose.runtime.saveable.mapSaver
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.toZatoshi
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextFieldParser
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import java.math.BigDecimal
import java.math.MathContext

sealed interface AmountState {
    val value: StringResource
    val fiatValue: StringResource
    val lastFieldChangedByUser: AmountField

    data class Valid(
        override val value: StringResource,
        override val fiatValue: StringResource,
        override val lastFieldChangedByUser: AmountField,
        val zatoshi: Zatoshi,
    ) : AmountState

    data class Invalid(
        override val value: StringResource,
        override val fiatValue: StringResource,
        override val lastFieldChangedByUser: AmountField
    ) : AmountState

    companion object {
        @Suppress("LongParameterList", "ReturnCount")
        fun newFromZec(
            locale: java.util.Locale,
            value: String,
            fiatValue: String,
            isTransparentOrTextRecipient: Boolean,
            exchangeRateState: ExchangeRateState,
            lastFieldChangedByUser: AmountField = AmountField.ZEC
        ): AmountState {
            val normalized = ZashiNumberTextFieldParser.normalizeInput(value, locale)

            val zecAmount =
                ZashiNumberTextFieldParser.toBigDecimalOrNull(normalized, locale)
                    ?: return Invalid(
                        stringRes(normalized),
                        stringRes(if (normalized.isBlank()) "" else fiatValue),
                        lastFieldChangedByUser
                    )

            val zatoshi =
                try {
                    zecAmount.convertZecToZatoshi()
                } catch (_: IllegalArgumentException) {
                    return Invalid(
                        stringRes(normalized),
                        stringRes(if (normalized.isBlank()) "" else fiatValue),
                        lastFieldChangedByUser
                    )
                }

            val currencyConversion =
                if (exchangeRateState !is ExchangeRateState.Data ||
                    (!exchangeRateState.isLoading && exchangeRateState.isStale)
                ) {
                    null
                } else {
                    exchangeRateState.currencyConversion
                }

            // Note that the zero funds sending is supported for sending a memo-only shielded transaction
            return when {
                (zatoshi.value == 0L && isTransparentOrTextRecipient) -> {
                    Invalid(stringRes(normalized), stringRes(fiatValue), lastFieldChangedByUser)
                }

                else -> {
                    Valid(
                        value = stringRes(normalized),
                        zatoshi = zatoshi,
                        fiatValue =
                            if (currencyConversion == null) {
                                stringRes(fiatValue)
                            } else {
                                stringResByNumber(
                                    zatoshi
                                        .convertZatoshiToZec()
                                        .multiply(BigDecimal(currencyConversion.priceOfZec), MathContext.DECIMAL128),
                                    maxDecimals = 2,
                                    includeGroupingSeparator = false
                                )
                            },
                        lastFieldChangedByUser = lastFieldChangedByUser
                    )
                }
            }
        }

        @Suppress("LongParameterList")
        fun newFromFiat(
            locale: java.util.Locale,
            value: String,
            fiatValue: String,
            isTransparentOrTextRecipient: Boolean,
            exchangeRateState: ExchangeRateState,
        ): AmountState {
            val normalized = ZashiNumberTextFieldParser.normalizeInput(fiatValue, locale)

            val fiatAmount =
                ZashiNumberTextFieldParser.toBigDecimalOrNull(normalized, locale)
                    ?: return Invalid(
                        value = stringRes(if (normalized.isBlank()) "" else value),
                        fiatValue = stringRes(normalized),
                        lastFieldChangedByUser = AmountField.FIAT
                    )

            val zatoshi =
                (exchangeRateState as? ExchangeRateState.Data)?.currencyConversion?.toZatoshi(amount = fiatAmount)

            return when {
                zatoshi == null -> {
                    Invalid(
                        value = stringRes(if (fiatValue.isBlank()) "" else value),
                        fiatValue = stringRes(fiatValue),
                        lastFieldChangedByUser = AmountField.FIAT
                    )
                }

                (zatoshi.value == 0L && isTransparentOrTextRecipient) -> {
                    Invalid(
                        value = stringRes(if (fiatValue.isBlank()) "" else value),
                        fiatValue = stringRes(fiatValue),
                        lastFieldChangedByUser = AmountField.FIAT
                    )
                }

                else -> {
                    Valid(
                        value = stringRes(zatoshi, TickerLocation.HIDDEN),
                        zatoshi = zatoshi,
                        fiatValue = stringRes(normalized),
                        lastFieldChangedByUser = AmountField.FIAT
                    )
                }
            }
        }

        private const val TYPE_VALID = "valid" // $NON-NLS
        private const val TYPE_INVALID = "invalid" // $NON-NLS
        private const val KEY_TYPE = "type" // $NON-NLS
        private const val KEY_VALUE = "value" // $NON-NLS
        private const val KEY_FIAT_VALUE = "fiat_value" // $NON-NLS
        private const val KEY_ZATOSHI = "zatoshi" // $NON-NLS
        private const val KEY_LAST_FIELD_CHANGED_BY_USER = "last_field_changed_by_user" // $NON-NLS

        internal fun getSaver(context: Context) =
            run {
                mapSaver(
                    save = { it.toSaverMap(context) },
                    restore = {
                        if (it.isEmpty()) {
                            null
                        } else {
                            val amountString = (it[KEY_VALUE] as String)
                            val fiatAmountString = (it[KEY_FIAT_VALUE] as String)
                            val type = (it[KEY_TYPE] as String)
                            val lastFieldChangedByUser =
                                AmountField.valueOf(it[KEY_LAST_FIELD_CHANGED_BY_USER] as String)
                            when (type) {
                                TYPE_VALID -> {
                                    Valid(
                                        value = stringRes(amountString),
                                        fiatValue = stringRes(fiatAmountString),
                                        zatoshi = Zatoshi(it[KEY_ZATOSHI] as Long),
                                        lastFieldChangedByUser = lastFieldChangedByUser
                                    )
                                }

                                TYPE_INVALID -> {
                                    Invalid(
                                        value = stringRes(amountString),
                                        fiatValue = stringRes(fiatAmountString),
                                        lastFieldChangedByUser = lastFieldChangedByUser
                                    )
                                }

                                else -> {
                                    null
                                }
                            }
                        }
                    }
                )
            }

        private fun AmountState.toSaverMap(context: Context): HashMap<String, Any> {
            val saverMap = HashMap<String, Any>()
            when (this) {
                is Valid -> {
                    saverMap[KEY_TYPE] = TYPE_VALID
                    saverMap[KEY_ZATOSHI] = this.zatoshi.value
                }

                is Invalid -> {
                    saverMap[KEY_TYPE] = TYPE_INVALID
                }
            }
            saverMap[KEY_VALUE] = this.value.getString(context)
            saverMap[KEY_FIAT_VALUE] = this.fiatValue.getString(context)
            saverMap[KEY_LAST_FIELD_CHANGED_BY_USER] = this.lastFieldChangedByUser.name

            return saverMap
        }
    }
}

enum class AmountField { ZEC, FIAT }
