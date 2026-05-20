package co.electriccoin.zcash.ui.screen.voting.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun VoteAppBar(
    title: String,
    onBack: () -> Unit,
    useCloseNavigation: Boolean = false,
    onConfigSettings: (() -> Unit)? = null,
) {
    ZashiSmallTopAppBar(
        title = title,
        navigationAction = {
            if (useCloseNavigation) {
                ZashiTopAppBarCloseNavigation(
                    onBack = onBack,
                    modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                )
            } else {
                ZashiTopAppBarBackNavigation(
                    onBack = onBack,
                    modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                )
            }
        },
        regularActions =
            if (onConfigSettings != null) {
                {
                    ZashiIconButton(
                        state =
                            IconButtonState(
                                icon = DesignR.drawable.ic_app_bar_settings,
                                contentDescription = stringRes(R.string.vote_chain_config_settings_content_description),
                                onClick = onConfigSettings
                            ),
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                null
            },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                )
    )
}
