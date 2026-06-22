package co.electriccoin.zcash.ui.screen.advancedsettings.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.advancedsettings.AdvancedSettingsTag
import kotlinx.collections.immutable.persistentListOf

@Composable
fun DebugView(state: DebugState) {
    BlankBgScaffold(
        topBar = {
            Toolbar(
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
                )
                if (index != state.items.lastIndex) {
                    ZashiHorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    onBack: () -> Unit
) {
    ZashiSmallTopAppBar(
        title = "Debug menu",
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
        DebugView(
            state =
                DebugState(
                    onBack = {},
                    items =
                        persistentListOf(
                            ListItemState(
                                title = stringRes(R.string.settings_recoveryPhrase),
                                bigIcon = imageRes(R.drawable.ic_advanced_settings_recovery),
                                onClick = {}
                            )
                        ),
                ),
        )
    }
