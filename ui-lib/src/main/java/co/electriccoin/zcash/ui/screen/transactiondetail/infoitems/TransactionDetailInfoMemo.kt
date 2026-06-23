package co.electriccoin.zcash.ui.screen.transactiondetail.infoitems

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ShimmerRectangle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactiondetail.info.TransactionDetailMemoState
import co.electriccoin.zcash.ui.screen.transactiondetail.info.TransactionDetailMemosState
import com.valentinilk.shimmer.shimmer

@Composable
fun TransactionDetailMemo(
    state: TransactionDetailMemosState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        when {
            state.memos == null -> {
                TransactionDetailLoadingMemo()
            }

            state.memos.isEmpty() -> {
                TransactionDetailInfoEmptyMemo(modifier = Modifier.fillMaxWidth())
            }

            else -> {
                state.memos.forEachIndexed { index, memo ->
                    val fullMemo = memo.content.getValue()
                    val fullMemoTooBig = fullMemo.length > MAX_MEMO_LENGTH

                    if (index > 0) {
                        Spacer(Modifier.height(8.dp))
                    }

                    if (fullMemoTooBig) {
                        ExpandableMemo(memo)
                    } else {
                        NonExpandableMemo(memo)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableMemo(state: TransactionDetailMemoState) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(isExpanded, label = "") {
        TransactionDetailInfoMemo(
            modifier = Modifier.fillMaxWidth(),
            state =
                TransactionDetailInfoMemoState(
                    content =
                        if (it) {
                            state.content
                        } else {
                            stringRes("${state.content.getValue().take(MAX_MEMO_LENGTH)}...")
                        },
                    bottomButton =
                        ButtonState(
                            text =
                                if (it) {
                                    stringRes(R.string.transactionHistory_viewLess)
                                } else {
                                    stringRes(R.string.transactionHistory_viewMore)
                                },
                            trailingIcon =
                                if (it) {
                                    co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_up_small
                                } else {
                                    co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_down_small
                                },
                            onClick = { isExpanded = !isExpanded }
                        ),
                    onClick = state.onClick
                )
        )
    }
}

@Composable
private fun NonExpandableMemo(state: TransactionDetailMemoState) {
    TransactionDetailInfoMemo(
        modifier = Modifier.fillMaxWidth(),
        state =
            TransactionDetailInfoMemoState(
                content = state.content,
                bottomButton = null,
                onClick = state.onClick
            )
    )
}

@Suppress("MagicNumber")
@Composable
private fun TransactionDetailLoadingMemo(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ZashiColors.Surfaces.bgSecondary,
    ) {
        Column(
            modifier =
                Modifier
                    .shimmer(rememberZashiShimmer())
                    .padding(12.dp)
        ) {
            ShimmerRectangle(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                color = ZashiColors.Surfaces.bgTertiary
            )
            Spacer(4.dp)
            ShimmerRectangle(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                color = ZashiColors.Surfaces.bgTertiary
            )
            Spacer(4.dp)
            ShimmerRectangle(
                modifier =
                    Modifier
                        .fillMaxWidth(.66f)
                        .height(20.dp),
                color = ZashiColors.Surfaces.bgTertiary
            )
        }
    }
}

@Composable
private fun TransactionDetailInfoMemo(
    state: TransactionDetailInfoMemoState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier then
                Modifier.clickable(
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = state.onClick,
                    role = Role.Button,
                ),
        shape = RoundedCornerShape(12.dp),
        color = ZashiColors.Surfaces.bgSecondary,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    start = if (state.bottomButton != null) 0.dp else 12.dp,
                    top = 12.dp,
                    end = if (state.bottomButton != null) 0.dp else 12.dp,
                    bottom = if (state.bottomButton != null) 0.dp else 12.dp,
                )
        ) {
            SelectionContainer {
                Text(
                    modifier =
                        Modifier.padding(
                            horizontal = if (state.bottomButton != null) 12.dp else 0.dp,
                        ),
                    text = state.content.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (state.bottomButton != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = ripple(),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = state.bottomButton.onClick,
                                role = Role.Button,
                            ).padding(12.dp),
                    verticalAlignment = CenterVertically
                ) {
                    SelectionContainer {
                        Text(
                            text = state.bottomButton.text.getValue(),
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    state.bottomButton.trailingIcon?.let {
                        Spacer(Modifier.width(6.dp))
                        Image(
                            painter = painterResource(it),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

private data class TransactionDetailInfoMemoState(
    val content: StringResource,
    val bottomButton: ButtonState?,
    val onClick: () -> Unit,
)

private const val MAX_MEMO_LENGTH = 130

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailInfoMemo(
                modifier = Modifier.fillMaxWidth(),
                state =
                    TransactionDetailInfoMemoState(
                        content = stringRes("Message "),
                        bottomButton =
                            ButtonState(
                                text = stringRes("Button"),
                                trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_down_small
                            ),
                        onClick = {}
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailMemo(
                modifier = Modifier.fillMaxWidth(),
                state = TransactionDetailMemosState(null)
            )
        }
    }

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailInfoEmptyMemo(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
