package co.electriccoin.zcash.ui.screen.connectkeystone.neworactive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun KeystoneNewOrActiveView(state: KeystoneNewOrActiveState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding),
            ) {
                Image(
                    modifier = Modifier.height(32.dp),
                    painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.image_keystone),
                    contentDescription = null,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(24.dp))
                ZashiButton(
                    state = state.activeDevice,
                    modifier = Modifier.fillMaxWidth().testTag(KeystoneNewOrActiveTag.ACTIVE_DEVICE),
                    defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
                )
                Spacer(Modifier.height(12.dp))
                ZashiButton(
                    state = state.newDevice,
                    modifier = Modifier.fillMaxWidth().testTag(KeystoneNewOrActiveTag.NEW_DEVICE),
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        KeystoneNewOrActiveView(
            state =
                KeystoneNewOrActiveState(
                    subtitle = stringRes("New or active device?"),
                    message = stringRes("Select whether this is a new Keystone device or an active one."),
                    newDevice = ButtonState(stringRes("New device")) {},
                    activeDevice = ButtonState(stringRes("Active device")) {},
                    onBack = {},
                )
        )
    }
