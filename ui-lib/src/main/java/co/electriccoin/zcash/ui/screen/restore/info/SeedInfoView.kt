package co.electriccoin.zcash.ui.screen.restore.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SeedInfoView(
    state: SeedInfoState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        dragHandle = null,
        content = { state, contentPadding ->
            Content(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f, false)
            )
        },
    )
}

@Composable
private fun Content(
    state: SeedInfoState,
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
                    )
        ) {
            Info(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZashiColors.Text.textPrimary)) {
                            append(stringResource(id = R.string.restore_dialog_message_1_bold_part))
                        }
                        append(" ")
                        append(stringResource(R.string.restore_dialog_message_1))
                    }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Info(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZashiColors.Text.textPrimary)) {
                            append(stringResource(id = R.string.restore_dialog_message_2_bold_part))
                        }
                        append(" ")
                        append(stringResource(R.string.restore_dialog_message_2))
                    }
            )

            Spacer(modifier = Modifier.height(32.dp))

            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(co.electriccoin.zcash.ui.design.R.string.restoreInfo_gotIt),
                onClick = state.onBack
            )
        }

        ZashiFrostedSheetHeader(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            onHeightChanged = { headerHeight = it },
            title = {
                Text(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    text = stringResource(R.string.restoreWallet_help_title),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        )
    }
}

@Composable
private fun Info(text: AnnotatedString) {
    Row {
        Image(
            painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Normal,
            color = ZashiColors.Text.textTertiary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SeedInfoView(
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    skipHiddenState = true,
                    initialValue = SheetValue.Expanded,
                ),
            state = SeedInfoState { },
        )
    }
