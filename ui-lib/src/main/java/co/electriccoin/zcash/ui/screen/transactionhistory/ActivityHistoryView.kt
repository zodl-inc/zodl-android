@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.transactionhistory

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.asScaffoldScrollPaddingValues
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.home.common.CommonEmptyScreen
import co.electriccoin.zcash.ui.screen.home.common.CommonShimmerLoadingScreen
import kotlinx.coroutines.launch

@Composable
fun ActivityHistoryView(
    state: ActivityHistoryState,
    search: TextFieldState,
    mainAppBarState: ZashiMainTopAppBarState?,
) {
    val hazeState = rememberZashiFrostState()
    val lazyListState = rememberLazyListState()
    BlankBgScaffold(
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState)
            ) {
                TransactionHistoryAppBar(
                    mainAppBarState = mainAppBarState,
                    state = state,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ZashiDimensions.Spacing.spacing3xl,
                                end = ZashiDimensions.Spacing.spacing3xl,
                                top = 8.dp
                            ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZashiTextField(
                        modifier = Modifier.weight(1f),
                        state = search,
                        singleLine = true,
                        maxLines = 1,
                        prefix = {
                            Image(
                                painter = painterResource(R.drawable.ic_transaction_search),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(ZashiColors.Dropdowns.Default.text)
                            )
                        },
                        placeholder = {
                            Text(
                                text = stringRes(stringResource(R.string.filter_search)).getValue(),
                                style = ZashiTypography.textMd,
                                color = ZashiColors.Inputs.Default.text,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    BadgeIconButton(
                        state = state.filterButton
                    )
                }

                if (state is ActivityHistoryState.Data) {
                    DockedHeaderItem(
                        state = state,
                        lazyListState = lazyListState
                    )
                }
            }
        }
    ) { paddingValues ->
        val listTopPadding = paddingValues.calculateTopPadding()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            when (state) {
                is ActivityHistoryState.Data -> {
                    Data(
                        paddingValues = paddingValues,
                        topPadding = listTopPadding,
                        state = state,
                        lazyListState = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is ActivityHistoryState.Empty -> {
                    CommonEmptyScreen(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = paddingValues.calculateTopPadding())
                    )
                }

                is ActivityHistoryState.Loading -> {
                    Loading(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = paddingValues.calculateTopPadding() + 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Data(
    paddingValues: PaddingValues,
    topPadding: Dp,
    state: ActivityHistoryState.Data,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier
) {
    val kbController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            scope.launch { kbController?.hide() }
        }
    }

    var previousFiltersId by remember { mutableStateOf(state.filtersId) }
    if (state.filtersId != previousFiltersId) {
        lazyListState.requestScrollToItem(0)
    }
    SideEffect {
        if (state.filtersId != previousFiltersId) {
            previousFiltersId = state.filtersId
        }
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = paddingValues.asScaffoldScrollPaddingValues(top = topPadding),
    ) {
        state.items.forEachIndexed { index, item ->
            when (item) {
                is ActivityHistoryItem.Header -> {
                    item(
                        contentType = item.contentType,
                        key = item.key
                    ) {
                        val isTouchingFrost by remember {
                            derivedStateOf {
                                val info =
                                    lazyListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == item.key }
                                info != null && info.offset < 0
                            }
                        }
                        val headerAlpha by animateFloatAsState(
                            targetValue = if (isTouchingFrost) 0f else 1f,
                            label = "ActivityHistoryHeaderAlpha"
                        )
                        HeaderItem(
                            item,
                            modifier =
                                Modifier
                                    .fillParentMaxWidth()
                                    .graphicsLayer { alpha = headerAlpha }
                                    .animateItem()
                        )
                    }
                }

                is ActivityHistoryItem.Activity -> {
                    item(
                        contentType = item.state.contentType,
                        key = item.state.key,
                    ) {
                        ActivityItem(
                            item = item,
                            index = index,
                            state = state,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Docked replacement for a sticky header. The Compose foundation pins stuck lazy list items to the
 * viewport edge, which hides them behind the frosted top bar, so the header of the top-most visible
 * section is derived from the list state and rendered as the bottom row of the frosted header itself.
 */
@Composable
private fun DockedHeaderItem(
    state: ActivityHistoryState.Data,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier
) {
    val currentHeader by remember(state) {
        derivedStateOf {
            val visibleCount = (lazyListState.firstVisibleItemIndex + 1).coerceAtMost(state.items.size)
            state.items
                .take(visibleCount)
                .lastOrNull { it is ActivityHistoryItem.Header } as? ActivityHistoryItem.Header
        }
    }
    val isOverlayingHeader by remember(state) {
        derivedStateOf {
            val firstVisible = state.items.getOrNull(lazyListState.firstVisibleItemIndex)
            firstVisible !is ActivityHistoryItem.Header || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }
    val dockedAlpha by animateFloatAsState(
        targetValue = if (isOverlayingHeader) 1f else 0f,
        label = "ActivityHistoryDockedHeaderAlpha"
    )

    currentHeader?.let { header ->
        Crossfade(
            modifier = modifier.graphicsLayer { alpha = dockedAlpha },
            targetState = header,
            label = "ActivityHistoryDockedHeader"
        ) {
            HeaderItem(
                it,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    CommonShimmerLoadingScreen(
        modifier = modifier,
        shimmerItemsCount = 10,
    )
}

@Composable
private fun HeaderItem(
    item: ActivityHistoryItem.Header,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = item.title.getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textTertiary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActivityItem(
    item: ActivityHistoryItem.Activity,
    index: Int,
    state: ActivityHistoryState.Data,
    modifier: Modifier = Modifier
) {
    val previousItem = if (index != 0) state.items[index - 1] else null
    val nextItem = if (index != state.items.lastIndex) state.items[index + 1] else null

    Column(
        modifier = modifier,
    ) {
        if (previousItem is ActivityHistoryItem.Header) {
            Spacer(Modifier.height(6.dp))
        }

        Activity(
            modifier = Modifier.padding(horizontal = 4.dp),
            state = item.state,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 20.dp)
        )

        if (index != state.items.lastIndex && nextItem is ActivityHistoryItem.Activity) {
            ZashiHorizontalDivider(
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else if (index != state.items.lastIndex && nextItem !is ActivityHistoryItem.Activity) {
            Spacer(
                modifier = Modifier.height(26.dp)
            )
        }
    }
}

@Composable
private fun BadgeIconButton(
    state: IconButtonState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clickable(
                    // Remove the ripple effect rather than clipping the badge icon
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = state.onClick,
                    role = Role.Button,
                )
    ) {
        Image(
            painter = painterResource(state.icon),
            contentDescription = state.contentDescription?.getValue(),
        )

        state.badge?.let {
            @Suppress("MagicNumber")
            Text(
                modifier =
                    Modifier
                        .offset(8.dp, (-8).dp)
                        .size(21.dp)
                        .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape)
                        .padding(2.dp)
                        .background(ZashiColors.Utility.Gray.utilityGray900, CircleShape)
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp),
                text = it.getValue(),
                textAlign = TextAlign.Center,
                color = ZashiColors.Surfaces.bgPrimary,
                style = ZashiTypography.textXs,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TransactionHistoryAppBar(
    mainAppBarState: ZashiMainTopAppBarState?,
    state: ActivityHistoryState
) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.general_activity),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
        },
        colors =
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            ),
        hamburgerMenuActions = {
            mainAppBarState?.balanceVisibilityButton?.let {
                ZashiIconButton(it, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(20.dp))
            }
        }
    )
}

const val EMPTY_GRADIENT_THRESHOLD = .28f

@PreviewScreens
@Composable
private fun DataPreview() =
    ZcashTheme {
        ActivityHistoryView(
            state =
                ActivityHistoryState.Data(
                    onBack = {},
                    filterButton =
                        IconButtonState(
                            icon = R.drawable.ic_transaction_filters,
                            badge = stringRes("1"),
                            onClick = {}
                        ),
                    items =
                        listOf(
                            ActivityHistoryItem.Header(stringRes("Header")),
                            ActivityHistoryItem.Activity(ActivityStateFixture.new(),),
                            ActivityHistoryItem.Activity(ActivityStateFixture.new(),),
                            ActivityHistoryItem.Header(stringRes("Header 2")),
                            ActivityHistoryItem.Activity(ActivityStateFixture.new()),
                            ActivityHistoryItem.Activity(ActivityStateFixture.new()),
                        )
                ),
            search = TextFieldState(stringRes(value = "")) {},
            mainAppBarState = ZashiMainTopAppBarStateFixture.new(),
        )
    }

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        ActivityHistoryView(
            state =
                ActivityHistoryState.Empty(
                    onBack = {},
                    filterButton =
                        IconButtonState(
                            icon = R.drawable.ic_transaction_filters,
                            badge = stringRes("1"),
                            onClick = {}
                        )
                ),
            search = TextFieldState(stringRes(value = "")) {},
            mainAppBarState = ZashiMainTopAppBarStateFixture.new(),
        )
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        ActivityHistoryView(
            state =
                ActivityHistoryState.Loading(
                    onBack = {},
                    filterButton =
                        IconButtonState(
                            icon = R.drawable.ic_transaction_filters,
                            badge = stringRes("1"),
                            onClick = {}
                        )
                ),
            search = TextFieldState(stringRes(value = "")) {},
            mainAppBarState = ZashiMainTopAppBarStateFixture.new(),
        )
    }
