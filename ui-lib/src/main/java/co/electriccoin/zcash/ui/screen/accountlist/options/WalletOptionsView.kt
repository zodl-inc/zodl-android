package co.electriccoin.zcash.ui.screen.accountlist.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.listitem.ZodlSheetListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.withStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletOptionsView(state: WalletOptionsState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { innerState, contentPadding ->
            Content(
                state = innerState,
                modifier =
                    Modifier
                        .weight(1f, false)
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                        .padding(bottom = contentPadding.calculateBottomPadding())
            )
        },
    )
}

@Composable
private fun Content(
    state: WalletOptionsState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.options.forEach { option ->
                ZodlSheetListItem(
                    modifier = Modifier.testTag(option.testTag),
                    icon = option.icon,
                    title = option.title,
                    subtitle = option.subtitle.withStyle(),
                    onClick = option.onClick,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        ZashiButton(
            state = state.dismissButton,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            defaultSecondaryColors =
                ZashiButtonDefaults.secondaryColors(
                    borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                )
        )
    }
}

@PreviewScreens
@Composable
private fun WalletOptionsPreview() =
    ZcashTheme {
        WalletOptionsView(WalletOptionsState.preview)
    }

@PreviewScreens
@Composable
private fun WalletOptionsLedgerPreview() =
    ZcashTheme {
        WalletOptionsView(WalletOptionsState.previewLedger)
    }
