package co.electriccoin.zcash.ui.screen.error

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCardButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiSimpleListItem
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncErrorView(
    state: SyncErrorState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        content = { state, contentPadding ->
            SyncErrorContent(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f, false)
            )
        },
    )
}

@Composable
fun SyncErrorContent(
    state: SyncErrorState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                ),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_swap_quote_error),
            contentDescription = null,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(co.electriccoin.zcash.ui.design.R.string.coinVote_error_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(12.dp)
        Text(
            text = state.diagnostics?.explanation?.getValue() ?: stringResource(R.string.sync_error_message),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm
        )
        state.diagnostics?.facts?.forEach { fact ->
            Spacer(12.dp)
            ZashiSimpleListItem(
                modifier = Modifier.fillMaxWidth(),
                state = fact
            )
        }
        Spacer(24.dp)
        ZashiCardButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.tryAgain
        )
        Spacer(8.dp)
        ZashiCardButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.switchServer
        )
        state.disableTor?.let { disableTorButton ->
            Spacer(8.dp)
            ZashiCardButton(
                modifier = Modifier.fillMaxWidth(),
                state = disableTorButton
            )
        }
        Spacer(28.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.support
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SyncErrorView(
            state =
                SyncErrorState(
                    tryAgain =
                        ButtonState(
                            text = stringRes("Try again"),
                            icon = R.drawable.ic_sync_error_try_again,
                            onClick = {},
                            trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                        ),
                    switchServer =
                        ButtonState(
                            text = stringRes("Switch server"),
                            icon = R.drawable.ic_sync_error_switch_server,
                            trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                            onClick = {}
                        ),
                    disableTor =
                        ButtonState(
                            text = stringRes("Disable Tor protection"),
                            icon = R.drawable.ic_sync_error_disable_tor,
                            trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                            onClick = {}
                        ),
                    support =
                        ButtonState(
                            text = stringRes("Contact Support"),
                            onClick = {}
                        ),
                    onBack = {}
                )
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun IncompatibleServerPreview() =
    ZcashTheme {
        SyncErrorView(
            state =
                SyncErrorState(
                    tryAgain =
                        ButtonState(
                            text = stringRes("Try again"),
                            icon = R.drawable.ic_sync_error_try_again,
                            onClick = {},
                            trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                        ),
                    switchServer =
                        ButtonState(
                            text = stringRes("Switch server"),
                            icon = R.drawable.ic_sync_error_switch_server,
                            trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right,
                            onClick = {}
                        ),
                    disableTor = null,
                    support =
                        ButtonState(
                            text = stringRes("Contact Support"),
                            onClick = {}
                        ),
                    onBack = {},
                    diagnostics =
                        SyncErrorDiagnosticsState(
                            explanation =
                                stringRes(
                                    "ZODL and this server are following different versions of the Zcash " +
                                        "consensus rules, so syncing can't continue. One of them is on an older " +
                                        "release — updating ZODL or switching to a different server will get you " +
                                        "syncing again."
                                ),
                            facts =
                                listOf(
                                    SimpleListItemState(stringRes("Server"), stringRes("zec.rocks:443")),
                                    SimpleListItemState(stringRes("Expected branch ID"), stringRes("0x5437f330")),
                                    SimpleListItemState(stringRes("Server branch ID"), stringRes("0x37a5165b")),
                                    SimpleListItemState(
                                        stringRes("Error type"),
                                        stringRes("MismatchedConsensusBranch")
                                    )
                                )
                        )
                )
        )
    }
