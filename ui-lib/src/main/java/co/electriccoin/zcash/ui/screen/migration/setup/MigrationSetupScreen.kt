package co.electriccoin.zcash.ui.screen.migration.setup

import androidx.activity.compose.BackHandler
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.PrivacyDisclaimerCard
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIcons
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState
import co.electriccoin.zcash.ui.R
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
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
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
                text = buildMigrationBodyText(state.orchardBalance.getValue(), state.fiatBalance?.getValue()),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Text(
                text = "Find out more",
                style = ZashiTypography.textSm.copy(textDecoration = TextDecoration.Underline),
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
                modifier = Modifier.clickable(onClick = state.onFindOutMore),
            )
            Spacer(Modifier.height(24.dp))
            MigrationModeSelector(
                selected = state.mode,
                onSelect = state.onModeChange,
            )
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.weight(1f))
            when (state.mode) {
                MigrationMode.IMMEDIATE -> {
                    PrivacyDisclaimerCard(
                        title = "Privacy Disclaimer",
                        body = "Your full balance will be revealed as crossing the pool boundary reveals the " +
                            "transaction amount. We recommend selecting Migrate with Privacy instead.",
                    )
                    Spacer(Modifier.height(20.dp))
                }
                MigrationMode.AUTOMATIC -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                            contentDescription = null,
                            tint = ZashiColors.Text.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Pool-crossing transfer amounts are visible on-chain.",
                            style = ZashiTypography.textXs,
                            fontWeight = FontWeight.Medium,
                            color = ZashiColors.Text.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
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

private fun buildMigrationBodyText(zecAmount: String, fiatAmount: String?) = buildAnnotatedString {
    append("Latest Zcash network upgrade requires moving your ")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(zecAmount)
    }
    if (fiatAmount != null) {
        append(" ($fiatAmount)")
    }
    append(" from the Orchard pool to the new Ironwood pool. Your funds are safe.")
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
            mode = MigrationMode.AUTOMATIC,
            title = "Migrate with Privacy",
            subtitle = "Split transfers over time · Scheduled in background · Maximum privacy",
            isWarning = false,
            selected = selected,
            onSelect = onSelect,
        )
        MigrationModeOption(
            mode = MigrationMode.IMMEDIATE,
            title = "Migrate Immediately",
            subtitle = "Single transfer · Sends now · No privacy",
            isWarning = true,
            selected = selected,
            onSelect = onSelect,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun MigrationModeOption(
    mode: MigrationMode,
    title: String,
    subtitle: String,
    isWarning: Boolean,
    selected: MigrationMode,
    onSelect: (MigrationMode) -> Unit,
) {
    val isSelected = mode == selected
    val isWarningSelected = isSelected && isWarning
    val warningBorder = ZashiColors.Utility.WarningYellow.utilityOrange500
    val warningRing = ZashiColors.Utility.WarningYellow.utilityOrange200
    val warningRadio = ZashiColors.Utility.WarningYellow.utilityOrange600
    val warningTitle = ZashiColors.Utility.WarningYellow.utilityOrange700

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isWarningSelected) {
                    it.drawBehind {
                        val ringWidthPx = 2.dp.toPx()
                        val cornerPx = 16.dp.toPx() + ringWidthPx
                        drawRoundRect(
                            color = warningRing,
                            topLeft = Offset(-ringWidthPx, -ringWidthPx),
                            size = Size(size.width + ringWidthPx * 2, size.height + ringWidthPx * 2),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                        )
                    }
                } else {
                    it
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) ZashiColors.Surfaces.bgPrimary else ZashiColors.Surfaces.bgSecondary)
            .let {
                when {
                    isWarningSelected -> it.border(1.dp, warningBorder, RoundedCornerShape(16.dp))
                    isSelected -> it.border(1.dp, ZashiColors.Text.textPrimary, RoundedCornerShape(16.dp))
                    else -> it
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
            colors = RadioButtonDefaults.colors(
                selectedColor = if (isWarningSelected) warningRadio else ZashiColors.Text.textPrimary,
                unselectedColor = ZashiColors.Surfaces.strokePrimary,
            ),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.Medium,
                color = if (isWarningSelected) warningTitle else ZashiColors.Text.textPrimary,
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
            onFindOutMore = {},
            onConfirm = {},
            onBack = {},
        )
    )
}

@PreviewScreens
@Composable
private fun PreviewImmediateSelected() = ZcashTheme {
    MigrationSetupView(
        state = MigrationSetupState(
            orchardBalance = stringRes("12.458 ZEC"),
            fiatBalance = stringRes("$4,832.86"),
            isKeystone = false,
            mode = MigrationMode.IMMEDIATE,
            onModeChange = {},
            onFindOutMore = {},
            onConfirm = {},
            onBack = {},
        )
    )
}
