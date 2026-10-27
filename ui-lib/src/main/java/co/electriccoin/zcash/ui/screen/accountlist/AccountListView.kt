package co.electriccoin.zcash.ui.screen.accountlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ShimmerRectangle
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.component.listitem.BaseListItem
import co.electriccoin.zcash.ui.design.component.listitem.ZodlWalletListItem
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import com.valentinilk.shimmer.shimmer

/**
 * Lays the sections out in one scrolling column whose minimum height is the viewport, with a
 * weighted spacer before the promo card and the "Add Account" button: while the wallets fit, the
 * card and the button sit at the bottom of the screen; once they do not, the whole column scrolls
 * and the button follows the last section.
 */
@Composable
fun AccountListView(state: AccountListState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            AccountListTopAppBar(
                state = state,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState)
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldScrollPadding(paddingValues),
            ) {
                if (state.sections == null) {
                    LoadingSection()
                } else {
                    state.sections.forEachIndexed { index, section ->
                        if (index != 0) {
                            Spacer(Modifier.height(16.dp))
                            ZashiHorizontalDivider(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                thickness = 1.dp,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        Section(section)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.weight(1f))
                state.keystonePromo?.let {
                    KeystonePromoCard(
                        state = it,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(32.dp))
                }
                state.addAccountButton?.let {
                    ZashiButton(
                        state = it,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountListTopAppBar(
    state: AccountListState,
    modifier: Modifier = Modifier,
) {
    ZashiSmallTopAppBar(
        title = state.title.getValue(),
        modifier = modifier,
        navigationAction = {
            ZashiTopAppBarCloseNavigation(onBack = state.onBack)
        },
        colors =
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            ),
    )
}

@Composable
private fun Section(state: AccountListSectionState) {
    Column {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textTertiary,
            )
            state.count?.let {
                Spacer(Modifier.width(8.dp))
                CountTag(it.getValue())
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.items.forEach { item ->
                ZodlWalletListItem(
                    state = item,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The number of wallets in a section on a round background: a circle as tall as the 20 dp line for a
 * single digit, widening into a pill only when the count needs more room.
 */
@Composable
private fun CountTag(count: String) {
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = COUNT_TAG_SIZE, minHeight = COUNT_TAG_SIZE)
                .background(ZashiColors.Utility.Gray.utilityGray50, CircleShape)
                .padding(start = 4.dp, end = 4.dp, bottom = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

private val COUNT_TAG_SIZE = 20.dp

/**
 * The shape of one section with two rows, shimmering until the wallets load.
 */
@Composable
private fun LoadingSection() {
    Column(
        modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .shimmer(rememberZashiShimmer()),
    ) {
        ShimmerRectangle(width = 96.dp, height = 20.dp)
        Spacer(Modifier.height(12.dp))
        repeat(LOADING_ROWS) { index ->
            if (index != 0) {
                Spacer(Modifier.height(8.dp))
            }
            ShimmerRectangle(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

private const val LOADING_ROWS = 2

/**
 * Keystone's own card artwork: a dark card in both themes, its photo fading into the text panel.
 * The photo is 99 dp wide and as tall as the card, which grows past Figma's 98 dp when the title
 * wraps, so it never ends short of the card's bottom edge.
 */
@Composable
private fun KeystonePromoCard(
    state: KeystonePromoState,
    modifier: Modifier = Modifier,
) {
    BaseListItem(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = PromoBackground,
        contentPadding = PaddingValues(0.dp),
        leading = null,
        trailing = null,
        onClick = state.onClick,
        content = {
            Row(
                modifier = it.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.image is ImageResource.ByDrawable) {
                    Box(modifier = Modifier.width(99.dp).fillMaxHeight()) {
                        Image(
                            modifier = Modifier.matchParentSize(),
                            painter = painterResource(state.image.resource),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.CenterStart,
                        )
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            PROMO_FADE_START to Color.Transparent,
                                            1f to PromoBackground,
                                        )
                                    )
                        )
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 98.dp)
                            .padding(start = 16.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.textMd,
                        fontWeight = FontWeight.SemiBold,
                        color = PromoTitle,
                    )
                    Text(
                        text = state.message.getValue(),
                        style = ZashiTypography.textXs,
                        color = PromoMessage,
                    )
                }
            }
        },
    )
}

@Suppress("MagicNumber")
private val PromoBackground = Color(0xFF101213)

@Suppress("MagicNumber")
private val PromoTitle = Color(0xFFFFFFFF)

@Suppress("MagicNumber")
private val PromoMessage = Color(0xFFC0BFB1)

private const val PROMO_FADE_START = 0.49f
