package co.electriccoin.zcash.ui.screen.transactiondetail

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.OverlappingIconsState
import co.electriccoin.zcash.ui.design.component.ZashiOverlappingIcons
import co.electriccoin.zcash.ui.design.component.ZashiTextOrShimmer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StringResourceColor.QUARTERNARY
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.orHiddenString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle

@Suppress("MagicNumber")
@Composable
fun TransactionDetailHeader(
    state: TransactionDetailHeaderState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier then
                if (state.onLongClick != null) {
                    Modifier.combinedClickable(
                        indication = null,
                        onClick = {},
                        onLongClick = state.onLongClick,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                } else {
                    Modifier
                },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZashiOverlappingIcons(OverlappingIconsState(state.icons))
        Spacer(Modifier.height(10.dp))
        ZashiTextOrShimmer(
            text = state.title?.getValue(),
            shimmerWidth = 120.dp,
            style = ZashiTypography.textLg,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(Modifier.height(2.dp))
        SelectionContainer {
            Row {
                ZashiTextOrShimmer(
                    text =
                        if (state.amount == null) {
                            null
                        } else {
                            val header =
                                state.amount.withStyle() +
                                    stringRes(" ZEC").withStyle(StyledStringStyle(color = QUARTERNARY))
                            header orHiddenString stringRes(R.string.general_hideBalancesMost)
                        },
                    shimmerWidth = 178.dp,
                    style = ZashiTypography.header3,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        }
    }
}

data class TransactionDetailHeaderState(
    val title: StringResource?,
    val amount: StringResource?,
    val icons: List<ImageResource>,
    val onLongClick: (() -> Unit)? = null
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailHeader(
                state =
                    TransactionDetailHeaderState(
                        title = stringRes("Sending"),
                        amount = stringRes(Zatoshi(100000000), HIDDEN),
                        icons =
                            listOf(
                                imageRes(R.drawable.ic_chain_zec),
                                imageRes(R.drawable.ic_chain_zec),
                                imageRes(R.drawable.ic_chain_zec),
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
            TransactionDetailHeader(
                state =
                    TransactionDetailHeaderState(
                        title = null,
                        amount = null,
                        icons =
                            listOf(
                                loadingImageRes(),
                                imageRes(R.drawable.ic_chain_zec),
                                imageRes(R.drawable.ic_chain_zec),
                            )
                    )
            )
        }
    }
