package co.electriccoin.zcash.ui.screen.migration.battery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
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

@Serializable
data object MigrationBatteryArgs

@Composable
fun MigrationBatteryScreen() {
    val vm = koinViewModel<MigrationBatteryVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }

        fun isUnrestricted() =
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true

        val isAlreadyUnrestricted = remember { isUnrestricted() }
        if (isAlreadyUnrestricted) {
            LaunchedEffect(Unit) { s.onAutoSkip() }
        } else {
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    // Only proceed if the user actually granted the exemption in Settings.
                    // Otherwise stay on this screen so they can retry Allow or tap Skip.
                    if (isUnrestricted()) s.onAllow()
                }
            MigrationBatteryView(
                state = s.copy(
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            launcher.launch(intent)
                        } else {
                            s.onAllow()
                        }
                    }
                )
            )
        }
    }
}

@Composable
fun MigrationBatteryView(state: MigrationBatteryState) {
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
            Text(
                text = "Disable Battery Optimization",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "To send migration transfers at their scheduled times, Zodl needs permission to run in the background.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            BatteryFeatureItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_battery_clock_check,
                title = "Transfers send at their scheduled windows",
                body = "Your device wakes up and broadcasts each transfer at its scheduled window — no action needed.",
            )
            Spacer(Modifier.height(16.dp))
            BatteryFeatureItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_battery_face_smile,
                title = "No need to open the app for each send",
                body = "Once the schedule is committed, all transfers broadcast in the background over the next 24 hours.",
            )
            Spacer(Modifier.height(16.dp))
            BatteryFeatureItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_battery_check_heart,
                title = "Stays de-correlated from your app activity",
                body = "Transfers go out at fixed network-wide windows, not tied to when you open Zodl.",
            )
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                    contentDescription = null,
                    tint = ZashiColors.Text.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Without this permission background operations may fail and you may experience delays.",
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
            }
            Spacer(Modifier.height(24.dp))
            ZashiButton(
                state = ButtonState(text = stringRes("Skip — I'll open the app"), onClick = state.onSkip),
                modifier = Modifier.fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
            )
            Spacer(Modifier.height(12.dp))
            ZashiButton(
                state = ButtonState(text = stringRes("Allow Background Access"), onClick = state.onAllow),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BatteryFeatureItem(icon: Int, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ZashiColors.Text.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = body,
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme {
    MigrationBatteryView(
        state = MigrationBatteryState(onAllow = {}, onSkip = {}, onAutoSkip = {}, onBack = {})
    )
}
