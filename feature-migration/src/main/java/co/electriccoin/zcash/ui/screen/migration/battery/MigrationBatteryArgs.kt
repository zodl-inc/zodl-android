package co.electriccoin.zcash.ui.screen.migration.battery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.provider.IsBackgroundExecutionAvailableProvider
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
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
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import co.electriccoin.zcash.ui.design.R as DesignR

@Serializable
data object MigrationBatteryArgs

@Composable
fun MigrationBatteryScreen() {
    val vm = koinViewModel<MigrationBatteryVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isBackgroundExecutionAvailableProvider = koinInject<IsBackgroundExecutionAvailableProvider>()
    LceRenderer(
        state = state,
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { s ->
        BackHandler { s.onBack() }

        fun isUnrestricted() = isBackgroundExecutionAvailableProvider.isAvailable()

        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                // Only proceed if the user actually granted the exemption in Settings.
                // Otherwise stay on this screen so they can retry Allow or tap Skip.
                if (isUnrestricted()) s.onAllow()
            }
        MigrationBatteryView(
            state =
                s.copy(
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Three-state battery setting: the one-tap exemption dialog only
                            // lifts OPTIMIZED. A RESTRICTED app cannot be un-restricted by any
                            // dialog — send the user to App Info instead, where the Battery
                            // entry offers the change; the result-launcher re-check below
                            // handles both paths identically.
                            val intent =
                                if (isBackgroundExecutionAvailableProvider.state() ==
                                    co.electriccoin.zcash.ui.common.provider.BackgroundExecutionState.RESTRICTED
                                ) {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                } else {
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
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

@Composable
fun MigrationBatteryView(state: MigrationBatteryState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
                regularActions = {},
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
                Text(
                    text = stringRes(DesignR.string.migrationBattery_title).getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(DesignR.string.migrationBattery_body).getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.height(24.dp))
                BatteryFeatureItem(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_battery_clock_check,
                    title = stringRes(DesignR.string.migrationBattery_scheduledWindowsTitle).getValue(),
                    body = stringRes(DesignR.string.migrationBattery_scheduledWindowsBody).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                BatteryFeatureItem(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_battery_face_smile,
                    title = stringRes(DesignR.string.migrationBattery_noNeedToOpenTitle).getValue(),
                    body = stringRes(DesignR.string.migrationBattery_noNeedToOpenBody).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                BatteryFeatureItem(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_battery_check_heart,
                    title = stringRes(DesignR.string.migrationBattery_fixedScheduleTitle).getValue(),
                    body = stringRes(DesignR.string.migrationBattery_fixedScheduleBody).getValue(),
                )
                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                        contentDescription = null,
                        tint = ZashiColors.Utility.WarningYellow.utilityOrange700,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringRes(DesignR.string.migrationBattery_withoutPermissionHint).getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Utility.WarningYellow.utilityOrange700,
                    )
                }
                Spacer(Modifier.height(24.dp))
                ZashiButton(
                    state = ButtonState(text = stringRes(DesignR.string.migration_common_skip), onClick = state.onSkip),
                    modifier = Modifier.fillMaxWidth(),
                    defaultPrimaryColors =
                        ZashiButtonDefaults.secondaryColors(
                            contentColor = ZashiColors.Utility.WarningYellow.utilityOrange700,
                            borderColor = ZashiColors.Utility.WarningYellow.utilityOrange300,
                        ),
                )
                Spacer(Modifier.height(12.dp))
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migration_common_allow),
                            onClick = state.onAllow
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
private fun Preview() =
    ZcashTheme {
        MigrationBatteryView(
            state = MigrationBatteryState(onAllow = {}, onSkip = {}, onAutoSkip = {}, onBack = {})
        )
    }
