@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.restore.height

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextField
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldPlaceholder
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun RestoreHeightView(state: RestoreHeightState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            AppBar(
                state = state,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState)
            )
        },
        bottomBar = {},
        content = { padding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zashiFrostSource(hazeState)
            ) {
                Content(
                    state = state,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .scaffoldPadding(padding)
                )
            }
        }
    )
}

@Composable
private fun Content(
    state: RestoreHeightState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = state.textFieldTitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Inputs.Default.label,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        ZashiNumberTextField(
            state = state.blockHeight,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                ZashiTextFieldPlaceholder(
                    state.textFieldHint
                )
            },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.textFieldNote.getValue(),
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))

        ZashiButton(
            state.estimate,
            modifier = Modifier.fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.secondaryColors()
        )

        Spacer(Modifier.height(12.dp))

        ZashiButton(
            state.restore,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(RestoreHeightTags.RESTORE_BTN),
        )
    }
}

@Composable
private fun AppBar(
    state: RestoreHeightState,
    modifier: Modifier = Modifier
) {
    ZashiSmallTopAppBar(
        modifier = modifier,
        title = state.title.getValue(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        regularActions = {
            ZashiIconButton(state.dialogButton, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(20.dp))
        },
        colors =
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            ),
    )
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        RestoreHeightView(
            state =
                RestoreHeightState(
                    title = stringRes("Restore"),
                    subtitle = stringRes("Wallet Birthday Height"),
                    message = stringRes("Entering your Wallet Birthday Height helps speed up the restore process."),
                    textFieldTitle = stringRes("Block Height"),
                    textFieldHint = stringRes("Enter number"),
                    textFieldNote =
                        stringRes(
                            "Wallet Birthday Height is the point in time when your wallet " +
                                "was created."
                        ),
                    onBack = {},
                    dialogButton = IconButtonState(R.drawable.ic_help) {},
                    blockHeight = NumberTextFieldState {},
                    estimate = ButtonState(stringRes("Estimate")) {},
                    restore = ButtonState(stringRes("Restore")) {}
                )
        )
    }
