package co.electriccoin.zcash.ui.screen.toomanynotes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.usecase.NavigateToTooManyNotesUseCase
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.MaxTooManyNotesSheetState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.common.ZodlInfoBottomSheetView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

/**
 * The too-many-notes sheet as a destination, for flows with no form of their own to host it, such as the swap and pay
 * quote. It answers the caller that opened it through [NavigateToTooManyNotesUseCase]: OK dismisses it and tells the
 * caller to clear its amount and focus the field; back, scrim and drag only dismiss it.
 */
@Serializable
data class TooManyNotesArgs(
    val requestId: String = UUID.randomUUID().toString()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooManyNotesScreen(args: TooManyNotesArgs) {
    val vm = koinViewModel<TooManyNotesVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    ZodlInfoBottomSheetView(
        state = state,
        primaryButton = state?.ok,
    ) { innerState ->
        Image(
            painter = painterResource(R.drawable.ic_error_circle),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(12.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = innerState.title.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = innerState.body.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
    }
}

class TooManyNotesVM(
    private val args: TooManyNotesArgs,
    private val navigateToTooManyNotes: NavigateToTooManyNotesUseCase
) : ViewModel() {
    val state: StateFlow<MaxTooManyNotesSheetState?> =
        MutableStateFlow<MaxTooManyNotesSheetState?>(
            MaxTooManyNotesSheetState.create(
                onOk = { navigateToTooManyNotes.onOk(args) },
                onDismiss = { navigateToTooManyNotes.onDismissed(args) }
            )
        ).asStateFlow()

    override fun onCleared() {
        navigateToTooManyNotes.onAbandoned(args)
        super.onCleared()
    }
}
