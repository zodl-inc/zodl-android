@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.swap.quote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiSwapQuoteHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.withStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapQuoteView(state: SwapQuoteState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { innerState, contentPadding ->
            when (innerState) {
                is SwapQuoteState.Success -> {
                    Success(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, false),
                        state = innerState,
                        contentPadding = contentPadding
                    )
                }

                is SwapQuoteState.Error -> {
                    Error(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, false),
                        state = innerState,
                        contentPadding = contentPadding
                    )
                }
            }
        }
    )
}

@Composable
private fun Error(
    state: SwapQuoteState.Error,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                )
    ) {
        if (state.icon is ImageResource.ByDrawable) {
            Image(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                painter = painterResource(state.icon.resource),
                contentDescription = null
            )
        }
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.subtitle.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(32.dp)
        ZashiButton(
            state = state.negativeButton,
            modifier = Modifier.fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
        )
        ZashiButton(
            state = state.positiveButton,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class SwapQuoteTab { BREAKDOWN, COMPARISON }

@Suppress("MagicNumber", "LongMethod")
@Composable
private fun Success(
    state: SwapQuoteState.Success,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                textAlign = TextAlign.Center
            )
            // Selected provider's icon (NEAR green / Maya), per MOB-1396.
            (state.providerIcon as? ImageResource.ByDrawable)?.let {
                Spacer(8.dp)
                Image(
                    modifier = Modifier.size(28.dp),
                    painter = painterResource(it.resource),
                    contentDescription = null
                )
            }
        }
        Spacer(24.dp)
        CompositionLocalProvider(LocalBalancesAvailable provides true) {
            ZashiSwapQuoteHeader(
                state =
                    SwapQuoteHeaderState(
                        from = state.from,
                        to = state.to
                    )
            )
        }

        val comparison = state.comparison
        if (comparison != null) {
            Spacer(24.dp)
            var tab by rememberSaveable { mutableStateOf(SwapQuoteTab.BREAKDOWN) }
            SwapQuoteTabRow(selected = tab, onSelect = { tab = it })
            Spacer(20.dp)
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxWidth()
            ) { targetTab ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (targetTab) {
                        SwapQuoteTab.COMPARISON -> ComparisonContent(comparison)
                        SwapQuoteTab.BREAKDOWN -> BreakdownContent(state)
                    }
                    Spacer(24.dp)
                }
            }
        } else {
            Spacer(32.dp)
            BreakdownContent(state)
        }

        if (state.infoText != null) {
            if (comparison != null) {
                Spacer(24.dp)
            } else {
                Spacer(48.dp)
            }
            ZashiInfoText(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textModifier = Modifier.padding(top = 4.dp),
                text = state.infoText.getValue()
            )
        }
        Spacer(24.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.primaryButton
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun ColumnScope.BreakdownContent(state: SwapQuoteState.Success) {
    state.items.forEachIndexed { index, item ->
        if (index != 0) {
            Spacer(12.dp)
        }
        SwapQuoteInfo(item)
    }
    Spacer(12.dp)
    ZashiHorizontalDivider()
    Spacer(12.dp)
    SwapQuoteInfo(
        item = state.amount,
        descriptionStyle = ZashiTypography.textSm,
        descriptionFontWeight = FontWeight.Medium,
        descriptionColor = ZashiColors.Text.textPrimary
    )
}

@Suppress("MagicNumber")
@Composable
private fun ComparisonContent(rows: List<SwapProviderQuoteState>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.swapAndPay_provider),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary
            )
            Spacer(1f)
            Text(
                text = stringResource(R.string.swapAndPay_youGet),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary
            )
        }
        Spacer(12.dp)
        rows.forEachIndexed { index, row ->
            if (index != 0) {
                Spacer(8.dp)
            }
            ProviderQuoteRow(row)
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun ProviderQuoteRow(state: SwapProviderQuoteState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (state.isSelected) 2.dp else 1.dp,
                    color =
                        if (state.isSelected) {
                            ZashiColors.Text.textPrimary
                        } else {
                            ZashiColors.Utility.Gray.utilityGray100
                        },
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = state.onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (state.icon as? ImageResource.ByDrawable)?.let {
            Image(
                modifier = Modifier.size(24.dp),
                painter = painterResource(it.resource),
                contentDescription = null
            )
            Spacer(8.dp)
        }
        Text(
            text = state.name.getValue(),
            style = ZashiTypography.textMd,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(1f)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = state.amount.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary
            )
            Text(
                text = state.fiatAmount.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary
            )
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun SwapQuoteTabRow(selected: SwapQuoteTab, onSelect: (SwapQuoteTab) -> Unit) {
    val spacing = 4.dp
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZashiColors.Utility.Gray.utilityGray50)
                .padding(4.dp)
    ) {
        val itemWidth = (maxWidth - spacing) / 2

        SwapQuoteTabIndicator(
            selectedIndex = if (selected == SwapQuoteTab.BREAKDOWN) 0 else 1,
            itemWidth = itemWidth,
            spacing = spacing
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            SwapQuoteTabSegment(
                text = stringResource(R.string.swapAndPay_breakdown),
                selected = selected == SwapQuoteTab.BREAKDOWN,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(SwapQuoteTab.BREAKDOWN) }
            )
            SwapQuoteTabSegment(
                text = stringResource(R.string.swapAndPay_comparison),
                selected = selected == SwapQuoteTab.COMPARISON,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(SwapQuoteTab.COMPARISON) }
            )
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun BoxScope.SwapQuoteTabIndicator(
    selectedIndex: Int,
    itemWidth: Dp,
    spacing: Dp
) {
    val offset by animateDpAsState(
        targetValue = (itemWidth + spacing) * selectedIndex,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
    )

    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(itemWidth)
                    .offset(x = offset)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZashiColors.Surfaces.bgPrimary)
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun SwapQuoteTabSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor by animateColorAsState(
        if (selected) ZashiColors.Text.textPrimary else ZashiColors.Text.textTertiary
    )

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    onClick = onClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ZashiTypography.textSm,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
fun SwapQuoteInfo(
    item: SwapQuoteInfoItem,
    descriptionStyle: TextStyle = ZashiTypography.textSm,
    descriptionFontWeight: FontWeight = FontWeight.Normal,
    descriptionColor: Color = ZashiColors.Text.textTertiary
) {
    Row {
        Text(
            modifier = Modifier.weight(1f),
            text = item.description.getValue(),
            style = descriptionStyle,
            fontWeight = descriptionFontWeight,
            color = descriptionColor
        )
        Column(
            horizontalAlignment = Alignment.End
        ) {
            SelectionContainer {
                ZashiAutoSizeText(
                    text = item.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary,
                    maxLines = 1
                )
            }
            if (item.subtitle != null) {
                SelectionContainer {
                    ZashiAutoSizeText(
                        text = item.subtitle.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@PreviewScreens
@Composable
private fun SuccessPreview() =
    ZcashTheme {
        SwapQuoteView(
            state =
                SwapQuoteState.Success(
                    from =
                        SwapTokenAmountState(
                            bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
                            amount = stringRes("0.0000004214"),
                            fiatAmount = stringResByDynamicCurrencyNumber(21312, "$"),
                            chain = stringRes("Chain"),
                            token = stringRes("Token")
                        ),
                    to =
                        SwapTokenAmountState(
                            bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_btc),
                            smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_btc),
                            amount = stringRes("0.0000004214"),
                            fiatAmount = stringResByDynamicCurrencyNumber(21312, "$"),
                            chain = stringRes("Chain"),
                            token = stringRes("Token")
                        ),
                    items =
                        listOf(
                            SwapQuoteInfoItem(
                                description = stringRes("Pay from"),
                                title = stringRes("Zodl").withStyle(),
                                subtitle = null
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("Pay to"),
                                title = stringResByAddress("Asdwae12easdasd"),
                                subtitle = null
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("ZEC transaction fee"),
                                title = stringRes(Zatoshi(1231234)).withStyle(),
                                subtitle = null
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("Max slippage 1%"),
                                title = stringRes(Zatoshi(1231234)).withStyle(),
                                subtitle = stringResByDynamicCurrencyNumber(23, "$").withStyle()
                            )
                        ),
                    amount =
                        SwapQuoteInfoItem(
                            description = stringRes("Total amount"),
                            title = stringRes(Zatoshi(123213)).withStyle(),
                            subtitle = stringResByDynamicCurrencyNumber(12312, "$").withStyle()
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("Confirm"),
                            onClick = {}
                        ),
                    onBack = {},
                    rotateIcon = false,
                    providerIcon = imageRes(R.drawable.ic_provider_near),
                    infoText = stringRes("Total amount includes max slippage of 0.5%."),
                    title = stringRes("Pay now")
                )
        )
    }

@PreviewScreens
@Composable
private fun ErrorPreview() =
    ZcashTheme {
        SwapQuoteView(
            state =
                SwapQuoteState.Error(
                    icon = imageRes(R.drawable.ic_zec_round_full),
                    title = stringRes("Title"),
                    subtitle = stringRes("Subtitle"),
                    negativeButton =
                        ButtonState(
                            text = stringRes("Negative"),
                            onClick = {}
                        ),
                    positiveButton =
                        ButtonState(
                            text = stringRes("Positive"),
                            onClick = {}
                        ),
                    onBack = {}
                )
        )
    }
