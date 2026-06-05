package co.electriccoin.zcash.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarWithAccountSelection
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBigIconButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.BalanceStateFixture
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.balances.BalanceWidget
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetState
import co.electriccoin.zcash.ui.screen.home.error.WalletErrorMessageState
import co.electriccoin.zcash.ui.screen.transactionhistory.widget.ActivityWidgetState
import co.electriccoin.zcash.ui.screen.transactionhistory.widget.ActivityWidgetStateFixture
import co.electriccoin.zcash.ui.screen.transactionhistory.widget.createActivityWidgets

@Composable
internal fun HomeView(
    appBarState: ZashiMainTopAppBarState?,
    balanceWidgetState: BalanceWidgetState,
    transactionWidgetState: ActivityWidgetState,
    state: HomeState
) {
    BlankBgScaffold(
        topBar = { ZashiTopAppBarWithAccountSelection(appBarState) }
    ) { paddingValues ->
        Content(
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + ZashiDimensions.Spacing.spacingLg),
            paddingValues = paddingValues,
            activityWidgetState = transactionWidgetState,
            balanceWidgetState = balanceWidgetState,
            state = state
        )
    }
}

@Composable
private fun Content(
    activityWidgetState: ActivityWidgetState,
    paddingValues: PaddingValues,
    balanceWidgetState: BalanceWidgetState,
    state: HomeState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(8.dp)
            BalanceWidget(
                modifier =
                    Modifier
                        .padding(
                            start = ZcashTheme.dimens.screenHorizontalSpacingRegular,
                            end = ZcashTheme.dimens.screenHorizontalSpacingRegular,
                        ),
                state = balanceWidgetState,
            )
            Spacer(16.dp)
            NavButtons(
                modifier =
                    Modifier
                        .zIndex(1f)
                        .offset(y = 8.dp),
                paddingValues = paddingValues,
                state = state
            )
            Spacer(Modifier.height(2.dp))
            OverlappingBoxes {
                HomeMessage(
                    modifier =
                        Modifier
                            .zIndex(0f),
                    state = state.message
                )
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    contentPadding = PaddingValues(top = 24.dp)
                ) {
                    createActivityWidgets(
                        state = activityWidgetState
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlappingBoxes(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val overlapPx by remember { mutableIntStateOf(with(density) { 24.dp.toPx().toInt() }) }

    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val firstBox = measurables.getOrNull(0)
        val secondBox = measurables.getOrNull(1)
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val firstPlaceable = firstBox?.measure(looseConstraints)
        val secondPlaceable = secondBox?.measure(looseConstraints)
        layout(
            width = constraints.maxWidth,
            height = constraints.maxHeight
        ) {
            firstPlaceable?.placeRelative(
                x = 0,
                y = 0,
            )
            secondPlaceable?.placeRelative(
                x = 0,
                y = ((firstPlaceable?.height ?: 0) - overlapPx).coerceAtLeast(0),
            )
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun NavButtons(
    paddingValues: PaddingValues,
    state: HomeState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.scaffoldPadding(paddingValues, top = 0.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ZashiBigIconButton(
            modifier =
                Modifier
                    .weight(1f)
                    .minHeight106Percent()
                    .testTag(HomeTags.RECEIVE),
            state = state.firstButton,
        )
        ZashiBigIconButton(
            modifier =
                Modifier
                    .weight(1f)
                    .minHeight106Percent()
                    .testTag(HomeTags.SEND),
            state = state.secondButton,
        )
        ZashiBigIconButton(
            modifier =
                Modifier
                    .minHeight106Percent()
                    .weight(1f)
                    .testTag(HomeTags.PAY),
            state = state.thirdButton,
        )
        ZashiBigIconButton(
            modifier =
                Modifier
                    .minHeight106Percent()
                    .weight(1f)
                    .testTag(HomeTags.SWAP),
            state = state.fourthButton,
        )
    }
}

@Suppress("MagicNumber")
fun Modifier.minHeight106Percent(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val minHeight = (placeable.width.toFloat() / (106f / 100f)).toInt()

        val newConstraints = constraints.copy(minHeight = minHeight.coerceAtMost(constraints.maxHeight))
        val newPlaceable = measurable.measure(newConstraints)

        layout(newPlaceable.width, newPlaceable.height) {
            newPlaceable.place(0, 0)
        }
    }

@PreviewScreens
@Composable
private fun Preview() {
    ZcashTheme {
        HomeView(
            appBarState = ZashiMainTopAppBarStateFixture.new(),
            balanceWidgetState = BalanceStateFixture.new(),
            transactionWidgetState = ActivityWidgetStateFixture.new(),
            state =
                HomeState(
                    firstButton =
                        BigIconButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_home_receive,
                            onClick = {}
                        ),
                    secondButton =
                        BigIconButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_home_send,
                            onClick = {}
                        ),
                    thirdButton =
                        BigIconButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_home_scan,
                            onClick = {}
                        ),
                    fourthButton =
                        BigIconButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_home_buy,
                            onClick = {}
                        ),
                    message = WalletErrorMessageState(onClick = {})
                )
        )
    }
}
