package co.electriccoin.zcash.ui.screen.ohttpunavailable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhttpUnavailableScreen() {
    val vm = koinViewModel<OhttpUnavailableVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    OhttpUnavailableView(state)
}

@Serializable
data object OhttpUnavailableArgs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhttpUnavailableView(
    state: OhttpUnavailableState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
    ) { innerState, contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = contentPadding.calculateBottomPadding()),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ohttp_alert_circle),
                contentDescription = null,
                modifier =
                    Modifier
                        .background(ZashiColors.Surfaces.bgPrimary, CircleShape)
                        .padding(12.dp),
            )
            Spacer(12.dp)
            Text(
                text = innerState.title.getValue(),
                style = ZashiTypography.textXl,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(4.dp)
            Text(
                text = innerState.body.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(24.dp)
            TorCard(
                torTitle = innerState.torTitle,
                torBody = innerState.torBody,
                isTorEnabled = innerState.isTorEnabled,
                onTorToggle = innerState.onTorToggle,
            )
            Spacer(24.dp)
            ZashiButton(
                state =
                    ButtonState(
                        text = innerState.keepWaitingText,
                        style = ButtonStyle.SECONDARY,
                        onClick = innerState.onKeepWaiting,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(12.dp)
            ZashiButton(
                state =
                    ButtonState(
                        text = innerState.switchToPublicText,
                        onClick = innerState.onSwitchToPublic,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TorCard(
    torTitle: StringResource,
    torBody: StringResource,
    isTorEnabled: Boolean,
    onTorToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZashiColors.Surfaces.bgPrimary, RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary), RoundedCornerShape(12.dp))
                .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = torTitle.getValue(),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = torBody.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
        Switch(checked = isTorEnabled, onCheckedChange = onTorToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        OhttpUnavailableView(state = OhttpUnavailableState.preview)
    }
