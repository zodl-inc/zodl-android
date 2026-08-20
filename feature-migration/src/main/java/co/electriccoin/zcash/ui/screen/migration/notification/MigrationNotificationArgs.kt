package co.electriccoin.zcash.ui.screen.migration.notification

import android.Manifest
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import co.electriccoin.zcash.ui.design.R as DesignR

@Serializable
data object MigrationNotificationArgs

@Composable
fun MigrationNotificationScreen() {
    val vm = koinViewModel<MigrationNotificationVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(
        state = state,
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { s ->
        BackHandler { s.onBack() }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                s.onAllow()
            }
        MigrationNotificationView(
            state =
                s.copy(
                    onAllow = {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
        )
    }
}

@Composable
fun MigrationNotificationView(state: MigrationNotificationState) {
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
                    text = stringRes(DesignR.string.migrationNotifPermission_title).getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(DesignR.string.migrationNotifPermission_subtitle).getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.height(24.dp))
                NotificationFeatureItem(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_notif_annotation_check,
                    title = stringRes(DesignR.string.migrationNotifPermission_statusTitle).getValue(),
                    body = stringRes(DesignR.string.migrationNotifPermission_statusBody).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                NotificationFeatureItem(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_notif_bell_ringing,
                    title = stringRes(DesignR.string.migrationNotifPermission_actionNeededTitle).getValue(),
                    body = stringRes(DesignR.string.migrationNotifPermission_actionNeededBody).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                NotificationFeatureItem(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_notif_announcement,
                    title = stringRes(DesignR.string.migrationNotifPermission_planChangesTitle).getValue(),
                    body = stringRes(DesignR.string.migrationNotifPermission_planChangesBody).getValue(),
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
                        text = stringRes(DesignR.string.migrationNotifPermission_warningHint).getValue(),
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
private fun Preview() =
    ZcashTheme {
        MigrationNotificationView(
            state = MigrationNotificationState(onAllow = {}, onSkip = {}, onAutoSkip = {}, onBack = {})
        )
    }

@PreviewScreens
@Composable
private fun PreviewForceDark() =
    ZcashTheme(forceDarkMode = true) {
        MigrationNotificationView(
            state = MigrationNotificationState(onAllow = {}, onSkip = {}, onAutoSkip = {}, onBack = {})
        )
    }
