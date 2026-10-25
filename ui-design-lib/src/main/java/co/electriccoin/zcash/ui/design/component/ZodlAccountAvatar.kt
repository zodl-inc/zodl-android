package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

/** Draws [state] at [size]. */
@Composable
fun ZodlAccountAvatar(
    state: AccountAvatarState,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_SIZE,
) {
    Box(modifier = modifier.size(size)) {
        state.image.Compose(modifier = Modifier.size(size))
        state.editButton?.let { button ->
            AvatarEditBadge(
                state = button,
                modifier = Modifier.offset(x = size - BADGE_OFFSET, y = size - BADGE_OFFSET)
            )
        }
    }
}

/**
 * The pencil badge on an avatar: a 32 dp dark circle cut out of the screen by a 3 dp ring in the
 * screen's background colour, inside a 48 dp tap target with the design system's press-morph and
 * ripple.
 */
@Composable
private fun AvatarEditBadge(
    state: IconButtonState,
    modifier: Modifier = Modifier,
) {
    ZodlCircleTapTarget(
        onClick = state.onClick,
        modifier = modifier,
        isEnabled = state.isEnabled,
        onClickLabel = state.contentDescription?.getValue(),
    ) {
        Box(
            modifier =
                Modifier
                    .size(BADGE_SIZE + BADGE_RING * 2)
                    .background(ZashiColors.Surfaces.bgPrimary, CircleShape)
                    .padding(BADGE_RING)
                    .background(ZashiColors.Btns.Primary.btnPrimaryBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(state.icon),
                contentDescription = state.contentDescription?.getValue(),
                modifier = Modifier.size(BADGE_ICON_SIZE),
                colorFilter = ColorFilter.tint(ZashiColors.Btns.Primary.btnPrimaryFg)
            )
        }
    }
}

/**
 * An account avatar: [image], a drawable or a [ImageResource.Layered] circle in the account's
 * colour, and [editButton], the pencil badge that changes the colour.
 */
@Immutable
data class AccountAvatarState(
    val image: ImageResource,
    val editButton: IconButtonState? = null,
) {
    companion object {
        /** [image] with the pencil badge that opens the colour picker through [onEditClick], none without it. */
        fun create(
            image: ImageResource,
            onEditClick: (() -> Unit)? = null,
        ) = AccountAvatarState(
            image = image,
            editButton =
                onEditClick?.let {
                    IconButtonState(
                        icon = R.drawable.ic_avatar_edit,
                        contentDescription = stringRes(R.string.colorPicker_edit),
                        onClick = it
                    )
                },
        )
    }
}

private val DEFAULT_SIZE = 90.dp
private val BADGE_SIZE = 32.dp
private val BADGE_RING = 3.dp
private val BADGE_ICON_SIZE = 16.dp

/** How far the badge's centre sits in from the avatar's bottom and right edges, from Figma. */
private val BADGE_INSET = 8.dp

private val BADGE_OFFSET = BADGE_INSET + TAP_TARGET_SIZE / 2

@PreviewScreens
@Composable
private fun DefaultPreview() =
    ZcashTheme {
        BlankSurface {
            ZodlAccountAvatar(
                state =
                    AccountAvatarState(
                        image = imageRes(R.drawable.ic_item_keystone),
                        editButton = IconButtonState(icon = R.drawable.ic_avatar_edit) {}
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun ColoredPreview() =
    ZcashTheme {
        BlankSurface {
            ZodlAccountAvatar(
                state =
                    AccountAvatarState(
                        image =
                            ImageResource.Layered(
                                background = Color(0xFFBE38F3),
                                foreground = ImageResource.ByDrawable(R.drawable.ic_avatar_edit),
                            ),
                        editButton = IconButtonState(icon = R.drawable.ic_avatar_edit) {}
                    )
            )
        }
    }
