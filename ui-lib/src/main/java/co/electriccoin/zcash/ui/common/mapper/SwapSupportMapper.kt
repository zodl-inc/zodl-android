package co.electriccoin.zcash.ui.common.mapper

import androidx.compose.ui.text.font.FontWeight
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiMessageState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.time.toJavaInstant

class SwapSupportMapper {
    fun getMessage(quoteStatus: SwapQuoteStatus?): ZashiMessageState? =
        when (quoteStatus?.status) {
            SwapStatus.REFUNDED -> {
                ZashiMessageState(
                    title = stringRes(R.string.swapAndPay_refundTitle),
                    text = styledStringResource(R.string.swapAndPay_refundInfo),
                    type = ZashiMessageState.Type.WARNING,
                )
            }

            SwapStatus.FAILED -> {
                ZashiMessageState(
                    title = stringRes(R.string.swapAndPay_failedTitle),
                    text = styledStringResource(R.string.swapAndPay_failedMsg),
                    type = ZashiMessageState.Type.ERROR,
                )
            }

            SwapStatus.EXPIRED -> {
                ZashiMessageState(
                    title = stringRes(R.string.swapAndPay_expiredTitle),
                    text = styledStringResource(R.string.swapAndPay_expiredMsg),
                    type = ZashiMessageState.Type.ERROR,
                )
            }

            SwapStatus.INCOMPLETE_DEPOSIT -> {
                createIncompleteDepositMessage(quoteStatus)
            }

            SwapStatus.PROCESSING -> {
                if (isProcessingLongEnough(quoteStatus)) {
                    ZashiMessageState(
                        title = stringRes(R.string.swapToZec_swapProcessing),
                        text = styledStringResource(R.string.swapAndPay_processingMsg),
                        type = ZashiMessageState.Type.INFO,
                    )
                } else {
                    null
                }
            }

            SwapStatus.PENDING,
            SwapStatus.SUCCESS,
            null -> {
                null
            }
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
                text = stringRes(R.string.errorPage_action_contactSupport),
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
        val missingAmount =
            (quoteStatus.amountInFormatted - (quoteStatus.depositedAmountFormatted ?: BigDecimal.ZERO))
                .coerceAtLeast(BigDecimal.ZERO)

        val deadline =
            quoteStatus.quote.deadline
                .toJavaInstant()
                .atZone(ZoneId.systemDefault())
        val style =
            StyledStringStyle(
                color = StringResourceColor.WARNING,
                fontWeight = FontWeight.Bold
            )
        return ZashiMessageState(
            stringRes(R.string.swapAndPay_status_incompleteDeposit),
            styledStringResource(
                R.string.transaction_detail_info_incomplete_deposit_message,
                StyledStringStyle(StringResourceColor.WARNING),
                stringResByDynamicCurrencyNumber(missingAmount, quoteStatus.quote.originAsset.tokenTicker)
                    .withStyle(style),
                stringResByDateTime(deadline, true).withStyle(style),
            ),
            type = ZashiMessageState.Type.WARNING,
        )
    }

    private fun isProcessingLongEnough(quoteStatus: SwapQuoteStatus): Boolean {
        val now = Instant.now()
        val timestamp = quoteStatus.timestamp
        val duration = Duration.between(timestamp, now)
        return duration.toMinutes() >= PROCESSING_SUPPORT_DELAY
    }

    companion object {
        const val PROCESSING_SUPPORT_DELAY = 60
    }
}
