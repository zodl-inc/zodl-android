package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
    ) {
        if (state == null) Loading() else Data(state = state)
    }
}

@Composable
private fun Loading() {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .shimmer(rememberZashiShimmer()),
    ) {
        Box {
            ShimmerCircle(
                size = 20.dp,
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
        Spacer(8.dp)
        with(
            measureTextStyle(
                text = stringResByNumber(BigDecimal(".123456")).getValue(),
                style = ZashiTypography.textSm.copy(fontWeight = FontWeight.SemiBold),
            )
        ) {
            ShimmerRectangle(
                width = size.widthDp,
                height = size.heightDp - 1.dp,
                color = ZashiColors.Surfaces.bgTertiary
            )
        }
        Spacer(2.dp)
        with(
            measureTextStyle(
                text = stringResByNumber(BigDecimal(".123456")).getValue(),
                style = ZashiTypography.textXs.copy(fontWeight = FontWeight.Medium),
            )
        ) {
            ShimmerRectangle(
                width = size.widthDp,
                height = size.heightDp - 1.dp,
                color = ZashiColors.Surfaces.bgTertiary
            )
        }
        Spacer(8.dp)
        with(
            measureTextStyle(
                text = stringResByNumber(BigDecimal(".123456")).getValue(),
                style = ZashiTypography.textSm.copy(fontWeight = FontWeight.Medium),
            )
        ) {
            ShimmerRectangle(
                width = size.widthDp,
                height = size.heightDp - 1.dp,
                color = ZashiColors.Surfaces.bgTertiary
            )
        }
        Spacer(2.dp)
        with(
            measureTextStyle(
                text = stringResByNumber(BigDecimal(".123")).getValue(),
                style = ZashiTypography.textXxs.copy(fontWeight = FontWeight.Medium),
            )
        ) {
            ShimmerRectangle(
                width = size.widthDp,
                height = size.heightDp - 1.dp,
                color = ZashiColors.Surfaces.bgTertiary
            )
        }
    }
}

@Composable
private fun Data(state: SwapTokenAmountState) {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.bigIcon is ImageResource.ByDrawable) {
                Box {
                    Image(
                        modifier = Modifier.size(20.dp),
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
        Spacer(8.dp)
        ZashiAutoSizeText(
            text = state.token.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            maxLines = 1,
        )
        ZashiAutoSizeText(
            text = state.chain.getValue(),
            style = ZashiTypography.textXs,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary,
            maxLines = 1,
        )
        Spacer(8.dp)
        ZashiAutoSizeText(
            modifier = Modifier.fillMaxWidth(),
            text = state.amount orHiddenString stringRes(R.string.hide_balance_placeholder),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary,
            maxLines = 1,
        )
        ZashiAutoSizeText(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            text = state.fiatAmount orHiddenString stringRes(R.string.hide_balance_placeholder),
            style = ZashiTypography.textXxs,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary,
            maxLines = 1,
        )
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
        }
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        BlankSurface {
            ZashiSwapQuoteAmount(
                state = null
            )
        }
    }
