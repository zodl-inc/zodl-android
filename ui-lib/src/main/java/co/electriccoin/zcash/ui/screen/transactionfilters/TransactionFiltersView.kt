package co.electriccoin.zcash.ui.screen.transactionfilters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiChipButton
import co.electriccoin.zcash.ui.design.component.ZashiChipButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.transactionfilters.fixture.TransactionFiltersStateFixture

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TransactionFiltersView(
    state: TransactionFiltersState?,
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
    state: TransactionFiltersState?,
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
            text = stringResource(R.string.filter_title),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )

        Spacer(Modifier.height(24.dp))

        if (state == null) {
            CircularScreenProgressIndicator()
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.filters.forEach { filter ->
                    ZashiChipButton(
                        state =
                            ChipButtonState(
                                endIcon = if (filter.isSelected) R.drawable.ic_close_small else null,
                                onClick = filter.onClick,
                                text = filter.text,
                            ),
                        shape = CircleShape,
                        border =
                            BorderStroke(1.dp, ZashiColors.Btns.Secondary.btnSecondaryBorder)
                                .takeIf { filter.isSelected },
                        color =
                            if (filter.isSelected) {
                                ZashiColors.Btns.Secondary.btnSecondaryBg
                            } else {
                                ZashiChipButtonDefaults.color
                            },
                        contentPadding =
                            if (filter.isSelected) {
                                PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                            } else {
                                PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            },
                        endIconSpacer = 10.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZashiButton(
                    state = state.secondaryButton,
                    modifier = Modifier.weight(1f),
                    defaultPrimaryColors =
                        ZashiButtonDefaults.secondaryColors(
                            borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                        )
                )

                ZashiButton(
                    state = state.primaryButton,
                    modifier = Modifier.weight(1f),
                    defaultPrimaryColors = ZashiButtonDefaults.primaryColors()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        TransactionFiltersView(
            state = TransactionFiltersStateFixture.new(),
            sheetState =
                rememberModalBottomSheetState(
                    skipHiddenState = true,
                    skipPartiallyExpanded = true,
                    initialValue = SheetValue.Expanded,
                    confirmValueChange = { true }
                )
        )
    }
