package co.electriccoin.zcash.ui.screen.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
fun EstimatedBlockHeightView(state: EstimatedBlockHeightState) {
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
    state: EstimatedBlockHeightState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        state.logo?.let { logo ->
            Image(
                modifier = Modifier.height(32.dp),
                painter = painterResource(logo),
                contentDescription = null,
            )
            VerticalSpacer(24.dp)
        }

        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        VerticalSpacer(8.dp)
        Text(
            text = state.message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary
        )
        VerticalSpacer(56.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.blockHeightText.getValue(),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header2,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        VerticalSpacer(12.dp)
        ZashiButton(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            state = state.copyButton,
            defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors()
        )
        VerticalSpacer(24.dp)
        Spacer(1f)
        ZashiButton(
            state = state.primaryButton,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppBar(
    state: EstimatedBlockHeightState,
    modifier: Modifier = Modifier
) {
    ZashiSmallTopAppBar(
        modifier = modifier,
        title = state.title?.getValue(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
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
