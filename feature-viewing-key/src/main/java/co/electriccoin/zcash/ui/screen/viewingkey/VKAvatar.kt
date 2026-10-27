package co.electriccoin.zcash.ui.screen.viewingkey

import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.design.component.AccountAvatarState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.viewingkey.R

/**
 * The view-only account avatar: the eye on the default circle, or on [color] once there is one,
 * with the pencil badge that opens the colour picker when [onEditClick] is given.
 */
internal fun vkAvatar(
    color: Color? = null,
    onEditClick: (() -> Unit)? = null,
) = AccountAvatarState.create(
    image =
        ImageResource.Layered(
            background = color ?: Color.Unspecified,
            foreground = ImageResource.ByDrawable(R.drawable.view_only_ic_avatar_eye)
        ),
    onEditClick = onEditClick,
)
