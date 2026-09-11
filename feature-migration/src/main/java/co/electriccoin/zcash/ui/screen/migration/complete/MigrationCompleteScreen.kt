package co.electriccoin.zcash.ui.screen.migration.complete

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import co.electriccoin.zcash.ui.screen.common.PrivacyDisclaimerCard
import co.electriccoin.zcash.ui.screen.migration.component.MigrationFailureBottomSheet
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import co.electriccoin.zcash.ui.design.R as DesignR

data class MigrationCompleteState(
    val totalTransferred: StringResource,
    val remainingDust: StringResource?,
    val isDustLocked: Boolean,
    val transfersProgress: StringResource,
    val duration: StringResource,
    val onDone: () -> Unit,
    val onMigrateAnyway: () -> Unit,
    val onLockBalance: () -> Unit,
    val onHelp: () -> Unit,
    val isMigrating: Boolean = false,
    val isLocking: Boolean = false,
    val failureSheet: co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState? = null,
    // MOB-1750: true for a small leftover Orchard balance not tied to an unseen in-app migration
    // celebration ("You've moved to Ironwood" copy, no Transfers/Duration rows) — false for the
    // original post-migration celebration variant ("Migration Complete"), unchanged.
    val isResidueOnly: Boolean = false,
)

@Serializable
data class MigrationCompleteArgs(
    val isResidueOnly: Boolean = false
)

