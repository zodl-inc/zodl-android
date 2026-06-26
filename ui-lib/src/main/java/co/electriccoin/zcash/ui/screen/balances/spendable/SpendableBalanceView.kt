package co.electriccoin.zcash.ui.screen.balances.spendable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.orHiddenString
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendableBalanceView(
    state: SpendableBalanceState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        content = { state, contentPadding ->
            BottomSheetContent(state, contentPadding, modifier = Modifier.weight(1f, false))
        },
    )
}

@Composable
fun BottomSheetContent(
    state: SpendableBalanceState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                )
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(12.dp)
        Text(
            text = state.message.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd
        )
        Spacer(32.dp)
        state.rows.forEachIndexed { index, state ->
            if (index != 0) {
                Spacer(12.dp)
            }
            BalanceActionRow(state)
        }
        state.shieldButton?.let {
            Spacer(32.dp)
            BalanceShieldButton(it)
        }
        Spacer(32.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.positive
        )
    }
}

@Composable
private fun BalanceActionRow(state: SpendableBalanceRowState) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.title.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(1f)
        when (state.icon) {
            is ImageResource.ByDrawable -> {
                Image(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(state.icon.resource),
                    contentDescription = null
                )
            }

            ImageResource.Loading -> {
                LottieProgress(modifier = Modifier.size(20.dp))
            }

            is ImageResource.DisplayString -> {
                // do nothing
            }
        }
        Spacer(8.dp)
        SelectionContainer {
            Text(
                text =
                    state.value orHiddenString
                        stringRes(co.electriccoin.zcash.ui.design.R.string.general_hideBalancesMost),
                color =
                    if (state.icon is ImageResource.Loading) {
                        ZashiColors.Text.textTertiary
                    } else {
                        ZashiColors.Text.textPrimary
                    },
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BalanceShieldButton(state: SpendableBalanceShieldButtonState) {
    ZashiCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 12.dp
            ),
        borderColor = ZashiColors.Surfaces.strokeSecondary
    ) {
        Row {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZashiAutoSizeText(
                        text = stringResource(R.string.smartBanner_help_shield_transparent),
                        color = ZashiColors.Text.textPrimary,
                        style = ZashiTypography.textMd,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(4.dp)
                    Image(
                        painter = painterResource(R.drawable.ic_transparent_small),
                        contentDescription = null
                    )
                }
                Spacer(4.dp)
                ZashiAutoSizeText(
                    text =
                        stringRes(state.amount)
                            orHiddenString
                            stringRes(co.electriccoin.zcash.ui.design.R.string.general_hideBalancesMost),
                    color = ZashiColors.Text.textPrimary,
                    style = ZashiTypography.textXl,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Spacer(8.dp)
            ZashiButton(
                state =
                    ButtonState(
                        text = stringRes(R.string.smartBanner_content_shield_button),
                        onClick = state.onShieldClick,
                        hapticFeedbackType = HapticFeedbackType.Confirm
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SpendableBalanceView(
            state =
                SpendableBalanceState(
                    title = stringRes("Title"),
                    message = stringRes("Subtitle"),
                    positive =
                        ButtonState(
                            text = stringRes("Positive")
                        ),
                    onBack = {},
                    rows =
                        listOf(
                            SpendableBalanceRowState(
                                title = stringRes("Row"),
                                icon = loadingImageRes(),
                                value = stringRes("Value")
                            ),
                            SpendableBalanceRowState(
                                title = stringRes("Row"),
                                icon = imageRes(R.drawable.ic_balance_shield),
                                value = stringRes("Value")
                            )
                        ),
                    shieldButton =
                        SpendableBalanceShieldButtonState(
                            amount = Zatoshi(10000),
                            onShieldClick = {}
                        )
                )
        )
    }
