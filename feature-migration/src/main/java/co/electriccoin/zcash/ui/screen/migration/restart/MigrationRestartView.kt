package co.electriccoin.zcash.ui.screen.migration.restart

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
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
import co.electriccoin.zcash.ui.screen.common.PrivacyDisclaimerCard
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIcons
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun MigrationRestartView(state: MigrationRestartState) {
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
                WalletHeaderIcons(
                    state =
                        WalletHeaderIconsState(
                            isKeystone = false,
                            badgeIcon = R.drawable.ic_migration_coins_swap,
                        )
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringRes(DesignR.string.restartMigration_title).getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.body.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(Modifier.height(24.dp))
                SummaryCard(state)
                Spacer(Modifier.height(8.dp))
                WarningCard(state.warning.getValue())
                Spacer(Modifier.weight(1f))
                SupportRow(state.support.getValue())
                Spacer(Modifier.height(12.dp))
                ZashiButton(
                    state = state.nextButton,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    ZashiConfirmationBottomSheet(state = state.confirmationDialog)
}

@Composable
private fun SummaryCard(state: MigrationRestartState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZashiColors.Surfaces.bgSecondary)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryRow(label = state.migratedLabel.getValue(), value = state.migratedValue.getValue())
        SummaryRow(label = state.remainingLabel.getValue(), value = state.remainingValue.getValue())
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
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

// Figma: #FEF6EE background / #B93815 text — both match ZashiColors.Utility.WarningYellow's
// utilityOrange50/utilityOrange700 tokens exactly (see PrivacyDisclaimerCard, which already
// implements this exact card), so this reuses that component rather than re-hardcoding the hex.
@Composable
private fun WarningCard(text: String) {
    PrivacyDisclaimerCard(body = text)
}

@Composable
private fun SupportRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_info),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        MigrationRestartView(
            state =
                MigrationRestartState(
                    onBack = {},
                    body = stringRes(DesignR.string.restartMigration_body),
                    migratedLabel = stringRes(DesignR.string.restartMigration_summaryMigratedLabel),
                    migratedValue = stringRes(DesignR.string.restartMigration_summaryMigratedValue, 7, 11),
                    remainingLabel = stringRes(DesignR.string.restartMigration_summaryRemainingLabel),
                    remainingValue = stringRes("3.070 ZEC"),
                    warning = stringRes(DesignR.string.restartMigration_warning),
                    support = stringRes(DesignR.string.restartMigration_support),
                    nextButton = ButtonState(text = stringRes(DesignR.string.restartMigration_next), onClick = {}),
                    confirmationDialog = null,
                )
        )
    }
