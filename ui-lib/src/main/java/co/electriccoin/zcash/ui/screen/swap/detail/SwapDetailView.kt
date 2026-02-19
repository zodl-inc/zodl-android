package co.electriccoin.zcash.ui.screen.swap.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.component.ZashiBottomBar
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiSwapQuoteHeader
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.asScaffoldPaddingValues
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.transactiondetail.ErrorFooter
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailHeader
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailHeaderState
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionErrorFooter
import co.electriccoin.zcash.ui.screen.transactiondetail.info.SwapRefundedInfo
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoContainer
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRow
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRowState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailRowHeader
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRow
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState

@Composable
fun SwapDetailView(
    state: SwapDetailState,
    appBarState: ZashiMainTopAppBarState?,
) {
    GradientBgScaffold(
        startColor = ZashiColors.Surfaces.bgPrimary orDark ZashiColors.Surfaces.bgAdjust,
        endColor = ZashiColors.Surfaces.bgPrimary,
        topBar = { TopAppBar(state = state, appBarState = appBarState) }
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TransactionDetailHeader(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .scaffoldPadding(
                            paddingValues = paddingValues,
                            bottom = 0.dp,
                            start = 0.dp,
                            end = 0.dp
                        ),
                state = state.transactionHeader
            )
            var isExpanded by rememberSaveable { mutableStateOf(false) }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .scaffoldPadding(paddingValues, top = 24.dp),
            ) {
                ZashiSwapQuoteHeader(state.quoteHeader)
                Spacer(20.dp)
                TransactionDetailRowHeader(
                    title = stringRes(R.string.swap_detail_row_header),
                    isExpanded = isExpanded,
                    onButtonClick = { isExpanded = !isExpanded }
                )
                Spacer(8.dp)
                TransactionDetailInfoContainer {
                    TransactionDetailSwapStatusRow(state = state.status)
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(expandFrom = Alignment.Top),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Column {
                            ZashiHorizontalDivider()
                            TransactionDetailInfoRow(state.depositTo)
                            ZashiHorizontalDivider()
                            TransactionDetailInfoRow(state.recipient)
                            ZashiHorizontalDivider()
                            TransactionDetailInfoRow(state.totalFees)
                            ZashiHorizontalDivider()
                            CompositionLocalProvider(LocalBalancesAvailable provides true) {
                                TransactionDetailInfoRow(state.maxSlippage)
                            }
                        }
                    }
                    ZashiHorizontalDivider()
                    TransactionDetailInfoRow(state = state.timestamp)
                }
                if (state.status.status == SwapStatus.REFUNDED) {
                    Spacer(8.dp)
                    SwapRefundedInfo()
                }
            }
            BottomBar(
                scrollState = scrollState,
                paddingValues = paddingValues,
                state = state
            )
        }
    }
}

@Composable
private fun BottomBar(
    scrollState: ScrollState,
    paddingValues: PaddingValues,
    state: SwapDetailState
) {
    ZashiBottomBar(
        isElevated = scrollState.value > 0,
        contentPadding = paddingValues.asScaffoldPaddingValues(top = 0.dp, bottom = 0.dp)
    ) {
        if (state.errorFooter != null) {
            TransactionErrorFooter(state.errorFooter)
        }

        state.primaryButton?.let {
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = it
            )
        }
    }
}

@Composable
private fun TopAppBar(state: SwapDetailState, appBarState: ZashiMainTopAppBarState?) {
    ZashiSmallTopAppBar(
        navigationAction = {
            ZashiTopAppBarCloseNavigation(onBack = state.onBack)
        },
        regularActions = {
            appBarState?.balanceVisibilityButton?.let {
                ZashiIconButton(it, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(20.dp))
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                ),
    )
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SwapDetailView(
            state =
                SwapDetailState(
                    quoteHeader =
                        SwapQuoteHeaderState(
                            from =
                                SwapTokenAmountState(
                                    bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_chain_placeholder),
                                    smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
                                    amount = stringResByDynamicNumber(0.000000421423154),
                                    fiatAmount =
                                        stringResByDynamicCurrencyNumber(
                                            0.0000000000000021312,
                                            "$",
                                        ),
                                    token = stringRes("ZEC"),
                                    chain = stringRes("Chain"),
                                ),
                            to =
                                SwapTokenAmountState(
                                    bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_chain_placeholder),
                                    smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
                                    amount = stringResByDynamicNumber(0.000000421423154),
                                    fiatAmount =
                                        stringResByDynamicCurrencyNumber(
                                            0.0000000000000021312,
                                            "$",
                                        ),
                                    token = stringRes("ZEC"),
                                    chain = stringRes("Chain"),
                                )
                        ),
                    transactionHeader =
                        TransactionDetailHeaderState(
                            title = stringRes("Swap pending"),
                            amount = stringRes(Zatoshi(1000000), HIDDEN),
                            icons =
                                listOf(
                                    imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_zec),
                                    imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_zec),
                                    imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_zec),
                                )
                        ),
                    status =
                        TransactionDetailSwapStatusRowState(
                            title = stringRes("Status"),
                            status = SwapStatus.REFUNDED,
                        ),
                    depositTo =
                        TransactionDetailInfoRowState(
                            title = stringRes("Deposit to"),
                            message = stringRes("depositTo"),
                            trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                        ),
                    recipient =
                        TransactionDetailInfoRowState(
                            title = stringRes("Recipient"),
                            message = stringRes("recipient"),
                            trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                        ),
                    totalFees =
                        TransactionDetailInfoRowState(
                            title = stringRes("Total fees"),
                            message = stringRes("totalFees"),
                        ),
                    maxSlippage =
                        TransactionDetailInfoRowState(
                            title = stringRes("Max slippage"),
                            message = stringRes("maxSlippage"),
                        ),
                    timestamp =
                        TransactionDetailInfoRowState(
                            title = stringRes("Timestamp"),
                            message = stringRes("timestamp"),
                        ),
                    errorFooter =
                        ErrorFooter(
                            stringRes("Title"),
                            stringRes("Subtitle"),
                        ),
                    primaryButton = ButtonState(stringRes("Primary"), ButtonStyle.DESTRUCTIVE1),
                    onBack = {},
                ),
            appBarState = ZashiMainTopAppBarStateFixture.new()
        )
    }
