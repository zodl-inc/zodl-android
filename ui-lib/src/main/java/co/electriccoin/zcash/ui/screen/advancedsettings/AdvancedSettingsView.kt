package co.electriccoin.zcash.ui.screen.advancedsettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.SettingsListItemLeadingIcon
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.collections.immutable.persistentListOf

// TODO [#1271]: Add AdvancedSettingsView Tests
// TODO [#1271]: https://github.com/Electric-Coin-Company/zashi-android/issues/1271
@Composable
fun AdvancedSettings(
    state: AdvancedSettingsState,
) {
    BlankBgScaffold(
        topBar = {
            AdvancedSettingsTopAppBar(
                onBack = state.onBack,
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
            state.items.fastForEachIndexed { index, item ->
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
            Info()
            Spacer(modifier = Modifier.height(20.dp))
            ZashiButton(
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors(),
                state = state.deleteButton
            )
        }
    }
}

@Composable
private fun Info() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_advanced_settings_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.advanced_settings_info),
            fontSize = 12.sp,
            color = ZashiColors.Text.textTertiary,
        )
    }
}

@Composable
private fun AdvancedSettingsTopAppBar(
    onBack: () -> Unit
) {
    ZashiSmallTopAppBar(
        title = stringResource(id = R.string.advanced_settings_title),
        modifier = Modifier.testTag(AdvancedSettingsTag.ADVANCED_SETTINGS_TOP_APP_BAR),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
    )
}

@PreviewScreens
@Composable
private fun AdvancedSettingsPreview() =
    ZcashTheme {
        AdvancedSettings(
            state =
                AdvancedSettingsState(
                    onBack = {},
                    items =
                        persistentListOf(
                            ListItemState(
                                title = stringRes(R.string.advanced_settings_recovery),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings_recovery),
                                onClick = {}
                            ),
                            ListItemState(
                                title = stringRes(R.string.advanced_settings_export),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings_export),
                                onClick = {}
                            ),
                            ListItemState(
                                title = stringRes(R.string.advanced_settings_choose_server),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings_choose_server),
                                onClick = {}
                            ),
                            ListItemState(
                                title = stringRes(R.string.advanced_settings_currency_conversion),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings_currency_conversion),
                                onClick = {}
                            ),
                            ListItemState(
                                title = stringRes(R.string.advanced_settings_crash_reporting),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings_crash_reporting),
                                onClick = {}
                            )
                        ),
                    deleteButton =
                        ButtonState(
                            text = stringRes(R.string.advanced_settings_delete_button),
                            onClick = {}
                        )
                ),
        )
    }
