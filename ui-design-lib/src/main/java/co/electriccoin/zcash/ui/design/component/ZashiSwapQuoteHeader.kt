package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber

@Suppress("MagicNumber")
@Composable
fun ZashiSwapQuoteHeader(
    state: SwapQuoteHeaderState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        color = ZashiColors.Surfaces.bgSecondary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZashiSwapQuoteAmount(
                modifier = Modifier.weight(1f),
                state = state.from
            )
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = ZashiColors.Btns.Secondary.btnSecondaryBg
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null
                    )
                }
            }
            ZashiSwapQuoteAmount(
                modifier = Modifier.weight(1f),
                state = state.to,
                isMirrored = true,
            )
        }
    }
}

@Immutable
data class SwapQuoteHeaderState(
    val from: SwapTokenAmountState?,
    val to: SwapTokenAmountState?,
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZashiSwapQuoteHeader(
                state =
                    SwapQuoteHeaderState(
                        from =
                            SwapTokenAmountState(
                                bigIcon = imageRes(R.drawable.ic_chain_placeholder),
                                smallIcon = imageRes(R.drawable.ic_zec_shielded),
                                amount = stringResByDynamicCurrencyNumber(0.000000421423154, "", TickerLocation.HIDDEN),
                                fiatAmount = stringResByDynamicCurrencyNumber(0.0000000000000021312, "$"),
                                token = stringRes("ZEC"),
                                chain = stringRes("Chain"),
                            ),
                        to =
                            SwapTokenAmountState(
                                bigIcon = imageRes(R.drawable.ic_chain_placeholder),
                                smallIcon = imageRes(R.drawable.ic_zec_shielded),
                                amount = stringResByDynamicCurrencyNumber(0.000000421423154, "", TickerLocation.HIDDEN),
                                fiatAmount = stringResByDynamicCurrencyNumber(0.0000000000000021312, "$"),
                                token = stringRes("ZEC"),
                                chain = stringRes("Chain"),
                            )
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        BlankSurface {
            ZashiSwapQuoteHeader(
                state =
                    SwapQuoteHeaderState(
                        from = null,
                        to = null
                    )
            )
        }
    }
