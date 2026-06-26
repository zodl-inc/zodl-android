package co.electriccoin.zcash.ui.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

@Composable
fun ZashiTopAppBarBackNavigation(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ZashiTopAppBarNavigation(
    modifier = modifier,
    backContentDescriptionText = stringResource(R.string.general_back),
    drawableRes = R.drawable.ic_zashi_navigation_back,
    onBack = onBack,
    enabled = enabled,
)

@Composable
fun ZashiTopAppBarCloseNavigation(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) = ZashiTopAppBarNavigation(
    modifier = modifier,
    backContentDescriptionText = stringResource(R.string.general_back),
    drawableRes = R.drawable.ic_navigation_close,
    onBack = onBack,
    tint = ZashiColors.Text.textPrimary
)

@Composable
fun ZashiTopAppBarHamburgerNavigation(onBack: () -> Unit) =
    ZashiTopAppBarNavigation(
        backContentDescriptionText = stringResource(R.string.general_back),
        drawableRes = R.drawable.ic_navigation_hamburger,
        onBack = onBack,
        tint = ZashiColors.Text.textPrimary
    )

@Composable
fun ZashiTopAppBarBigCloseNavigation(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
    ) {
        Spacer(24.dp)
        Button(
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ZashiColors.Btns.Tertiary.btnTertiaryBg
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_settings_opt_int_close),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Btns.Tertiary.btnTertiaryFg)
            )
        }
    }
}

@Composable
fun ZashiTopAppBarNavigation(
    backContentDescriptionText: String,
    @DrawableRes drawableRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier.testTag(ZashiTopAppBarNavigationTag.BACK)
        ) {
            Icon(
                painter = painterResource(drawableRes),
                contentDescription = backContentDescriptionText,
                tint = tint ?: LocalContentColor.current
            )
        }
    }
}

object ZashiTopAppBarNavigationTag {
    const val BACK = "NAVIGATION_BACK"
}
