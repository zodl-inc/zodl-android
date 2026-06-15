package co.electriccoin.zcash.ui.screen.migration.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
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
data object MigrationNotificationArgs

@Composable
fun MigrationNotificationScreen() {
    val vm = koinViewModel<MigrationNotificationVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        val isAlreadyGranted =
            remember {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
            }
        if (isAlreadyGranted) {
            LaunchedEffect(Unit) { s.onAutoSkip() }
        } else {
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    s.onAllow()
                }
            MigrationNotificationView(
                state = s.copy(
                    onAllow = {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            )
        }
    }
}

@Composable
fun MigrationNotificationView(state: MigrationNotificationState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(onBack = state.onBack) },
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
                text = "Allow Notifications",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "If we miss a window for a scheduled transfer or background transaction " +
                    "submission fails, we can send you a local notification and prompt you to open " +
                    "Zodl. Get notified about:",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            NotificationFeatureItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_notif_annotation_check,
                title = "Migration Status",
                body = "We will inform you whenever your funds are fully migrated to Ironwood.",
            )
            Spacer(Modifier.height(16.dp))
            NotificationFeatureItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_notif_bell_ringing,
                title = "Action Needed",
                body = "If we miss a window, we will prompt you to open the app and send a transfer manually.",
            )
            Spacer(Modifier.height(16.dp))
            NotificationFeatureItem(
                icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_notif_announcement,
                title = "Transfer Plan Changes",
                body = "If allocated funds are spent or any pre-signed transfer expires, we will let you know.",
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
                    text = "Without this permission, you'll need to open Zodl to view the migration " +
                        "progress and approve any fall-back operations.",
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
                state = ButtonState(text = stringRes("Allow Notifications"), onClick = state.onAllow),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NotificationFeatureItem(icon: Int, title: String, body: String) {
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
    MigrationNotificationView(
        state = MigrationNotificationState(onAllow = {}, onSkip = {}, onAutoSkip = {}, onBack = {})
    )
}

@PreviewScreens
@Composable
private fun PreviewForceDark() = ZcashTheme(forceDarkMode = true) {
    MigrationNotificationView(
        state = MigrationNotificationState(onAllow = {}, onSkip = {}, onAutoSkip = {}, onBack = {})
    )
}