@Composable
fun MigrationCompleteScreen(args: MigrationCompleteArgs) {
    val vm = koinViewModel<MigrationCompleteVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(
        state = state,
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { MigrationCompleteView(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationCompleteView(state: MigrationCompleteState) {
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
                // The "?" opens the lock explainer as pure information. It's only meaningful while
                // there's an unlocked residue that can still be locked, so it's hidden once the
                // balance is locked or when there's no residual dust at all.
                regularActions = {
                    if (state.remainingDust != null && !state.isDustLocked) {
                        ZashiIconButton(
                            state =
                                IconButtonState(
                                    icon = R.drawable.ic_help,
                                    onClick = state.onHelp,
                                ),
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(20.dp))
                    }
                },
            )
        },
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(co.electriccoin.zcash.migration.R.drawable.ic_migration_complete),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text =
                        stringRes(
                            if (state.isResidueOnly) {
                                DesignR.string.migrationComplete_residueTitle
                            } else {
                                DesignR.string.migrationComplete_title
                            }
                        ).getValue(),
                    style = ZashiTypography.header5,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        stringRes(
                            if (state.isResidueOnly) {
                                DesignR.string.migrationComplete_residueSubtitle
                            } else {
                                DesignR.string.migrationComplete_subtitle
                            }
                        ).getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                SummaryCard(state)
                Spacer(Modifier.weight(1f))
                when {
                    state.remainingDust == null -> {
                        ZashiButton(
                            state =
                                ButtonState(
                                    text = stringRes(DesignR.string.migration_common_gotIt),
                                    onClick = state.onDone
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    state.isDustLocked -> {
                        Spacer(Modifier.height(20.dp))
                        LockedDisclaimer(dustAmount = state.remainingDust.getValue())
                        Spacer(Modifier.height(20.dp))
                        ZashiButton(
                            state =
                                ButtonState(
                                    text = stringRes(DesignR.string.migration_common_gotIt),
                                    onClick = state.onDone
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> {
                        Spacer(Modifier.height(20.dp))
                        PrivacyDisclaimerCard(
                            title = stringRes(DesignR.string.migrationComplete_orchardBalanceRemainingTitle).getValue(),
                            body =
                                stringRes(
                                    if (state.isResidueOnly) {
                                        DesignR.string.migrationComplete_residueRecommendationBody
                                    } else {
                                        DesignR.string.migrationComplete_orchardBalanceRemainingBody
                                    },
                                    state.remainingDust.getValue()
                                ).getValue(),
                        )
                        Spacer(Modifier.height(20.dp))
                        ZashiButton(
                            state =
                                ButtonState(
                                    text =
                                        stringRes(
                                            if (state.isMigrating) {
                                                DesignR.string.migrationComplete_migrating
                                            } else {
                                                DesignR.string.migrationComplete_migrateAnyway
                                            }
                                        ),
                                    onClick = state.onMigrateAnyway,
                                    isEnabled = !state.isMigrating && !state.isLocking,
                                    isLoading = state.isMigrating,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            defaultPrimaryColors =
                                ZashiButtonDefaults.secondaryColors(
                                    contentColor = ZashiColors.Utility.WarningYellow.utilityOrange700,
                                    borderColor = ZashiColors.Utility.WarningYellow.utilityOrange300,
                                ),
                        )
                        Spacer(Modifier.height(8.dp))
                        ZashiButton(
                            state =
                                ButtonState(
                                    text =
                                        stringRes(
                                            if (state.isLocking) {
                                                DesignR.string.migrationComplete_lockingBalance
                                            } else {
                                                DesignR.string.migrationComplete_lockBalance
                                            }
                                        ),
                                    onClick = state.onLockBalance,
                                    isEnabled = !state.isMigrating && !state.isLocking,
                                    isLoading = state.isLocking,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
    MigrationFailureBottomSheet(state.failureSheet)
}

@Composable
private fun SummaryCard(state: MigrationCompleteState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZashiColors.Surfaces.bgSecondary)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isResidueOnly) {
            // MOB-1750: reduced summary — no Transfers/Duration rows, since a residue not tied
            // to an unseen in-app celebration has no round-trip campaign to report on (whether
            // it's genuinely 0 sent-in-app, or an already-seen completion's leftover residue).
            SummaryRow(
                label = stringRes(DesignR.string.migrationComplete_inIronwoodLabel).getValue(),
                value = state.totalTransferred.getValue(),
            )
            state.remainingDust?.let { dust ->
                SummaryRow(
                    label = stringRes(DesignR.string.migrationComplete_leftInOrchardLabel).getValue(),
                    value = dust.getValue(),
                )
            }
        } else {
            SummaryRow(
                label = stringRes(DesignR.string.migrationComplete_totalTransferredLabel).getValue(),
                value = state.totalTransferred.getValue(),
            )
            state.remainingDust?.let { dust ->
                SummaryRow(
                    label = stringRes(DesignR.string.migrationComplete_remainingDustLabel).getValue(),
                    value = dust.getValue(),
                )
            }
            SummaryRow(
                label = stringRes(DesignR.string.migrationComplete_transfersLabel).getValue(),
                value = state.transfersProgress.getValue(),
            )
            SummaryRow(
                label = stringRes(DesignR.string.migrationComplete_durationLabel).getValue(),
                value = state.duration.getValue(),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary,
        )
    }
}

@Composable
private fun LockedDisclaimer(dustAmount: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZashiColors.Surfaces.bgSecondary)
                .padding(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(DesignR.string.migrationComplete_orchardBalanceLockedTitle).getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(DesignR.string.migrationComplete_orchardBalanceLockedBody, dustAmount).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@PreviewScreens
@Composable
private fun PreviewWithDust() =
    ZcashTheme {
        MigrationCompleteView(
            state =
                MigrationCompleteState(
                    totalTransferred = stringRes("12.458 ZEC"),
                    remainingDust = stringRes("0.00031 ZEC"),
                    isDustLocked = false,
                    transfersProgress = stringRes("5 of 5 sent"),
                    duration = stringRes("~24 hours"),
                    onDone = {},
                    onMigrateAnyway = {},
                    onLockBalance = {},
                    onHelp = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewWithLockedDust() =
    ZcashTheme {
        MigrationCompleteView(
            state =
                MigrationCompleteState(
                    totalTransferred = stringRes("12.458 ZEC"),
                    remainingDust = stringRes("0.00031 ZEC"),
                    isDustLocked = true,
                    transfersProgress = stringRes("5 of 5 sent"),
                    duration = stringRes("~24 hours"),
                    onDone = {},
                    onMigrateAnyway = {},
                    onLockBalance = {},
                    onHelp = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewNoDust() =
    ZcashTheme {
        MigrationCompleteView(
            state =
                MigrationCompleteState(
                    totalTransferred = stringRes("12.458 ZEC"),
                    remainingDust = null,
                    isDustLocked = false,
                    transfersProgress = stringRes("5 of 5 sent"),
                    duration = stringRes("~24 hours"),
                    onDone = {},
                    onMigrateAnyway = {},
                    onLockBalance = {},
                    onHelp = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewResidueOnly() =
    ZcashTheme {
        MigrationCompleteView(
            state =
                MigrationCompleteState(
                    totalTransferred = stringRes("12.45 ZEC"),
                    remainingDust = stringRes("0.008 ZEC"),
                    isDustLocked = false,
                    transfersProgress = stringRes("0 of 0 sent"),
                    duration = stringRes("~0 hours"),
                    isResidueOnly = true,
                    onDone = {},
                    onMigrateAnyway = {},
                    onLockBalance = {},
                    onHelp = {},
                )
        )
    }
