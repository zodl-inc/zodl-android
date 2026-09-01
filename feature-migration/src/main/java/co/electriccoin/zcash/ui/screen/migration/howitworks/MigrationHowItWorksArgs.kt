package co.electriccoin.zcash.ui.screen.migration.howitworks

import androidx.activity.compose.BackHandler
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
data object MigrationHowItWorksArgs

@Composable
fun MigrationHowItWorksScreen() {
    val vm = koinViewModel<MigrationHowItWorksVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(
        state = state,
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { s ->
        BackHandler { s.onBack() }
        MigrationHowItWorksView(s)
    }
}

@Composable
fun MigrationHowItWorksView(state: MigrationHowItWorksState) {
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
                    text = stringRes(DesignR.string.migrationHowItWorks_title).getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(DesignR.string.migrationHowItWorks_subtitle).getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.height(32.dp))
                HowItWorksStep(
                    icon = co.electriccoin.zcash.ui.R.drawable.ic_migration_coins_swap,
                    title = stringRes(DesignR.string.migrationHowItWorks_splitScheduleTitle).getValue(),
                    description = stringRes(DesignR.string.migrationHowItWorks_splitScheduleDescription).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                HowItWorksStep(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_check_square_broken,
                    title = stringRes(DesignR.string.migrationHowItWorks_approveOnceTitle).getValue(),
                    description = stringRes(DesignR.string.migrationHowItWorks_approveOnceDescription).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                HowItWorksStep(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_notif_bell_ringing,
                    title = stringRes(DesignR.string.migrationHowItWorks_ifSomethingFailsTitle).getValue(),
                    description = stringRes(DesignR.string.migrationHowItWorks_ifSomethingFailsDescription).getValue(),
                )
                Spacer(Modifier.height(16.dp))
                HowItWorksStep(
                    icon = co.electriccoin.zcash.migration.R.drawable.ic_migration_calendar,
                    title = stringRes(DesignR.string.migrationHowItWorks_largeBalanceTitle).getValue(),
                    description = stringRes(DesignR.string.migrationHowItWorks_largeBalanceDescription).getValue(),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                        contentDescription = null,
                        tint = ZashiColors.Text.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringRes(DesignR.string.migrationHowItWorks_disclaimer).getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
                Spacer(Modifier.height(20.dp))
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migration_common_continue),
                            onClick = state.onContinue
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HowItWorksStep(icon: Int, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ZashiColors.Text.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = description,
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
        MigrationHowItWorksView(
            state = MigrationHowItWorksState(onContinue = {}, onBack = {})
        )
    }
