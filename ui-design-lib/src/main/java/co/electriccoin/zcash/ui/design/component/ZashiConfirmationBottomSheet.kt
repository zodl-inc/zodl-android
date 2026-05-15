package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZashiConfirmationBottomSheet(state: ZashiConfirmationState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        shape =
            if (state?.style == ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING) {
                RoundedCornerShape(34.dp)
            } else {
                ZashiModalBottomSheetDefaults.SheetShape
            },
        containerColor =
            if (state?.style == ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING) {
                ZashiColors.Surfaces.bgSecondary
            } else {
                ZashiModalBottomSheetDefaults.ContainerColor
            }
    ) { innerState, contentPadding ->
        ConfirmationContent(
            modifier = Modifier.weight(1f, false),
            state = innerState,
            contentPadding = contentPadding
        )
    }
}

data class ZashiConfirmationState(
    val icon: Int,
    val title: StringResource,
    val message: StringResource,
    val primaryAction: ButtonState,
    val secondaryAction: ButtonState? = null,
    override val onBack: () -> Unit,
    val style: ZashiConfirmationStyle = ZashiConfirmationStyle.DEFAULT,
) : ModalBottomSheetState {
    companion object
}

enum class ZashiConfirmationStyle {
    DEFAULT,
    UNVERIFIED_POLL_WARNING,
}

@Composable
private fun ConfirmationContent(
    state: ZashiConfirmationState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val isUnverifiedPollWarning = state.style == ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING
    val actions =
        if (isUnverifiedPollWarning) {
            listOfNotNull(state.secondaryAction, state.primaryAction)
        } else {
            listOfNotNull(state.primaryAction, state.secondaryAction)
        }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConfirmationIcon(state)
        Spacer(12.dp)
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.textXl,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(if (isUnverifiedPollWarning) 4.dp else 12.dp)
        Text(
            text = state.message.getValue(),
            style = if (isUnverifiedPollWarning) ZashiTypography.textSm else ZashiTypography.textMd,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(32.dp)
        actions.forEachIndexed { index, action ->
            ConfirmationButton(
                state = action,
                isUnverifiedPollWarning = isUnverifiedPollWarning
            )
            if (index != actions.lastIndex) {
                Spacer(if (isUnverifiedPollWarning) 12.dp else 8.dp)
            }
        }
    }
}

@Composable
private fun ConfirmationIcon(state: ZashiConfirmationState) {
    if (state.style == ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(ZashiColors.Surfaces.bgPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(state.icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        Image(
            painter = painterResource(state.icon),
            contentDescription = null
        )
    }
}

@Composable
private fun ConfirmationButton(
    state: ButtonState,
    isUnverifiedPollWarning: Boolean
) {
    if (isUnverifiedPollWarning) {
        ZashiButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            state = state,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            defaultSecondaryColors =
                ZashiButtonDefaults.secondaryColors(
                    borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                )
        )
    } else {
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun ZashiConfirmationBottomSheetPreview() =
    ZcashTheme {
        ZashiConfirmationBottomSheet(
            state =
                ZashiConfirmationState(
                    icon = android.R.drawable.ic_dialog_alert,
                    title = stringRes("Are you sure?"),
                    message = stringRes("This action cannot be undone."),
                    primaryAction =
                        ButtonState(
                            text = stringRes("Confirm"),
                            style = ButtonStyle.DESTRUCTIVE2,
                            onClick = {}
                        ),
                    secondaryAction =
                        ButtonState(
                            text = stringRes("Cancel"),
                            style = ButtonStyle.PRIMARY,
                            onClick = {}
                        ),
                    onBack = {}
                )
        )
    }
