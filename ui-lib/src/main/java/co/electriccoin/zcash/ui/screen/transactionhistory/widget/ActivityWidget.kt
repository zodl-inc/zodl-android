package co.electriccoin.zcash.ui.screen.transactionhistory.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.common.CommonShimmerLoadingScreen
import co.electriccoin.zcash.ui.screen.transactionhistory.Activity

fun LazyListScope.createActivityWidgets(state: ActivityWidgetState) {
    when (state) {
        is ActivityWidgetState.Data -> activityWidgets(state)
        is ActivityWidgetState.Empty -> emptyWidget(state)
        ActivityWidgetState.Loading -> loadingWidget()
    }
}

private fun LazyListScope.activityWidgets(state: ActivityWidgetState.Data) {
    item {
        ActivityWidgetHeader(
            state = state.header,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
    }

    itemsIndexed(
        items = state.transactions,
        key = { _, item -> item.key },
        contentType = { _, item -> item.contentType }
    ) { index, item ->
        Column(
            modifier = Modifier.animateItem()
        ) {
            Activity(
                state = item,
                modifier = Modifier.padding(horizontal = 4.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 20.dp)
            )

            if (index != state.transactions.lastIndex) {
                ZashiHorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private fun LazyListScope.emptyWidget(state: ActivityWidgetState.Empty) {
    item {
        Box {
            CommonShimmerLoadingScreen(
                modifier = Modifier.padding(top = 32.dp),
                shimmerItemsCount = 2,
                contentPaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                disableShimmer = !state.enableShimmer,
                showDivider = false
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    EMPTY_GRADIENT_THRESHOLD to ZashiColors.Surfaces.bgPrimary,
                                    1f to ZashiColors.Surfaces.bgPrimary,
                                )
                        ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(72.dp))
                Image(
                    painter = painterResource(R.drawable.ic_transaction_widget_empty),
                    contentDescription = null,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.transactionHistory_nothingHere),
                    color = ZashiColors.Text.textPrimary,
                    style = ZashiTypography.textLg,
                    fontWeight = FontWeight.SemiBold
                )
                state.subtitle?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it.getValue(),
                        color = ZashiColors.Text.textTertiary,
                        style = ZashiTypography.textSm,
                    )
                }
                // state.sendTransaction?.let {
                //     Spacer(Modifier.height(20.dp))
                //     ZashiButton(
                //         state = it,
                //         defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors(),
                //     )
                // }
            }
        }
    }
}

private fun LazyListScope.loadingWidget() {
    item {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier =
                        Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = 24.dp),
                    text = stringResource(R.string.general_activity),
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = ZashiTypography.textLg
                )
                Spacer(Modifier.height(32.dp))
            }
            Spacer(Modifier.height(10.dp))
            CommonShimmerLoadingScreen(
                modifier = Modifier.fillParentMaxWidth(),
                shimmerItemsCount = 5
            )
        }
    }
}

private const val EMPTY_GRADIENT_THRESHOLD = .41f

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            LazyColumn {
                createActivityWidgets(
                    state = ActivityWidgetStateFixture.new()
                )
            }
        }
    }

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        BlankSurface {
            LazyColumn {
                createActivityWidgets(
                    state =
                        ActivityWidgetState.Empty(
                            subtitle = stringRes(R.string.transaction_history_widget_empty_subtitle),
                            sendTransaction =
                                ButtonState(
                                    text = stringRes("Send a transaction"),
                                    onClick = {}
                                ),
                            enableShimmer = true
                        )
                )
            }
        }
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        BlankSurface {
            LazyColumn {
                createActivityWidgets(
                    state = ActivityWidgetState.Loading
                )
            }
        }
    }
