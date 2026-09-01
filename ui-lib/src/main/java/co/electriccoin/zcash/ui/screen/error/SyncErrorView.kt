package co.electriccoin.zcash.ui.screen.error

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
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
        dragHandle = null,
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
    val hazeState = rememberZashiFrostState()
    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .zashiFrostSource(hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = headerHeight,
                        bottom = contentPadding.calculateBottomPadding()
                    ),
        ) {
            Text(
                text = stringResource(R.string.sync_error_message),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm
            )
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

        ZashiFrostedSheetHeader(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            onHeightChanged = { headerHeight = it },
            title = {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
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
                }
            }
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
