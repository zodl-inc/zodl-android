package co.electriccoin.zcash.ui.screen.advancedsettings.debug.orchardbalance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
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
fun DebugOrchardBalanceView(state: DebugOrchardBalanceState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                title = "Set Mock Orchard Balance",
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(paddingValues)
            ) {
                Text(
                    text = "Current mock balance: ${state.currentBalance.getValue()}",
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(16.dp)
                Text(
                    "ZEC amount:",
                    color = ZashiColors.Text.textTertiary,
                    style = ZashiTypography.textXs,
                )
                Spacer(4.dp)
                ZashiTextField(
                    state = state.zecInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "123.23",
                            style = ZashiTypography.textMd,
                            color = ZashiColors.Inputs.Default.text
                        )
                    },
                    singleLine = true,
                )
                Spacer(16.dp)
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.setBalance,
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun DebugOrchardBalancePreview() =
    ZcashTheme {
        DebugOrchardBalanceView(
            state =
                DebugOrchardBalanceState(
                    currentBalance = stringRes("10 ZEC"),
                    zecInput =
                        co.electriccoin.zcash.ui.design.component.TextFieldState(
                            value = stringRes("123.23"),
                            onValueChange = {}
                        ),
                    setBalance =
                        co.electriccoin.zcash.ui.design.component.ButtonState(
                            text = stringRes("Set Balance"),
                            onClick = {}
                        ),
                    onBack = {},
                )
        )
    }
