package co.electriccoin.zcash.ui.screen.swap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiAssetCard
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber

@Composable
internal fun SwapAmountText(
    state: SwapAmountTextState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
    ) {
        Column {
            Row {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary
                )
                if (state.subtitle != null) {
                    Spacer(1f)
                    SelectionContainer {
                        ZashiAutoSizeText(
                            text = state.subtitle.getValue(),
                            style = ZashiTypography.textSm,
                            fontWeight = FontWeight.Medium,
                            color = ZashiColors.Text.textTertiary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(8.dp)
            Row(
                verticalAlignment = CenterVertically
            ) {
                ZashiAssetCard(
                    state = state.token
                )
                Spacer(1f)
                Spacer(4.dp)
                SelectionContainer {
                    ZashiAutoSizeText(
                        text = state.text.getValue(),
                        style = ZashiTypography.header4,
                        fontWeight = FontWeight.SemiBold,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 1
                    )
                }
            }
            Spacer(8.dp)
            Row {
                SelectionContainer(
                    modifier = Modifier.weight(1f),
                ) {
                    ZashiAutoSizeText(
                        modifier = Modifier.fillMaxWidth(),
                        text = state.secondaryText?.getValue().orEmpty(),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 1,
                        contentAlignment = Alignment.CenterEnd,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Immutable
data class SwapAmountTextState(
    val token: AssetCardState,
    val title: StringResource,
    val subtitle: StringResource?,
    val text: StringResource,
    val secondaryText: StringResource?,
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            SwapAmountText(
                state =
                    SwapAmountTextState(
                        token =
                            AssetCardState.Data(
                                token = stringRes(value = "ZEC"),
                                bigIcon = null,
                                smallIcon = null,
                                isEnabled = true,
                                onClick = {}
                            ),
                        title = stringRes("To"),
                        subtitle = stringRes("Max"),
                        text = stringResByDynamicCurrencyNumber(101, "$"),
                        secondaryText = stringResByDynamicCurrencyNumber(2.47123, "ZEC")
                    )
            )
        }
    }
