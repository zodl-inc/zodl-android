package co.electriccoin.zcash.ui.screen.transactiondetail.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactiondetail.ReceiveShieldedStateFixture
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoColumn
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoColumnState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoContainer
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoHeaderState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRow
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRowState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailMemo
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailTitleHeader

@Composable
fun ReceiveShielded(
    state: ReceiveShieldedState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        state.memo?.let {
            TransactionDetailTitleHeader(
                state =
                    TransactionDetailInfoHeaderState(
                        title = stringRes(R.string.transaction_detail_info_message)
                    )
            )
            Spacer(Modifier.height(8.dp))
            TransactionDetailMemo(
                modifier = Modifier.fillMaxWidth(),
                state = it
            )
        }
        Spacer(Modifier.height(20.dp))
        TransactionDetailTitleHeader(
            state =
                TransactionDetailInfoHeaderState(
                    title = stringRes(R.string.transaction_detail_info_transaction_details)
                )
        )
        Spacer(Modifier.height(8.dp))
        TransactionDetailInfoContainer {
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
            CompositionLocalProvider(
                LocalBalancesAvailable provides
                    (state.isPending || LocalBalancesAvailable.current)
            ) {
                TransactionDetailInfoRow(
                    modifier = Modifier.fillMaxWidth(),
                    state =
                        TransactionDetailInfoRowState(
                            title =
                                if (state.isPending) {
                                    stringRes(R.string.transaction_detail_info_transaction_status)
                                } else {
                                    stringRes(R.string.transaction_detail_info_timestamp)
                                },
                            message = state.completedTimestamp
                        )
                )
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
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ReceiveShielded(
                state = ReceiveShieldedStateFixture.new(),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewWithoutMemo() =
    ZcashTheme {
        BlankSurface {
            ReceiveShielded(
                state = ReceiveShieldedStateFixture.new(memo = TransactionDetailMemosState(emptyList())),
            )
        }
    }
