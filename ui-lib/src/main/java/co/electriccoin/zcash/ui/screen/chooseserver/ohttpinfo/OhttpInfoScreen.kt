package co.electriccoin.zcash.ui.screen.chooseserver.ohttpinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhttpInfoScreen() {
    val vm = koinViewModel<OhttpInfoVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    OhttpInfoView(state)
}

@Serializable
data object OhttpInfoArgs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhttpInfoView(
    state: OhttpInfoState?,
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
                painter = painterResource(R.drawable.ic_ohttp_incognito),
                contentDescription = null,
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
            Spacer(20.dp)
            OhttpInfoItem(R.drawable.ic_ohttp_lock, R.string.ohttpInfo_item1Title, R.string.ohttpInfo_item1Body)
            Spacer(16.dp)
            OhttpInfoItem(R.drawable.ic_ohttp_shield, R.string.ohttpInfo_item2Title, R.string.ohttpInfo_item2Body)
            Spacer(16.dp)
            OhttpInfoItem(
                R.drawable.ic_ohttp_check_circle,
                R.string.ohttpInfo_item3Title,
                R.string.ohttpInfo_item3Body
            )
            Spacer(24.dp)
            ZashiButton(
                state = ButtonState(text = innerState.gotItText, onClick = innerState.onGotIt),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OhttpInfoItem(
    icon: Int,
    title: Int,
    body: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painter = painterResource(icon), contentDescription = null)
        Column {
            Text(
                text = stringRes(title).getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = stringRes(body).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        OhttpInfoView(state = OhttpInfoState.preview)
    }
