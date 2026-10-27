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
import androidx.compose.foundation.layout.size
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
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarWithAccountSelection
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBigIconButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.balances.BalanceWidget
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetState
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
        // Always-present, zero-size marker mirroring iOS's AccessibilityID.Home.syncComplete/
        // .syncPending overlay. Gives e2e flows a positive, non-flaky signal for "wallet caught
        // up with the chain tip" instead of asserting on the sync banner's text/visibility,
        // which changes across states (Restoring/Resyncing/Syncing/gone) and can be replaced by
        // unrelated banners (e.g. Migration Required) once sync is actually done.
        //
        // Deliberately NOT hidden from accessibility (no hideFromAccessibility()/
        // invisibleToUser()) — that excludes a node from the accessibility tree, which is what
        // Maestro's Android driver queries to resolve `id:` selectors, so a hidden node can never
        // be found. iOS's equivalent overlay keeps itself in the accessibility tree for the same
        // reason (.accessibilityElement(children: .ignore) + a label, not a hide call).
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(1.dp)
                    .testTag(if (state.isSyncComplete) HomeTags.SYNC_COMPLETE else HomeTags.SYNC_PENDING)
        )
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

@Composable
private fun NavButtons(
    paddingValues: PaddingValues,
    state: HomeState,
    modifier: Modifier = Modifier
) {
    val buttons =
        listOfNotNull(
            state.firstButton to HomeTags.RECEIVE,
            state.secondButton?.let { it to HomeTags.SEND },
            state.thirdButton?.let { it to HomeTags.PAY },
            state.fourthButton to HomeTags.SWAP,
        )
    Row(
        modifier = modifier.scaffoldPadding(paddingValues, top = 0.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(NAV_BUTTON_SPACING)
    ) {
        buttons.forEach { (button, tag) ->
            ZashiBigIconButton(
                modifier =
                    Modifier
                        .weight(1f)
                        .then(
                            if (buttons.size == FULL_NAV_BUTTON_COUNT) {
                                Modifier.minHeight106Percent()
                            } else {
                                Modifier.minHeightOfFullRowSlot(buttonCount = buttons.size)
                            }
                        ).testTag(tag),
                state = button,
            )
        }
    }
}

private val NAV_BUTTON_SPACING = 9.dp

private const val FULL_NAV_BUTTON_COUNT = 4

/**
 * Keeps a row of fewer than [FULL_NAV_BUTTON_COUNT] buttons as tall as the full row: each button
 * gets the minimum height [minHeight106Percent] would give a button of the full row, whose width
 * follows from this button's width, the [buttonCount] and the [NAV_BUTTON_SPACING].
 */
@Suppress("MagicNumber")
private fun Modifier.minHeightOfFullRowSlot(buttonCount: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val spacing = NAV_BUTTON_SPACING.toPx()
        val fullRowSlotWidth =
            (buttonCount * placeable.width + (buttonCount - FULL_NAV_BUTTON_COUNT) * spacing) / FULL_NAV_BUTTON_COUNT
        val minHeight = (fullRowSlotWidth / (106f / 100f)).toInt()

        val newConstraints = constraints.copy(minHeight = minHeight.coerceIn(0, constraints.maxHeight))
        val newPlaceable = measurable.measure(newConstraints)

        layout(newPlaceable.width, newPlaceable.height) {
            newPlaceable.place(0, 0)
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
            balanceWidgetState = BalanceWidgetState.emptyPreview,
            transactionWidgetState = ActivityWidgetStateFixture.new(),
            state = HomeState.preview
        )
    }
}

@PreviewScreens
@Composable
private fun CannotSpendPreview() {
    ZcashTheme {
        HomeView(
            appBarState = ZashiMainTopAppBarState.previewWithSubtitle,
            balanceWidgetState = BalanceWidgetState.emptyPreview,
            transactionWidgetState = ActivityWidgetStateFixture.new(),
            state = HomeState.previewCannotSpend
        )
    }
}
