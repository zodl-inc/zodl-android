package co.electriccoin.zcash.ui.screen.connectledger.handshake

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceSkeletons
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet

/**
 * The Figma "Approve on Device" frames: while the pairing runs, the placeholder rows with the
 * waiting indicator over them and only Cancel below; after an issue, only the header, with Cancel
 * and Retry below and the issue's sheet on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerHandshakeView(state: LedgerHandshakeState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarBackNavigation(state.onBack) },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
            ) {
                Image(
                    modifier = Modifier.height(32.dp),
                    painter = painterResource(R.drawable.ic_ledger_wordmark),
                    contentDescription = null,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.ledger_handshake_title),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ledger_handshake_message),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                )
                if (state.isWaiting) {
                    Spacer(Modifier.height(24.dp))
                    LedgerDeviceSkeletons(isShimmering = false) {
                        WaitingIndicator(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = INDICATOR_BOTTOM_OFFSET.dp),
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.weight(1f))
                ZashiButton(
                    state = state.cancelButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(LedgerHandshakeTag.CANCEL_BTN),
                )
                state.retryButton?.let {
                    Spacer(Modifier.height(12.dp))
                    ZashiButton(
                        state = it,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(LedgerHandshakeTag.RETRY_BTN),
                    )
                }
            }

            LedgerErrorSheet(state.errorSheet)
        }
    }
}

@Composable
private fun WaitingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "LedgerHandshakeSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SPINNER_TURN_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "LedgerHandshakeSpinnerRotation",
    )
    Column(
        modifier = modifier.testTag(LedgerHandshakeTag.WAITING_INDICATOR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            modifier =
                Modifier
                    .size(24.dp)
                    .rotate(rotation),
            painter = painterResource(R.drawable.ic_ledger_loading),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                modifier = Modifier.widthIn(max = WAITING_TEXT_MAX_WIDTH.dp),
                text = stringResource(R.string.ledger_handshake_waiting_title),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier.widthIn(max = WAITING_TEXT_MAX_WIDTH.dp),
                text = stringResource(R.string.ledger_handshake_waiting_subtitle),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val FULL_TURN = 360f

private const val SPINNER_TURN_MILLIS = 1000

private const val WAITING_TEXT_MAX_WIDTH = 280

private const val INDICATOR_BOTTOM_OFFSET = 16

@PreviewScreens
@Composable
private fun WaitingPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.preview)
    }

@PreviewScreens
@Composable
private fun ErrorPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewError)
    }

@PreviewScreens
@Composable
private fun ErrorSheetPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewErrorSheet)
    }
