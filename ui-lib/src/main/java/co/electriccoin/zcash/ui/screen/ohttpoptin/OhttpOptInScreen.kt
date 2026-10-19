package co.electriccoin.zcash.ui.screen.ohttpoptin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBaseSettingsOptIn
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiInfoRow
import co.electriccoin.zcash.ui.design.component.ZashiTextButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun OhttpOptInScreen() {
    val vm = koinViewModel<OhttpOptInVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.onBack() }
    OhttpOptInView(state = state)
}

@Serializable
data object OhttpOptInArgs

@Composable
fun OhttpOptInView(state: OhttpOptInState) {
    ZashiBaseSettingsOptIn(
        header = stringResource(R.string.ohttpOptIn_title),
        image = R.drawable.ic_ohttp_incognito,
        onDismiss = state.onBack,
        content = {
            Spacer(20.dp)
            Text(
                text = stringResource(R.string.ohttpOptIn_body),
                color = ZashiColors.Text.textTertiary,
                fontSize = 14.sp,
            )
            Spacer(24.dp)
            ZashiInfoRow(
                icon = R.drawable.ic_ohttp_lock,
                title = stringResource(R.string.ohttpInfo_item1Title),
                subtitle = stringResource(R.string.ohttpInfo_item1Body),
            )
            Spacer(20.dp)
            ZashiInfoRow(
                icon = R.drawable.ic_ohttp_shield,
                title = stringResource(R.string.ohttpInfo_item2Title),
                subtitle = stringResource(R.string.ohttpInfo_item2Body),
            )
            Spacer(20.dp)
            ZashiInfoRow(
                icon = R.drawable.ic_ohttp_check_circle,
                title = stringResource(R.string.ohttpInfo_item3Title),
                subtitle = stringResource(R.string.ohttpInfo_item3Body),
            )
        },
        info = null,
        footer = {
            ZashiTextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = state.onNotNow,
            ) {
                Text(
                    text = stringResource(R.string.ohttpOptIn_notNow),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(4.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.ohttpOptIn_turnOn),
                onClick = state.onTurnOn,
                colors = ZashiButtonDefaults.primaryColors()
            )
        }
    )
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            OhttpOptInView(state = OhttpOptInState(onTurnOn = {}, onNotNow = {}, onBack = {}))
        }
    }
