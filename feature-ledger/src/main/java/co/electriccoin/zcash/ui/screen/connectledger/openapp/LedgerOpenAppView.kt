package co.electriccoin.zcash.ui.screen.connectledger.openapp

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
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCheckRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerChecklistCard
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

@Composable
fun LedgerOpenAppView(state: LedgerOpenAppState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBackClick) },
        step = 3,
        title = stringResource(R.string.ledger_openApp_title),
        description = stringResource(R.string.ledger_openApp_message),
        bottomButton = {
            ZashiButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerOpenAppTag.CONTINUE_BTN),
                text = stringResource(R.string.ledger_connect_continue),
                onClick = state.onContinueClick,
            )
        },
    ) {
        LedgerChecklistCard {
            LedgerCheckRow(title = stringResource(R.string.ledger_openApp_check1))
            LedgerCheckRow(title = stringResource(R.string.ledger_openApp_check2))
            LedgerCheckRow(title = stringResource(R.string.ledger_openApp_check3))
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerOpenAppView(state = LedgerOpenAppState.preview)
    }
