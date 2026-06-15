package co.electriccoin.zcash.ui.screen.migration.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class MigrationPrivacyArgs(val mode: MigrationMode)

@Composable
fun MigrationPrivacyScreen(args: MigrationPrivacyArgs) {
    val vm = koinViewModel<MigrationPrivacyVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        MigrationPrivacyView(s)
    }
}

@Composable
fun MigrationPrivacyView(state: MigrationPrivacyState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
                regularActions = {},
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
        ) {
            Image(
                painter = painterResource(co.electriccoin.zcash.ui.R.drawable.ic_tor_settings),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Network Privacy",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Enable Tor to route this transfer privately through the Tor network. This prevents " +
                    "your IP address from being linked to the transfer.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "You may use a trusted VPN if Tor is unavailable in your region.",
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "What Happens Next",
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            PrivacyComparisonItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_shield_tick,
                title = "With Tor",
                body = "IP hidden · All transfers unlinkable to your device",
            )
            Spacer(Modifier.height(8.dp))
            PrivacyComparisonItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_shield_outline,
                title = "Without Tor or VPN",
                body = "Transfers still de-correlated in time · IP visible to network operators",
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZashiColors.Surfaces.bgPrimary)
                    .border(1.dp, ZashiColors.Surfaces.strokeSecondary, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Route via Tor",
                        style = ZashiTypography.textMd,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textPrimary,
                    )
                    Text(
                        text = "Use Tor for transaction submission.",
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
                Switch(
                    checked = state.useTor,
                    onCheckedChange = state.onTorToggle,
                )
            }
            Spacer(Modifier.height(24.dp))
            ZashiButton(
                state = ButtonState(text = stringRes("Next"), onClick = state.onConfirm),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrivacyComparisonItem(icon: Int, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ZashiColors.Text.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = body,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@PreviewScreens
@Composable
private fun PreviewImmediate() = ZcashTheme {
    MigrationPrivacyView(
        state = MigrationPrivacyState(
            mode = MigrationMode.IMMEDIATE,
            useTor = false,
            onTorToggle = {},
            onConfirm = {},
            onSkip = {},
            onBack = {},
        )
    )
}

@PreviewScreens
@Composable
private fun PreviewAutomatic() = ZcashTheme {
    MigrationPrivacyView(
        state = MigrationPrivacyState(
            mode = MigrationMode.AUTOMATIC,
            useTor = true,
            onTorToggle = {},
            onConfirm = {},
            onSkip = {},
            onBack = {},
        )
    )
}
