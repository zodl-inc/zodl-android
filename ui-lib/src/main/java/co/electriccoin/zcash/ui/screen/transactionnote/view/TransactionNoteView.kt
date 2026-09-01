package co.electriccoin.zcash.ui.screen.transactionnote.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.rememberModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.transactionnote.model.TransactionNoteState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TransactionNoteView(
    state: TransactionNoteState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    onSheetOpen: (FocusRequester) -> Unit = { }
) {
    val onSheetOpen by rememberUpdatedState(onSheetOpen)
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        dragHandle = null,
        content = { state, contentPadding ->
            val focusRequester = remember { FocusRequester() }
            BottomSheetContent(
                state = state,
                contentPadding = contentPadding,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f, false)
            )
            LaunchedEffect(sheetState.currentValue) {
                if (sheetState.currentValue == SheetValue.Expanded) {
                    onSheetOpen(focusRequester)
                }
            }
        },
    )
}

@Composable
private fun BottomSheetContent(
    state: TransactionNoteState,
    contentPadding: PaddingValues,
    focusRequester: FocusRequester,
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
                        top = headerHeight,
                        bottom = contentPadding.calculateBottomPadding()
                    )
        ) {
            ZashiTextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .focusRequester(focusRequester),
                state = state.note,
                minLines = 4,
                placeholder = {
                    Text(
                        text = "Write an optional note to describe this transaction...",
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Inputs.Default.text
                    )
                }
            )

            Spacer(Modifier.height(6.dp))
            Text(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = state.noteCharacters.getValue(),
                style = ZashiTypography.textSm,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.negative?.let {
                    ZashiButton(
                        state = it,
                        modifier = Modifier.weight(1f),
                        defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
                    )
                }

                state.secondaryButton?.let {
                    ZashiButton(
                        state = it,
                        modifier = Modifier.weight(1f),
                        defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors()
                    )
                }

                state.primaryButton?.let {
                    ZashiButton(
                        state = it,
                        modifier = Modifier.weight(1f),
                        defaultPrimaryColors = ZashiButtonDefaults.primaryColors()
                    )
                }
            }
        }

        ZashiFrostedSheetHeader(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            onHeightChanged = { headerHeight = it },
            title = {
                Text(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                    text = state.title.getValue(),
                    style = ZashiTypography.textXl,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        TransactionNoteView(
            state =
                TransactionNoteState(
                    onBack = {},
                    title = stringRes("Title"),
                    note = TextFieldState(stringRes("")) {},
                    noteCharacters = stringRes("x/y characters").withStyle(),
                    primaryButton = null,
                    secondaryButton = null,
                    negative = ButtonState(stringRes("Delete note")),
                ),
            sheetState =
                rememberModalBottomSheetState(
                    skipHiddenState = true,
                    skipPartiallyExpanded = true,
                    initialValue = SheetValue.Expanded,
                    confirmValueChange = { true }
                )
        )
    }
