package co.electriccoin.zcash.ui.screen.accountlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.listitem.BaseListItem
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemColors
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDefaults
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDesignType
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
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
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(co.electriccoin.zcash.ui.R.string.account_list_title),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(Modifier.height(24.dp))
        state.items?.forEachIndexed { index, item ->
            if (index != 0) {
                Spacer(Modifier.height(8.dp))
            }

            when (item) {
                is AccountListItem.Account ->
                    ZashiAccountListItem(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        state = item.state,
                    )

                is AccountListItem.Other ->
                    ZashiKeystonePromoListItem(item)
            }
        }
        if (state.isLoading) {
            Spacer(Modifier.height(24.dp))
            LottieProgress(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        if (state.addWalletButton != null) {
            Spacer(modifier = Modifier.height(32.dp))
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
}

@Composable
private fun ZashiKeystonePromoListItem(item: AccountListItem.Other) {
    ZashiKeystonePromoListItem(
        modifier = Modifier.padding(horizontal = 4.dp),
        state = item.state,
    )
}

@Composable
private fun ZashiKeystonePromoListItem(
    state: ListItemState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    colors: ZashiListItemColors =
        ZashiListItemDefaults.primaryColors(
            backgroundColor = ZashiColors.Surfaces.bgTertiary
        ),
    below: @Composable ColumnScope.(Modifier) -> Unit = {
        Image(
            painter = painterResource(co.electriccoin.zcash.ui.R.drawable.img_keystone_promo),
            contentDescription = null
        )
    },
    content: @Composable (Modifier) -> Unit = {
        ZashiKeystonePromoListContent(
            modifier = it,
            text = state.title.getValue(),
            subtitle = state.subtitle?.getValue(),
            isEnabled = state.isEnabled
        )
    },
) {
    BaseListItem(
        modifier = modifier,
        contentPadding = contentPadding,
        leading = null,
        content = content,
        trailing = null,
        below = below,
        onClick = state.onClick.takeIf { state.isEnabled },
        border = colors.borderColor.takeIf { !it.isUnspecified }?.let { BorderStroke(1.dp, it) },
        color = colors.backgroundColor
    )
}

@Composable
private fun ZashiKeystonePromoListContent(
    text: String,
    subtitle: String?,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = ZashiTypography.textLg,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (isEnabled) {
                        ZashiColors.Text.textPrimary
                    } else {
                        ZashiColors.Text.textDisabled
                    }
            )
        }
        subtitle?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary
            )
        }
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
                badge = null,
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
        trailing = null,
        color =
            if (state.isSelected) {
                ZashiColors.Surfaces.bgSecondary
            } else {
                Color.Transparent
            },
        onClick = state.onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        AccountListView(
            state =
                AccountListState(
                    items =
                        listOf(
                            AccountListItem.Account(
                                ZashiAccountListItemState(
                                    title = stringRes("Zashi"),
                                    subtitle = stringRes("u1078r23uvtj8xj6dpdx..."),
                                    icon = R.drawable.ic_item_zashi,
                                    isSelected = true,
                                    onClick = {}
                                )
                            ),
                            AccountListItem.Other(
                                ListItemState(
                                    title = stringRes("Keystone Hardware Wallet"),
                                    subtitle = stringRes("Get a Keystone Hardware Wallet and secure your Zcash."),
                                    design = ZashiListItemDesignType.SECONDARY,
                                    onClick = {}
                                )
                            )
                        ),
                    isLoading = false,
                    onBack = {},
                    addWalletButton = ButtonState(stringRes("Connect Hardware Wallet"))
                )
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun HardwareWalletAddedPreview() =
    ZcashTheme {
        AccountListView(
            state =
                AccountListState(
                    items =
                        listOf(
                            AccountListItem.Account(
                                ZashiAccountListItemState(
                                    title = stringRes("Zodl"),
                                    subtitle = stringRes("u1078r23uvtj8xj6dpdx..."),
                                    icon = R.drawable.ic_item_zashi,
                                    isSelected = true,
                                    onClick = {}
                                )
                            ),
                            AccountListItem.Account(
                                ZashiAccountListItemState(
                                    title = stringRes("Keystone"),
                                    subtitle = stringRes("u1078r23uvtj8xj6dpdx..."),
                                    icon = R.drawable.ic_item_keystone,
                                    isSelected = false,
                                    onClick = {}
                                )
                            ),
                        ),
                    isLoading = false,
                    onBack = {},
                    addWalletButton = null
                )
        )
    }
