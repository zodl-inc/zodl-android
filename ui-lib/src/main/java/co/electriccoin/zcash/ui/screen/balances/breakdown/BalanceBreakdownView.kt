package co.electriccoin.zcash.ui.screen.balances.breakdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.StyledBalance
import co.electriccoin.zcash.ui.design.component.StyledBalanceDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orHiddenString
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceBreakdownView(
    state: BalanceBreakdownState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        containerColor = ZashiColors.Surfaces.bgSecondary,
        content = { state, contentPadding ->
            BottomSheetContent(state, contentPadding, modifier = Modifier.weight(1f, false))
        },
    )
}

@Composable
private fun BottomSheetContent(
    state: BalanceBreakdownState,
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
        )
        Spacer(4.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.subtitle.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(24.dp)
        BalanceCard(state.total, modifier = Modifier.fillMaxWidth())
        Spacer(8.dp)
        state.pools.chunked(2).forEachIndexed { index, row ->
            if (index != 0) {
                Spacer(8.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { pool ->
                    BalanceCard(pool, modifier = Modifier.weight(1f))
                }
                // Keep single-item rows left-aligned with a matching empty cell.
                if (row.size == 1) {
                    Spacer(1f)
                }
            }
        }
        Spacer(32.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.positive
        )
    }
}

@Composable
private fun BalanceCard(
    state: BalanceBreakdownItemState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .background(
                    color = ZashiColors.Surfaces.bgPrimary,
                    shape = RoundedCornerShape(16.dp)
                ).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = state.title.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
        )
        Spacer(4.dp)
        StyledBalance(
            balance = state.amount,
            isHideBalances = LocalBalancesAvailable.current.not(),
            showDust = true,
            textColor = ZashiColors.Text.textPrimary,
            textStyle =
                StyledBalanceDefaults.textStyles(
                    mostSignificantPart = ZashiTypography.textMd.copy(fontWeight = FontWeight.SemiBold),
                    leastSignificantPart = ZashiTypography.textXs.copy(fontWeight = FontWeight.SemiBold),
                )
        )
        state.fiat?.let { fiat ->
            Spacer(2.dp)
            Text(
                text =
                    fiat orHiddenString
                        stringRes(co.electriccoin.zcash.ui.design.R.string.general_hideBalancesMost),
                color = ZashiColors.Text.textQuaternary,
                style = ZashiTypography.textXs,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BalanceBreakdownView(
            state =
                BalanceBreakdownState(
                    title = stringRes("Total Balance Across Pools"),
                    subtitle = stringRes("Your ZEC balance is now broken down by Zcash pool."),
                    total =
                        BalanceBreakdownItemState(
                            title = stringRes("Total Balance"),
                            amount = Zatoshi(1012300000),
                            fiat = stringRes("$4,906.32")
                        ),
                    pools =
                        listOf(
                            BalanceBreakdownItemState(stringRes("Orchard"), Zatoshi(310000000), stringRes("$1,502.48")),
                            BalanceBreakdownItemState(stringRes("Sapling"), Zatoshi(281300000), stringRes("$1,363.38")),
                            BalanceBreakdownItemState(
                                stringRes("Transparent"),
                                Zatoshi(14200000),
                                stringRes("$68.82")
                            ),
                            BalanceBreakdownItemState(
                                stringRes("Ironwood"),
                                Zatoshi(406800000),
                                stringRes("$1,971.64")
                            ),
                        ),
                    positive =
                        co.electriccoin.zcash.ui.design.component
                            .ButtonState(stringRes("Got it")),
                    onBack = {},
                )
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun PreviewNoFiat() =
    ZcashTheme {
        BalanceBreakdownView(
            state =
                BalanceBreakdownState(
                    title = stringRes("Total Balance Across Pools"),
                    subtitle = stringRes("Your ZEC balance is now broken down by Zcash pool."),
                    total =
                        BalanceBreakdownItemState(
                            title = stringRes("Total Balance"),
                            amount = Zatoshi(1012300000),
                            fiat = null
                        ),
                    pools =
                        listOf(
                            BalanceBreakdownItemState(stringRes("Orchard"), Zatoshi(310000000), null),
                            BalanceBreakdownItemState(stringRes("Sapling"), Zatoshi(281300000), null),
                            BalanceBreakdownItemState(stringRes("Transparent"), Zatoshi(14200000), null),
                            BalanceBreakdownItemState(stringRes("Ironwood"), Zatoshi(406800000), null),
                        ),
                    positive =
                        co.electriccoin.zcash.ui.design.component
                            .ButtonState(stringRes("Got it")),
                    onBack = {},
                )
        )
    }
