package co.electriccoin.zcash.ui.screen.connectledger.handshake

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCallout
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceSkeletons
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

/**
 * The Figma "Approve on Your Ledger" frame: while the Ledger is asked, the placeholder rows with
 * the waiting indicator over them; after an issue, only the header and the callout, with the
 * issue's sheet on top. The back arrow cancels a running handshake.
 */
@Composable
fun LedgerHandshakeView(state: LedgerHandshakeState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBack) },
        step = 4,
        title = state.title.getValue(),
        description = state.message.getValue(),
        descriptionColor = ZashiColors.Text.textPrimary,
        bottomButton = {
            ZashiButton(
                state = state.primaryButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerHandshakeTag.PRIMARY_BTN),
            )
        },
        callout = {
            LedgerCallout(
                title = stringResource(R.string.ledger_handshake_callout_title),
                text = stringResource(R.string.ledger_handshake_callout_message),
            )
        },
    ) {
        if (state.isConnecting) {
            LedgerDeviceSkeletons(isShimmering = false) {
                WaitingIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = INDICATOR_BOTTOM_OFFSET.dp),
                )
            }
        }
    }

    LedgerErrorSheet(state.errorSheet)
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
private fun ConnectingPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewConnecting)
    }

@PreviewScreens
@Composable
private fun ErrorPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewError)
    }

@PreviewScreens
@Composable
private fun ErrorDismissedPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewErrorDismissed)
    }

@PreviewScreens
@Composable
private fun AlreadyAddedPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewAlreadyAdded)
    }
