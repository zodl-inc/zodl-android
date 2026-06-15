package co.electriccoin.zcash.ui.screen.migration.setup

import androidx.activity.compose.BackHandler
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.R as DesignR
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIcons
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState
import org.koin.androidx.compose.koinViewModel

@Composable
fun MigrationSetupScreen() {
    val vm = koinViewModel<MigrationSetupVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        MigrationSetupView(s)
    }
}

@Composable
fun MigrationSetupView(state: MigrationSetupState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
        ) {
            WalletHeaderIcons(
                state =
                    WalletHeaderIconsState(
                        isKeystone = state.isKeystone,
                        badgeIcon = R.drawable.ic_migration_coins_swap,
                    )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Move to Ironwood",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Latest Zcash network upgrade requires moving your ZEC from the Orchard pool to the " +
                    "new Ironwood pool. Your funds are safe.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZashiColors.Surfaces.bgSecondary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Orchard balance",
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                    Text(
                        text = state.orchardBalance.getValue(),
                        style = ZashiTypography.textMd,
                        fontWeight = FontWeight.SemiBold,
                        color = ZashiColors.Text.textPrimary,
                    )
                }
                state.fiatBalance?.let { fiat ->
                    Text(
                        text = fiat.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            MigrationModeSelector(
                selected = state.mode,
                onSelect = state.onModeChange,
            )
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(DesignR.drawable.ic_info),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Pool-crossing transfer amounts are visible on-chain.",
                    style = ZashiTypography.textXs,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textTertiary,
                )
            }
            Spacer(Modifier.height(20.dp))
            ZashiButton(
                state = ButtonState(
                    text = stringRes("Next"),
                    onClick = state.onConfirm,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MigrationModeSelector(
    selected: MigrationMode,
    onSelect: (MigrationMode) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MigrationModeOption(
            mode = MigrationMode.IMMEDIATE,
            title = "Migrate Immediately",
            subtitle = "Single transfer · Sends now · No privacy",
            selected = selected,
            onSelect = onSelect,
        )
        MigrationModeOption(
            mode = MigrationMode.AUTOMATIC,
            title = "Migrate with Privacy",
            subtitle = "Split transfers over time · Scheduled in background · Maximum privacy",
            selected = selected,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun MigrationModeOption(
    mode: MigrationMode,
    title: String,
    subtitle: String,
    selected: MigrationMode,
    onSelect: (MigrationMode) -> Unit,
) {
    val isSelected = mode == selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) ZashiColors.Surfaces.bgPrimary else ZashiColors.Surfaces.bgSecondary)
            .let {
                if (isSelected) {
                    it.border(1.dp, ZashiColors.Text.textPrimary, RoundedCornerShape(16.dp))
                } else {
                    it
                }
            }
            .selectable(
                selected = isSelected,
                onClick = { onSelect(mode) },
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = ZashiColors.Text.textPrimary),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = subtitle,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme {
    MigrationSetupView(
        state = MigrationSetupState(
            orchardBalance = stringRes("12.458 ZEC"),
            fiatBalance = stringRes("$4,832.86"),
            isKeystone = false,
            mode = MigrationMode.AUTOMATIC,
            onModeChange = {},
            onConfirm = {},
            onBack = {},
        )
    )
}
