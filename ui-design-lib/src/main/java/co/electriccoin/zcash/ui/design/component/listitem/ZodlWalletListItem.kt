package co.electriccoin.zcash.ui.design.component.listitem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiTextOrShimmer
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * One wallet of the Wallets & Hardware screen as Figma draws it: a 40 dp circular avatar, 16 dp
 * from a Text SM Medium title over a Text XS subtitle, and an optional trailing icon action.
 *
 * The selected wallet is a filled, borderless row whose avatar carries the green check badge; every
 * other wallet is a row on the screen background with a 1 dp stroke. Both are radius 12 with
 * 10 / 12 dp padding. A null [WalletListItemState.subtitle] draws a shimmer placeholder, for a
 * balance that has not loaded yet.
 *
 * Tapping the row calls [WalletListItemState.onClick]; the trailing action is its own tap target
 * and never triggers the row. It keeps the 48 dp touch target of [ZashiIconButton] while taking the
 * 40 dp of Figma's ghost icon button in the layout, so the row stays 60 dp tall.
 *
 * For accessibility services the row reports whether it is the selected wallet, since the fill and
 * the check badge only show it visually; the state sits on the clickable node itself, the one an
 * accessibility service focuses. A trailing action without a content description is
 * decorative and left out of the accessibility tree, as a chevron that repeats the row's own action
 * is; one with a description stays a focusable button of its own.
 */
@Composable
fun ZodlWalletListItem(
    state: WalletListItemState,
    modifier: Modifier = Modifier,
) {
    val background =
        if (state.isSelected) {
            ZashiColors.Utility.Gray.utilityGray50
        } else {
            ZashiColors.Surfaces.bgPrimary
        }
    BaseListItem(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = background,
        border =
            if (state.isSelected) {
                null
            } else {
                BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary)
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        leading = null,
        clickSemantics = { selected = state.isSelected },
        content = {
            Row(
                modifier = it,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WalletAvatar(
                    icon = state.icon,
                    isSelected = state.isSelected,
                    badgeBorderColor = background,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ZashiTextOrShimmer(
                        text = state.subtitle?.getValue(),
                        shimmerWidth = 80.dp,
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailing =
            state.trailingAction?.let { action ->
                { TrailingAction(state = action, modifier = it) }
            },
        onClick = state.onClick,
    )
}

@Composable
private fun WalletAvatar(
    icon: ImageResource,
    isSelected: Boolean,
    badgeBorderColor: Color,
) {
    Box(modifier = Modifier.size(40.dp)) {
        icon.Compose(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
        )
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(6.dp, 6.dp)
                        .size(21.5.dp)
                        .background(ZashiColors.Avatars.avatarStatus, CircleShape)
                        .border(1.5.dp, badgeBorderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    modifier = Modifier.size(14.dp),
                    painter = painterResource(R.drawable.ic_wallet_list_selected_check),
                    contentDescription = null,
                )
            }
        }
    }
}

/**
 * Reports [TRAILING_ACTION_SIZE] to the row and centres the 48 dp [ZashiIconButton] on it; the
 * overflow falls inside the row's padding, which nothing clips, and Compose hit-tests a child
 * outside its parent's bounds, so the whole button stays tappable (ZodlWalletListItemTouchTargetTest
 * taps its outer ring). Without a content description it is hidden from accessibility services but
 * still reacts to touch.
 */
@Composable
private fun TrailingAction(
    state: IconButtonState,
    modifier: Modifier = Modifier,
) {
    val semanticsModifier =
        if (state.contentDescription == null) {
            modifier.clearAndSetSemantics { }
        } else {
            modifier
        }
    ZashiIconButton(
        state = state,
        modifier =
            semanticsModifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val size = TRAILING_ACTION_SIZE.roundToPx()
                layout(size, size) {
                    placeable.place((size - placeable.width) / 2, (size - placeable.height) / 2)
                }
            },
    )
}

private val TRAILING_ACTION_SIZE = 40.dp

/**
 * @param icon The wallet's 40 dp avatar.
 * @param subtitle The line under the title, typically the balance; null draws a shimmer.
 * @param isSelected Draws the filled row and the check badge of the active wallet.
 * @param trailingAction The icon action at the end of the row, a separate tap target.
 * @param onClick What tapping the row does.
 */
@Immutable
data class WalletListItemState(
    val icon: ImageResource,
    val title: StringResource,
    val subtitle: StringResource?,
    val isSelected: Boolean,
    val trailingAction: IconButtonState?,
    val onClick: () -> Unit,
)

@PreviewScreens
@Composable
private fun ZodlWalletListItemPreview() =
    ZcashTheme {
        BlankSurface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZodlWalletListItem(
                    state =
                        WalletListItemState(
                            icon = imageRes(R.drawable.ic_item_zashi),
                            title = stringRes("Zodl"),
                            subtitle = stringRes("23.4573 ZEC"),
                            isSelected = true,
                            trailingAction =
                                IconButtonState(icon = R.drawable.ic_wallet_list_chevron, onClick = {}),
                            onClick = {},
                        )
                )
                ZodlWalletListItem(
                    state =
                        WalletListItemState(
                            icon = imageRes(R.drawable.ic_item_keystone),
                            title = stringRes("Keystone"),
                            subtitle = stringRes("9000.0001 ZEC"),
                            isSelected = false,
                            trailingAction =
                                IconButtonState(icon = R.drawable.ic_wallet_list_more, onClick = {}),
                            onClick = {},
                        )
                )
                ZodlWalletListItem(
                    state =
                        WalletListItemState(
                            icon = imageRes(R.drawable.ic_item_ledger),
                            title = stringRes("A very long wallet name that does not fit"),
                            subtitle = null,
                            isSelected = false,
                            trailingAction =
                                IconButtonState(icon = R.drawable.ic_wallet_list_more, onClick = {}),
                            onClick = {},
                        )
                )
            }
        }
    }
