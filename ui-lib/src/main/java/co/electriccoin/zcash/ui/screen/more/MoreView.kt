package co.electriccoin.zcash.ui.screen.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.SettingsListItemLeadingIcon
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiVersion
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.collections.immutable.persistentListOf

@Composable
fun MoreView(state: MoreState) {
    BlankBgScaffold(
        topBar = {
            SettingsTopAppBar(
                onBack = state.onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldScrollPadding(paddingValues),
        ) {
            state.items.forEachIndexed { index, item ->
                ZashiListItem(
                    state = item,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    leading =
                        (item.bigIcon as? ImageResource.ByDrawable)?.let { icon ->
                            { modifier ->
                                SettingsListItemLeadingIcon(
                                    modifier = modifier,
                                    drawableRes = icon.resource,
                                    contentDescription = item.title.getValue()
                                )
                            }
                        }
                )
                if (index != state.items.lastIndex) {
                    ZashiHorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))
            Spacer(modifier = Modifier.weight(1f))
            ZashiVersion(
                modifier = Modifier.fillMaxWidth(),
                version = state.version,
                onLongClick = state.onVersionLongClick,
                onDoubleClick = state.onVersionDoubleClick
            )
        }
    }
}

@Composable
private fun SettingsTopAppBar(onBack: () -> Unit) {
    ZashiSmallTopAppBar(
        title = stringResource(id = R.string.settings_title),
        modifier = Modifier.testTag(MoreTags.SETTINGS_TOP_APP_BAR),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        }
    )
}

@PreviewScreens
@Composable
private fun PreviewMoreView() {
    ZcashTheme {
        MoreView(
            state =
                MoreState(
                    version = stringRes("Version 1.2"),
                    onBack = {},
                    items =
                        persistentListOf(
                            ListItemState(
                                title = stringRes(R.string.settings_address_book),
                                bigIcon = imageRes(R.drawable.ic_settings_address_book),
                                onClick = { },
                            ),
                            ListItemState(
                                title = stringRes(R.string.settings_advanced_settings),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings),
                                onClick = { },
                            ),
                            ListItemState(
                                title = stringRes(R.string.settings_about_us),
                                bigIcon = imageRes(R.drawable.ic_settings_info),
                                onClick = { },
                            ),
                            ListItemState(
                                title = stringRes(R.string.settings_feedback),
                                bigIcon = imageRes(R.drawable.ic_settings_feedback),
                                onClick = { },
                            ),
                        ),
                    onVersionLongClick = {},
                    onVersionDoubleClick = {}
                ),
        )
    }
}

@PreviewScreens
@Composable
private fun IntegrationsDisabledPreview() {
    ZcashTheme {
        MoreView(
            state =
                MoreState(
                    version = stringRes("Version 1.2"),
                    onBack = {},
                    items =
                        persistentListOf(
                            ListItemState(
                                title = stringRes(R.string.settings_address_book),
                                bigIcon = imageRes(R.drawable.ic_settings_address_book),
                                onClick = { },
                            ),
                            ListItemState(
                                title = stringRes(R.string.settings_advanced_settings),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings),
                                onClick = { },
                            ),
                            ListItemState(
                                title = stringRes(R.string.settings_about_us),
                                bigIcon = imageRes(R.drawable.ic_settings_info),
                                onClick = { },
                            ),
                            ListItemState(
                                title = stringRes(R.string.settings_feedback),
                                bigIcon = imageRes(R.drawable.ic_settings_feedback),
                                onClick = { },
                            ),
                        ),
                    onVersionLongClick = {},
                    onVersionDoubleClick = {}
                ),
        )
    }
}
