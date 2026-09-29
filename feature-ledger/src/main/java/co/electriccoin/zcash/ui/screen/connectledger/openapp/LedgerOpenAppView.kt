package co.electriccoin.zcash.ui.screen.connectledger.openapp

import androidx.compose.foundation.layout.fillMaxWidth
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
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerCheckRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerChecklistCard
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerStepLayout

@Composable
fun LedgerOpenAppView(state: LedgerOpenAppState) {
    LedgerStepLayout(
        navigationAction = { ZashiTopAppBarBackNavigation(state.onBack) },
        step = 3,
        title = stringResource(R.string.ledger_openApp_title),
        description = stringResource(R.string.ledger_openApp_message),
        bottomButton = {
            ZashiButton(
                state = state.primaryButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LedgerOpenAppTag.CONTINUE_BTN),
            )
        },
    ) {
        LedgerChecklistCard {
            LedgerCheckRow(title = stringResource(R.string.ledger_openApp_check1))
            LedgerCheckRow(title = stringResource(R.string.ledger_openApp_check2))
            LedgerCheckRow(title = stringResource(R.string.ledger_openApp_check3))
        }
        state.inlineIssue?.let {
            Spacer(48.dp)
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
private fun Preview() =
    ZcashTheme {
        LedgerOpenAppView(state = LedgerOpenAppState.preview)
    }

@PreviewScreens
@Composable
private fun OpeningPreview() =
    ZcashTheme {
        LedgerOpenAppView(state = LedgerOpenAppState.previewOpening)
    }

@PreviewScreens
@Composable
private fun DeclinedPreview() =
    ZcashTheme {
        LedgerOpenAppView(state = LedgerOpenAppState.previewDeclined)
    }
