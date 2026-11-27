package co.electriccoin.zcash.ui.screen.connectledger.handshake

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCallout
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceSkeletons
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerWaitingIndicator

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
            LedgerDeviceSkeletons(
                isShimmering = false,
                fadeStart = 0f,
                opaqueAt = SKELETON_OPAQUE_AT,
            ) {
                LedgerWaitingIndicator(
                    title = stringResource(R.string.ledger_handshake_waiting_title),
                    subtitle = stringResource(R.string.ledger_handshake_waiting_subtitle),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = INDICATOR_BOTTOM_OFFSET.dp)
                            .testTag(LedgerHandshakeTag.WAITING_INDICATOR),
                )
            }
        }
    }

    LedgerErrorSheet(state.errorSheet)
}

private const val INDICATOR_BOTTOM_OFFSET = 16

/**
 * Figma's step 4 fade starts at the top of the placeholder list and is opaque from halfway down.
 */
private const val SKELETON_OPAQUE_AT = 0.5f

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
