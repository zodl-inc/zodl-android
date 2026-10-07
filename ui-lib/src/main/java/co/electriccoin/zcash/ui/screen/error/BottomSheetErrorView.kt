package co.electriccoin.zcash.ui.screen.error

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetErrorView(
    state: ErrorState?,
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
    state: ErrorState,
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
        Image(
            painter = painterResource(R.drawable.ic_error_warning),
            contentDescription = null
        )
        Spacer(12.dp)
        Text(
            text = state.title.getValue(),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(12.dp)
        Text(
            text = state.message.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textMd
        )
        Spacer(36.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.negative,
            defaultPrimaryColors = ZashiButtonDefaults.secondaryColors()
        )
        Spacer(8.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.positive
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BottomSheetErrorView(
            state =
                ErrorState(
                    title = stringRes("Error"),
                    message = stringRes("Something went wrong"),
                    positive =
                        ButtonState(
                            text = stringRes("Positive")
                        ),
                    negative =
                        ButtonState(
                            text = stringRes("Negative")
                        ),
                    onBack = {}
                )
        )
    }
