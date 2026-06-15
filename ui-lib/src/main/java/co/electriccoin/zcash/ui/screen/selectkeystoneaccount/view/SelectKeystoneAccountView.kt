package co.electriccoin.zcash.ui.screen.selectkeystoneaccount.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiCheckboxListItem
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiCheckboxListItemState
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxListItem
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxListItemState
import co.electriccoin.zcash.ui.design.component.listitem.checkbox.ZashiExpandedCheckboxRowState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.selectkeystoneaccount.model.SelectKeystoneAccountState

@Composable
fun SelectKeystoneAccountView(state: SelectKeystoneAccountState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = {
                    ZashiTopAppBarCloseNavigation(state.onBackClick)
                }
            )
        }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldScrollPadding(it)
        ) {
            HeaderSection(state = state, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(48.dp))
            Content(state)
            Spacer(Modifier.weight(1f))
            BottomSection(state = state, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun Content(state: SelectKeystoneAccountState) {
    Column {
        state.items.forEachIndexed { index, item ->
            if (index != 0) {
                ZashiHorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            }

            when (item) {
                is ZashiCheckboxListItemState -> {
                    ZashiCheckboxListItem(
                        state = item,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }

                is ZashiExpandedCheckboxListItemState -> {
                    ZashiExpandedCheckboxListItem(
                        state = item,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSection(
    state: SelectKeystoneAccountState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
    ) {
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.positiveButtonState
        )
    }
}

@Composable
private fun HeaderSection(
    state: SelectKeystoneAccountState,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Image(
            modifier = Modifier.height(32.dp),
            painter = painterResource(R.drawable.image_keystone),
            contentDescription = null
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
    }
}

@PreviewScreens
@Composable
private fun SinglePreview() =
    ZcashTheme {
        SelectKeystoneAccountView(
            state =
                SelectKeystoneAccountState(
                    onBackClick = {},
                    title = stringRes("Confirm Account to Access"),
                    subtitle =
                        stringRes(
                            "Select the wallet you'd like to connect to proceed. " +
                                "Once connected, you’ll be able to wirelessly sign transactions " +
                                "with your hardware wallet."
                        ),
                    items =
                        listOf(
                            ZashiExpandedCheckboxListItemState(
                                title = stringRes("title"),
                                subtitle = stringRes("subtitle").withStyle(),
                                icon = R.drawable.ic_item_zashi,
                                isSelected = false,
                                info =
                                    ZashiExpandedCheckboxRowState(
                                        title = stringRes("title"),
                                        subtitle = stringRes("subtitle")
                                    ),
                                onClick = {}
                            )
                        ),
                    positiveButtonState = ButtonState(stringRes("Unlock")),
                )
        )
    }

@PreviewScreens
@Composable
private fun MultiplePreview() =
    ZcashTheme {
        SelectKeystoneAccountView(
            state =
                SelectKeystoneAccountState(
                    onBackClick = {},
                    title = stringRes("Confirm Account to Access"),
                    subtitle =
                        stringRes(
                            "Select the wallet you'd like to connect to proceed. Once connected," +
                                " you’ll be able to wirelessly sign transactions with your hardware wallet."
                        ),
                    items =
                        listOf(
                            ZashiCheckboxListItemState(
                                title = stringRes("title"),
                                subtitle = stringRes("subtitle"),
                                icon = imageRes("1"),
                                isSelected = true,
                                onClick = {}
                            ),
                            ZashiCheckboxListItemState(
                                title = stringRes("title"),
                                subtitle = stringRes("subtitle"),
                                icon = imageRes("2"),
                                isSelected = false,
                                onClick = {}
                            )
                        ),
                    positiveButtonState = ButtonState(stringRes("Unlock")),
                )
        )
    }
