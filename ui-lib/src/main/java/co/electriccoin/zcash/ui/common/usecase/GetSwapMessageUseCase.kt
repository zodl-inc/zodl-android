package co.electriccoin.zcash.ui.common.usecase

import androidx.compose.ui.text.font.FontWeight
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapStatus.INCOMPLETE_DEPOSIT
import co.electriccoin.zcash.ui.common.model.SwapStatus.REFUNDED
import co.electriccoin.zcash.ui.design.component.ZashiMessageState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import kotlinx.datetime.toJavaInstant
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GetSwapMessageUseCase {
    operator fun invoke(swap: SwapQuoteStatusData): ZashiMessageState? {
        val quoteStatus = swap.status
        val swapStatus = quoteStatus?.status
        return when (swapStatus) {
            REFUNDED -> ZashiMessageState(
                stringRes(R.string.transaction_detail_info_refunded_title),
                styledStringResource(stringRes(R.string.transaction_detail_info_refunded_message)),
            )

            INCOMPLETE_DEPOSIT -> {
                val missingAmount =
                    (quoteStatus.amountInUsd - (quoteStatus.depositedAmountUsd ?: BigDecimal.ZERO))
                        .coerceAtLeast(BigDecimal.ZERO)

                val deadline = quoteStatus.quote.deadline.toJavaInstant().atZone(ZoneId.of("UTC"))
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'")
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                ZashiMessageState(
                    stringRes(R.string.transaction_detail_info_incomplete_deposit_title),
                    styledStringResource(
                        R.string.transaction_detail_info_incomplete_deposit_message,
                        color = StringResourceColor.WARNING,
                        fontWeight = null,
                        styledStringResource(
                            stringResByDynamicCurrencyNumber(missingAmount, "USDC"),
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Bold),
                        styledStringResource(stringRes(deadline.format(timeFormatter)),
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Bold),
                        styledStringResource(stringRes(deadline.format(dateFormatter)),
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Bold),
                    ),
                )
            }

            else -> null
        }
    }
}
