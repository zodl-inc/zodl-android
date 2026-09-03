package co.electriccoin.zcash.ui.screen.scan.thirdparty

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.component.zashiVerticalGradient
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
fun ThirdPartyScanView(state: ThirdPartyScanState) {
    val hazeState = rememberZashiFrostState()
    val gradientStartColor = ZashiColors.Utility.WarningYellow.utilityOrange100
    val gradientEndColor = ZashiColors.Surfaces.bgPrimary
    GradientBgScaffold(
        startColor = gradientStartColor,
        endColor = gradientEndColor,
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState, frostColor = Color.Transparent),
                title = stringResource(R.string.deeplinkWarning_screenTitle),
                colors = ZcashTheme.colors.topAppBarColors.copyColors(containerColor = Color.Transparent),
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
                    .background(
                        zashiVerticalGradient(
                            startColor = gradientStartColor,
                            endColor = gradientEndColor
                        )
                    )
        ) {
            Content(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(paddingValues)
            )
        }
    }
}

@Composable
private fun Content(
    state: ThirdPartyScanState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(1f)
        Spacer(14.dp)
        Image(
            painter = painterResource(id = R.drawable.img_third_party_scan),
            contentDescription = null,
        )
        Spacer(24.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = stringResource(R.string.deeplinkWarning_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(12.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = stringResource(R.string.deeplinkWarning_desc),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
        )
        Spacer(1f)
        Spacer(14.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = state.onScanClick,
            text = stringResource(R.string.deeplinkWarning_cta)
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        ThirdPartyScanView(
            ThirdPartyScanState(
                onScanClick = {},
                onBack = {}
            )
        )
    }
