package co.electriccoin.zcash.ui.screen.accountlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.listitem.BaseListItem
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDefaults
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import kotlinx.collections.immutable.persistentListOf

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AccountListView(
    state: AccountListState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        dragHandle = null,
        content = { state, contentPadding ->
            BottomSheetContent(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f, false)
            )
        },
    )
}

@Composable
private fun BottomSheetContent(
    state: AccountListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val hazeState = rememberZashiFrostState()
    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .zashiFrostSource(hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = headerHeight,
                        bottom = contentPadding.calculateBottomPadding()
                    )
        ) {
            state.items?.forEachIndexed { index, item ->
                if (index != 0) {
                    Spacer(Modifier.height(8.dp))
                }
                ZashiAccountListItem(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    state = item,
                )
            }
            if (state.isLoading) {
                Spacer(Modifier.height(24.dp))
                LottieProgress(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            if (state.addWalletButton != null) {
                Spacer(modifier = Modifier.height(24.dp))
                ZashiButton(
                    state = state.addWalletButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    defaultPrimaryColors =
                        ZashiButtonDefaults.secondaryColors(
                            borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                        )
                )
            }
        }

        ZashiFrostedSheetHeader(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            onHeightChanged = { headerHeight = it },
            title = {
                Text(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    text = stringResource(co.electriccoin.zcash.ui.R.string.keystone_drawer_title),
                    style = ZashiTypography.textXl,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        )
    }
}

@Composable
private fun ZashiAccountListItem(
    state: ZashiAccountListItemState,
    modifier: Modifier = Modifier,
) {
    BaseListItem(
        modifier = modifier,
        contentPadding = ZashiListItemDefaults.contentPadding,
        leading = {
            ZashiListItemDefaults.LeadingItem(
                modifier = it,
                icon = imageRes(state.icon),
                badge =
                    if (state.isSelected) {
                        imageRes(co.electriccoin.zcash.ui.R.drawable.ic_account_selected_badge)
                    } else {
                        null
                    },
                contentDescription = state.title.getValue()
            )
        },
        content = {
            ZashiListItemDefaults.ContentItem(
                modifier = it,
                text = state.title.getValue(),
                subtitle = state.subtitle.getValue(),
                titleIcons = persistentListOf(),
                isEnabled = true
            )
        },
        trailing = {
            ZashiListItemDefaults.TrailingItem(
                modifier = it,
                contentDescription = state.title.getValue()
            )
        },
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
        onClick = state.onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        AccountListView(state = AccountListState.preview)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun HWWalletAddedPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewMaxed)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewLoading)
    }
