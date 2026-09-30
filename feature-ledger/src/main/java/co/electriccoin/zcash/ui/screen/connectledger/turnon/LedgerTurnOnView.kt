package co.electriccoin.zcash.ui.screen.connectledger.turnon

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCallout
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerChecklistCard
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerChecklistItem
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

@Composable
fun LedgerTurnOnView(state: LedgerTurnOnState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBackClick) },
        step = 1,
        title = stringResource(R.string.ledger_connect_turnOn_title),
        description = stringResource(R.string.ledger_connect_turnOn_message),
        bottomButton = {
            ZashiButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerTurnOnTag.CONTINUE_BTN),
                text = stringResource(R.string.ledger_connect_continue),
                onClick = state.onContinueClick,
            )
        },
        callout = {
            LedgerCallout(
                title = stringResource(R.string.ledger_connect_turnOn_callout_title),
                text = stringResource(R.string.ledger_connect_turnOn_callout_message),
            )
        },
    ) {
        LedgerChecklistCard {
            LedgerChecklistItem(number = 1, text = stringResource(R.string.ledger_connect_turnOn_check1))
            LedgerChecklistItem(number = 2, text = stringResource(R.string.ledger_connect_turnOn_check2))
            LedgerChecklistItem(number = 3, text = stringResource(R.string.ledger_connect_turnOn_check3))
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerTurnOnView(state = LedgerTurnOnState.preview)
    }
