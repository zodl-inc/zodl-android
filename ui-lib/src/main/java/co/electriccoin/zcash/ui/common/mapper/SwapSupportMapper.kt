package co.electriccoin.zcash.ui.common.mapper

import androidx.compose.ui.text.font.FontWeight
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiMessageState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import kotlinx.datetime.toJavaInstant
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class SwapSupportMapper {
    fun getMessage(quoteStatus: SwapQuoteStatus?): ZashiMessageState? =
        when (quoteStatus?.status) {
            SwapStatus.REFUNDED ->
                ZashiMessageState(
                    title = stringRes(R.string.transaction_detail_info_refunded_title),
                    text = styledStringResource(R.string.transaction_detail_info_refunded_message),
                )

            SwapStatus.FAILED ->
                ZashiMessageState(
                    title = stringRes(R.string.transaction_detail_info_failed_title),
                    text = styledStringResource(R.string.transaction_detail_info_failed_message),
                )

            SwapStatus.EXPIRED ->
                ZashiMessageState(
                    title = stringRes(R.string.transaction_detail_info_expired_title),
                    text = styledStringResource(R.string.transaction_detail_info_expired_message),
                )

            SwapStatus.INCOMPLETE_DEPOSIT -> createIncompleteDepositMessage(quoteStatus)

            SwapStatus.PROCESSING -> {
                if (isProcessingLongEnough(quoteStatus)) {
                    ZashiMessageState(
                        title = stringRes(R.string.swap_detail_title_swap_processing),
                        text = styledStringResource(R.string.transaction_detail_info_pending_deposit_message),
                        type = ZashiMessageState.Type.INFO,
                    )
                } else {
                    null
                }
            }

            SwapStatus.PENDING,
            SwapStatus.SUCCESS,
            null -> null
        }

    fun getButton(quoteStatus: SwapQuoteStatus?, onSupportClicked: (String) -> Unit): ButtonState? =
        if (when (quoteStatus?.status) {
                SwapStatus.REFUNDED,
                SwapStatus.FAILED,
                SwapStatus.EXPIRED -> true

                SwapStatus.PROCESSING -> isProcessingLongEnough(quoteStatus)

                SwapStatus.INCOMPLETE_DEPOSIT,
                SwapStatus.PENDING,
                SwapStatus.SUCCESS,
                null -> false
            }
        ) {
            ButtonState(
                text = stringRes(R.string.transaction_detail_contact_support),
                style = ButtonStyle.TERTIARY,
                onClick = {
                    quoteStatus?.quote?.depositAddress?.address?.let {
                        onSupportClicked(it)
                    }
                }
            )
        } else {
            null
        }

    private fun createIncompleteDepositMessage(quoteStatus: SwapQuoteStatus): ZashiMessageState {
        val missingAmount = (quoteStatus.amountInFormatted - (quoteStatus.depositedAmountFormatted ?: BigDecimal.ZERO))
                .coerceAtLeast(BigDecimal.ZERO)

        val deadline =
            quoteStatus.quote.deadline
                .toJavaInstant()
                .atZone(ZoneId.systemDefault())

        return ZashiMessageState(
            stringRes(R.string.transaction_detail_info_incomplete_deposit_title),
            styledStringResource(
                R.string.transaction_detail_info_incomplete_deposit_message,
                color = StringResourceColor.WARNING,
                fontWeight = null,
                styledStringResource(
                    stringResByDynamicCurrencyNumber(missingAmount, quoteStatus.quote.originAsset.tokenTicker),
                    color = StringResourceColor.WARNING,
                    fontWeight = FontWeight.Bold
                ),
                styledStringResource(
                    stringResByDateTime(deadline, true),
                    color = StringResourceColor.WARNING,
                    fontWeight = FontWeight.Bold
                ),
            ),
        )
    }

    private fun isProcessingLongEnough(quoteStatus: SwapQuoteStatus): Boolean {
        val now = Instant.now()
        val timestamp = quoteStatus.timestamp
        val duration = Duration.between(timestamp, now)
        return duration.toMinutes() >= PROCESSING_SUPPORT_DELAY
    }

    companion object{
        const val PROCESSING_SUPPORT_DELAY = 60
    }
}
