package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.requestFocusWhenHidden
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The blocking "Too many notes to send at once" sheet of Send, Swap and Pay (Figma 9043:3302).
 *
 * Each emission of [focusRequests] focuses [focusRequester] once the sheet has been hidden, which is how
 * OK lands the user in the cleared amount field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZashiMaxTooManyNotesSheet(
    state: MaxTooManyNotesSheetState?,
    focusRequests: Flow<Unit>,
    focusRequester: FocusRequester,
) {
    val sheetState = rememberInScreenModalBottomSheetState()
    val view = LocalView.current
    LaunchedEffect(focusRequests, focusRequester, sheetState) {
        focusRequests.collect { focusRequester.requestFocusWhenHidden(sheetState, view) }
    }
    ZashiInScreenModalBottomSheet(state = state, sheetState = sheetState) {
        TooManyNotesContent(
            state = it,
            modifier = Modifier.weight(1f, false)
        )
    }
}

@Composable
private fun TooManyNotesContent(
    state: MaxTooManyNotesSheetState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .testTag(ZashiMaxTooManyNotesSheetTags.SHEET),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_error_circle),
            contentDescription = null
        )
        Spacer(12.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.body.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(24.dp)
        ZashiButton(
            state = state.ok,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The too-many-notes sheet with its single "OK" button (Figma 9162:2867). [ok] runs the owner's action, [onBack]
 * (back press, scrim tap or drag) only dismisses the sheet.
 *
 * Use [create] where there is an amount to change: OK dismisses the sheet, clears the amount and focuses the amount
 * field. Use [createCloseOnly] after the transaction was broadcast, when the notes it spends are locked and there is
 * no amount to change: OK only dismisses.
 */
@Immutable
data class MaxTooManyNotesSheetState(
    val title: StringResource,
    val body: StringResource,
    val ok: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        /** [onOk] is the whole OK action (dismiss, clear the amount, focus it); [onDismiss] is back/scrim only. */
        fun create(
            onOk: () -> Unit,
            onDismiss: () -> Unit
        ) = MaxTooManyNotesSheetState(
            title = stringRes(R.string.general_maxTooManyNotesTitle),
            body = stringRes(R.string.general_maxTooManyNotesBody),
            ok =
                ButtonState(
                    text = stringRes(R.string.general_ok),
                    style = ButtonStyle.PRIMARY,
                    onClick = onOk
                ),
            onBack = onDismiss
        )

        /** The sheet of a transaction that was broadcast and refused for being too large: OK only dismisses. */
        fun createCloseOnly(onClose: () -> Unit) = create(onOk = onClose, onDismiss = onClose)
    }
}

object ZashiMaxTooManyNotesSheetTags {
    const val SHEET = "zashi_max_too_many_notes_sheet"
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun ZashiMaxTooManyNotesSheetPreview() =
    ZcashTheme {
        ZashiMaxTooManyNotesSheet(
            state = MaxTooManyNotesSheetState.create(onOk = {}, onDismiss = {}),
            focusRequests = emptyFlow(),
            focusRequester = remember { FocusRequester() }
        )
    }
