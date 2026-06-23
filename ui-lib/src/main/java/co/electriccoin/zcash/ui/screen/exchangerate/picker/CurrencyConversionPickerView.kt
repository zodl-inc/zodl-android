package co.electriccoin.zcash.ui.screen.exchangerate.picker

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConversionPickerView(state: CurrencyConversionPickerState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        dragHandle = null,
        content = { innerState, _ ->
            BlankBgScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(innerState, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                }
            ) { padding ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scaffoldScrollPadding(
                                paddingValues = padding,
                                top = padding.calculateTopPadding(),
                                bottom = 0.dp,
                                start = 0.dp,
                                end = 0.dp,
                            ),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                ) {
                    itemsIndexed(
                        items = innerState.items,
                        key = { _, item -> item.key },
                        contentType = { _, item -> item.contentType }
                    ) { index, item ->
                        CurrencyItem(item)
                        if (index != innerState.items.lastIndex) {
                            ZashiHorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CurrencyItem(item: CurrencyConversionPickerItemState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = item.onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.code.getValue(),
            style = ZashiTypography.textMd,
            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (item.isSelected) ZashiColors.Text.textPrimary else ZashiColors.Dropdowns.Parts.liTextPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = item.name.getValue(),
            style = ZashiTypography.textMd,
            fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (item.isSelected) ZashiColors.Text.textPrimary else ZashiColors.Text.textTertiary
        )
        Image(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right),
            contentDescription = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    innerState: CurrencyConversionPickerState,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    ZashiSmallTopAppBar(
        title = stringRes(co.electriccoin.zcash.ui.R.string.currencyConversion_selectCurrencyTitle).getValue(),
        navigationAction = {
            ZashiTopAppBarCloseNavigation(
                onBack = innerState.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                ),
        windowInsets = windowInsets
    )
}

@PreviewScreens
@Composable
private fun CurrencyConversionPickerPreview() =
    ZcashTheme {
        CurrencyConversionPickerView(state = CurrencyConversionPickerState.preview)
    }
