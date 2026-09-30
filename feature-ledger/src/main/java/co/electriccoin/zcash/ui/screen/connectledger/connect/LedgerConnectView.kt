package co.electriccoin.zcash.ui.screen.connectledger.connect

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCallout
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerNumberedRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

@Composable
fun LedgerConnectView(state: LedgerConnectState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBackClick) },
        step = null,
        title = stringResource(R.string.ledger_flow_title),
        description = stringResource(R.string.ledger_intro_message),
        bottomButton = {
            ZashiButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerConnectTag.CONNECT_BTN),
                text = stringResource(R.string.ledger_intro_cta),
                onClick = state.onContinueClick,
            )
        },
        callout = {
            LedgerCallout(
                title = stringResource(R.string.ledger_intro_callout_title),
                text = stringResource(R.string.ledger_intro_callout_message),
            )
        },
    ) {
        INTRO_STEPS.forEachIndexed { index, (title, message) ->
            if (index != 0) {
                Spacer(16.dp)
            }
            LedgerNumberedRow(
                number = index + 1,
                title = stringResource(title),
                subtitle = stringResource(message),
            )
        }
    }
}

private val INTRO_STEPS =
    listOf(
        R.string.ledger_intro_step1_title to R.string.ledger_intro_step1_message,
        R.string.ledger_intro_step2_title to R.string.ledger_intro_step2_message,
        R.string.ledger_intro_step3_title to R.string.ledger_intro_step3_message,
        R.string.ledger_intro_step4_title to R.string.ledger_intro_step4_message,
    )

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerConnectView(state = LedgerConnectState.preview)
    }
