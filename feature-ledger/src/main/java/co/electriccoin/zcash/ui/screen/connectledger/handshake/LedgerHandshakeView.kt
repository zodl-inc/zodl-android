package co.electriccoin.zcash.ui.screen.connectledger.handshake

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCallout
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

@Composable
fun LedgerHandshakeView(state: LedgerHandshakeState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBack) },
        step = 4,
        title = state.title.getValue(),
        description = state.message.getValue(),
        bottomButton = {
            state.primaryButton?.let {
                ZashiButton(
                    state = it,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(LedgerHandshakeTag.PRIMARY_BTN),
                )
            }
        },
    ) {
        LedgerCallout(
            title = stringResource(R.string.ledger_handshake_callout_title),
            text = stringResource(R.string.ledger_handshake_callout_message),
        )
        Spacer(48.dp)
        if (state.isConnecting) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(32.dp)
                        .testTag(LedgerHandshakeTag.SPINNER),
                color = ZashiColors.Text.textPrimary,
                strokeWidth = 3.dp,
            )
        }
        state.inlineIssue?.let {
            LedgerInlineIssue(
                state = it,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    LedgerErrorSheet(state.errorSheet)
}

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
private fun AlreadyAddedPreview() =
    ZcashTheme {
        LedgerHandshakeView(state = LedgerHandshakeState.previewAlreadyAdded)
    }
