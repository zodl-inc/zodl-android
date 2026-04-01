package co.electriccoin.zcash.ui.screen.deletewallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.CheckboxTextStyles
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetZashiView(state: ResetZashiState) {
    Scaffold(
        topBar = {
            ResetZashiTopAppBar(
                onBack = state.onBack,
            )
        },
    ) { paddingValues ->
        ResetZashiContent(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
                    .verticalScroll(rememberScrollState())
        )
    }
    ZashiConfirmationBottomSheet(state = state.confirmationDialog)
}

@Composable
private fun ResetZashiTopAppBar(onBack: () -> Unit) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.delete_wallet_title),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = onBack
            )
        }
    )
}

@Composable
private fun ResetZashiContent(
    state: ResetZashiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.delete_wallet_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(8.dp)

        Text(
            text = stringResource(R.string.delete_wallet_text_1),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.delete_wallet_text_2),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.delete_wallet_text_3),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(24.dp)
        Spacer(1f)
        CheckboxCard(state.checkboxState)
        Spacer(28.dp)
        ZashiButton(
            state = state.buttonState,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CheckboxCard(checkboxState: CheckboxState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Utility.WarningYellow.utilityOrange50,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl)
    ) {
        ZashiCheckbox(
            contentPadding = PaddingValues(20.dp),
            state = checkboxState,
            textSpacing = 8.dp,
            textStyles =
                CheckboxTextStyles(
                    title =
                        ZashiTypography.textSm.copy(
                            fontWeight = FontWeight.Medium,
                            color = ZashiColors.Text.textTertiary
                        ),
                    subtitle =
                        ZashiTypography.textXs.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ZashiColors.Utility.WarningYellow.utilityOrange700
                        ),
                )
        )
    }
}

@PreviewScreens
@Composable
private fun ResetZashiViewPreview() =
    ZcashTheme {
        ResetZashiView(
            state =
                ResetZashiState(
                    onBack = {},
                    checkboxState =
                        CheckboxState(
                            title = stringRes(R.string.delete_wallet_checkbox_title),
                            subtitle = stringRes("This data cannot be recovered during Restore."),
                            isChecked = true,
                            onClick = {}
                        ),
                    buttonState =
                        ButtonState(
                            text = stringRes("Confirm"),
                            onClick = {}
                        ),
                    confirmationDialog = null,
                )
        )
    }
