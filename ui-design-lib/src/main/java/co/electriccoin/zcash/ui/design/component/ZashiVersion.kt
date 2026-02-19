package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ZashiVersion(
    version: StringResource,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null
) {
    Column(
        modifier =
            modifier then
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                        onDoubleClick = onDoubleClick,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                } else {
                    Modifier
                }
    ) {
        Image(
            modifier = Modifier.align(CenterHorizontally).width(79.dp),
            painter =
                painterResource(id = R.drawable.app_logo),
            contentDescription = version.getValue()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier.align(CenterHorizontally),
            text = version.getValue(),
            color = ZashiColors.Text.textTertiary
        )
    }
}

@PreviewScreens
@Composable
private fun ZashiVersionPreview() =
    ZcashTheme {
        BlankSurface {
            ZashiVersion(version = stringRes("Version"))
        }
    }
