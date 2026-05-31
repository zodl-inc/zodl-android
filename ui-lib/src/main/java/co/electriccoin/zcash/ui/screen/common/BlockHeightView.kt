package co.electriccoin.zcash.ui.screen.common

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextField
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldPlaceholder
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
fun BlockHeightView(state: BlockHeightState) {
    BlankBgScaffold(
        topBar = { AppBar(state) },
        bottomBar = {},
        content = { padding ->
            Content(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
            )
        }
    )
}

@Composable
private fun Content(
    state: BlockHeightState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        state.logo?.let { logo ->
            Image(
                modifier = Modifier.height(32.dp),
                painter = painterResource(logo),
                contentDescription = null,
            )
            Spacer(Modifier.height(24.dp))
        }

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
            placeholder = { ZashiTextFieldPlaceholder(state.textFieldHint) },
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

        state.secondaryButton?.let { secondary ->
            ZashiButton(
                secondary,
                modifier = Modifier.fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors()
            )
            Spacer(Modifier.height(12.dp))
        }

        ZashiButton(
            state.primaryButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        state.primaryButtonTestTag?.let { mod.testTag(it) } ?: mod
                    },
        )
    }
}

@Composable
private fun AppBar(state: BlockHeightState) {
    ZashiSmallTopAppBar(
        title = state.title?.let { it.getValue() },
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
        },
        regularActions = {
            state.dialogButton?.let {
                ZashiIconButton(it, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(20.dp))
            }
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                ),
    )
}
