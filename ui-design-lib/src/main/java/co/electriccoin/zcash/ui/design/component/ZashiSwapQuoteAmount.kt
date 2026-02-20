package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.orHiddenString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import com.valentinilk.shimmer.shimmer
import java.math.BigDecimal

@Composable
internal fun ZashiSwapQuoteAmount(
    state: SwapTokenAmountState?,
    modifier: Modifier = Modifier,
    isMirrored: Boolean = false,
) {
    Box(
        modifier = modifier.padding(ZashiDimensions.Spacing.spacingXl),
    ) {
        Layout(
            state = state,
            isMirrored = isMirrored,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Layout(
    state: SwapTokenAmountState?,
    isMirrored: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier.then(
                if (state == null) {
                    Modifier.shimmer(rememberZashiShimmer())
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = if (isMirrored) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMirrored) {
                TopBottom(state, false)
                Spacer(16.dp)
                ShimmerableIcon(state)
            } else {
                ShimmerableIcon(state)
                Spacer(16.dp)
                TopBottom(state, true)
            }
        }
        ZashiHorizontalDivider(
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = ZashiDimensions.Spacing.spacingXl),
            color = ZashiColors.Surfaces.bgTertiary,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isMirrored) Alignment.End else Alignment.Start
        ) {
            ShimmerableText(
                text = state?.let { it.amount orHiddenString stringRes(R.string.hide_balance_placeholder) },
                shimmerText = stringResByNumber(BigDecimal(".123456")).getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            ShimmerableText(
                text = state?.let { it.fiatAmount orHiddenString stringRes(R.string.hide_balance_placeholder) },
                shimmerText = stringResByNumber(BigDecimal(".123")).getValue(),
                style = ZashiTypography.textXxs,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@Composable
private fun RowScope.TopBottom(state: SwapTokenAmountState?, end: Boolean) {
    Column(
        horizontalAlignment = if (end) Alignment.Start else Alignment.End,
        modifier = Modifier.weight(1f)
    ) {
        ShimmerableText(
            text = state?.token?.getValue(),
            shimmerText = stringResByNumber(BigDecimal(".123456")).getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
        )
        ShimmerableText(
            text = state?.chain?.getValue(),
            shimmerText = stringResByNumber(BigDecimal(".123456")).getValue(),
            style = ZashiTypography.textXxs.copy(lineHeight = 10.sp),
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary,
            maxLines = 2,
            textAlign = if (end) TextAlign.Start else TextAlign.End
        )
    }
}

@Composable
private fun ShimmerableText(
    text: String?,
    shimmerText: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    if (text == null) {
        with(
            measureTextStyle(
                text = shimmerText,
                style = style.copy(fontWeight = fontWeight ?: style.fontWeight),
            )
        ) {
            ShimmerRectangle(
                modifier =
                    Modifier
                        .width(size.widthDp)
                        .height(size.heightDp)
                        .padding(1.dp),
                color = ZashiColors.Surfaces.bgTertiary,
            )
        }
    } else {
        ZashiAutoSizeText(
            modifier = modifier,
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            textAlign = textAlign
        )
    }
}

@Composable
private fun ShimmerableIcon(state: SwapTokenAmountState?) {
    if (state == null) {
        Box {
            ShimmerCircle(
                size = 28.dp,
                color = ZashiColors.Surfaces.bgTertiary
            )
            Box(
                modifier =
                    Modifier
                        .offset(4.dp, 4.dp)
                        .size(12.dp)
                        .border(1.dp, ZashiColors.Surfaces.bgSecondary, CircleShape)
                        .align(Alignment.BottomEnd)
                        .background(ZashiColors.Surfaces.bgTertiary, CircleShape)
            )
        }
    } else {
        Icon(state)
    }
}

@Composable
private fun Icon(state: SwapTokenAmountState) {
    if (state.bigIcon is ImageResource.ByDrawable) {
        Box {
            Image(
                modifier = Modifier.size(28.dp),
                painter = painterResource(state.bigIcon.resource),
                contentDescription = null
            )

            if (state.smallIcon is ImageResource.ByDrawable) {
                if (state.smallIcon.resource in
                    listOf(R.drawable.ic_zec_shielded, R.drawable.ic_zec_unshielded)
                ) {
                    Image(
                        modifier =
                            Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .offset(4.dp, 4.dp),
                        painter = painterResource(state.smallIcon.resource),
                        contentDescription = null,
                    )
                } else {
                    Surface(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .offset(4.dp, 4.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ZashiColors.Surfaces.bgPrimary)
                    ) {
                        Image(
                            modifier = Modifier.size(14.dp),
                            painter = painterResource(state.smallIcon.resource),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Immutable
data class SwapTokenAmountState(
    val amount: StringResource,
    val fiatAmount: StringResource,
    val token: StringResource,
    val chain: StringResource,
    val bigIcon: ImageResource,
    val smallIcon: ImageResource,
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            Column {
                ZashiSwapQuoteAmount(
                    modifier = Modifier.fillMaxWidth(.75f),
                    state =
                        SwapTokenAmountState(
                            token = stringRes("ZEC"),
                            chain = stringRes("Chain"),
                            bigIcon = imageRes(R.drawable.ic_chain_placeholder),
                            smallIcon = imageRes(R.drawable.ic_zec_shielded),
                            amount = stringRes("0.1231231"),
                            fiatAmount = stringRes("$123.45")
                        )
                )
                ZashiSwapQuoteAmount(
                    modifier = Modifier.fillMaxWidth(.75f),
                    isMirrored = true,
                    state =
                        SwapTokenAmountState(
                            token = stringRes("ZEC"),
                            chain = stringRes("Chain"),
                            bigIcon = imageRes(R.drawable.ic_chain_placeholder),
                            smallIcon = imageRes(R.drawable.ic_zec_shielded),
                            amount = stringRes("0.1231231"),
                            fiatAmount = stringRes("$123.45")
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
            Column {
                ZashiSwapQuoteAmount(
                    state = null
                )
                ZashiSwapQuoteAmount(
                    state = null,
                    isMirrored = true
                )
            }
        }
    }
