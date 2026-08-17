package co.electriccoin.zcash.ui.common.appbar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.internal.TopAppBarColors
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark

@Composable
fun ZashiTopAppbar(
    state: ZashiMainTopAppBarState?,
    title: StringResource? = null,
    showHideBalances: Boolean = true,
    colors: TopAppBarColors =
        ZcashTheme.colors.topAppBarColors orDark
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            ),
    onBack: () -> Unit,
) {
    ZashiSmallTopAppBar(
        title = title?.getValue(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        regularActions = {
            if (state?.balanceVisibilityButton != null && showHideBalances) {
                ZashiIconButton(state.balanceVisibilityButton, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(20.dp))
            }
        },
        colors = colors,
    )
}
