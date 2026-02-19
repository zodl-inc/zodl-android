package co.electriccoin.zcash.ui.screen.transactiondetail.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSwapQuoteHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoColumn
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoColumnState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoContainer
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRow
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRowState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailRowHeader
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRow
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState

@Composable
fun SendSwap(state: SendSwapState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
    ) {
        var isExpanded by rememberSaveable { mutableStateOf(false) }
        ZashiSwapQuoteHeader(
            state = state.quoteHeader
        )
        Spacer(20.dp)
        TransactionDetailRowHeader(
            title = stringRes(R.string.transaction_detail_info_transaction_details),
            isExpanded = isExpanded,
            onButtonClick = { isExpanded = !isExpanded }
        )
        Spacer(8.dp)
        TransactionDetailInfoContainer {
            TransactionDetailSwapStatusRow(
                modifier = Modifier.fillMaxWidth(),
                state =
                    TransactionDetailSwapStatusRowState(
                        title = stringRes(R.string.transaction_detail_info_transaction_status),
                        status = state.status
                    )
            )
            ZashiHorizontalDivider(thickness = 2.dp)
            TransactionDetailInfoRow(
                modifier = Modifier.fillMaxWidth(),
                state =
                    TransactionDetailInfoRowState(
                        title = stringRes(R.string.transaction_detail_info_sent_to),
                        message = state.depositAddress,
                        trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                        onClick = state.onDepositAddressClick
                    ),
            )
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column {
                    if (state.status == SwapStatus.REFUNDED) {
                        ZashiHorizontalDivider(thickness = 2.dp)
                        TransactionDetailInfoRow(
                            modifier = Modifier.fillMaxWidth(),
                            state =
                                TransactionDetailInfoRowState(
                                    title = stringRes(R.string.transaction_detail_info_refunded),
                                    message = state.refundedAmount,
                                )
                        )
                    }
                    if (state.status != SwapStatus.REFUNDED) {
                        ZashiHorizontalDivider(thickness = 2.dp)
                        TransactionDetailInfoRow(
                            modifier = Modifier.fillMaxWidth(),
                            state =
                                TransactionDetailInfoRowState(
                                    title = stringRes(R.string.transaction_detail_info_recipient),
                                    message = state.recipientAddress,
                                    trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                                    onClick = state.onRecipientAddressClick
                                )
                        )
                    }
                    ZashiHorizontalDivider(thickness = 2.dp)
                    TransactionDetailInfoRow(
                        modifier = Modifier.fillMaxWidth(),
                        state =
                            TransactionDetailInfoRowState(
                                title = stringRes(R.string.transaction_detail_info_transaction_id),
                                message = state.transactionId,
                                trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                                onClick = state.onTransactionIdClick
                            )
                    )
                    ZashiHorizontalDivider(thickness = 2.dp)
                    TransactionDetailInfoRow(
                        modifier = Modifier.fillMaxWidth(),
                        state =
                            TransactionDetailInfoRowState(
                                title = stringRes(R.string.transaction_detail_info_total_fees),
                                message = state.totalFees,
                            )
                    )
                    ZashiHorizontalDivider(thickness = 2.dp)
                    CompositionLocalProvider(LocalBalancesAvailable provides true) {
                        TransactionDetailInfoRow(
                            modifier = Modifier.fillMaxWidth(),
                            state =
                                TransactionDetailInfoRowState(
                                    title =
                                        if (state.isSlippageRealized) {
                                            stringRes(R.string.transaction_detail_info_realized_slippage)
                                        } else {
                                            stringRes(R.string.transaction_detail_info_max_slippage)
                                        },
                                    message = state.maxSlippage,
                                )
                        )
                    }
                    ZashiHorizontalDivider(thickness = 2.dp)
                    TransactionDetailInfoRow(
                        modifier = Modifier.fillMaxWidth(),
                        state =
                            TransactionDetailInfoRowState(
                                title = stringRes(R.string.transaction_detail_info_timestamp),
                                message = state.completedTimestamp,
                            )
                    )
                }
            }
            if (state.note != null) {
                ZashiHorizontalDivider(thickness = 2.dp)
                TransactionDetailInfoColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state =
                        TransactionDetailInfoColumnState(
                            title = stringRes(R.string.transaction_detail_info_note),
                            message = state.note,
                            onClick = null
                        )
                )
            }
        }
        if (state.status == SwapStatus.REFUNDED) {
            Spacer(8.dp)
            SwapRefundedInfo()
        }
    }
}

@Composable
fun SwapRefundedInfo() {
    ZashiCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = ZashiColors.Utility.WarningYellow.utilityOrange50,
                contentColor = ZashiColors.Utility.WarningYellow.utilityOrange800,
            ),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row {
            Image(
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Utility.WarningYellow.utilityOrange500)
            )
            Spacer(12.dp)
            Column {
                Spacer(2.dp)
                Text(
                    text = stringResource(R.string.transaction_detail_info_refunded_title),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Utility.WarningYellow.utilityOrange700
                )
                Spacer(8.dp)
                Text(
                    text = stringResource(R.string.transaction_detail_info_refunded_message),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Utility.WarningYellow.utilityOrange800
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            SendSwap(
                state =
                    SendSwapState(
                        status = SwapStatus.REFUNDED,
                        quoteHeader = SwapQuoteHeaderState(from = null, to = null),
                        depositAddress = stringResByAddress(value = "Address"),
                        totalFees = null,
                        recipientAddress = null,
                        transactionId = stringRes("Transaction ID"),
                        refundedAmount = stringRes("Refunded amount"),
                        onTransactionIdClick = {},
                        onDepositAddressClick = {},
                        onRecipientAddressClick = {},
                        maxSlippage = null,
                        note = stringRes("None"),
                        isSlippageRealized = false,
                        isPending = false,
                        completedTimestamp = stringRes("Completed"),
                    ),
            )
        }
    }
