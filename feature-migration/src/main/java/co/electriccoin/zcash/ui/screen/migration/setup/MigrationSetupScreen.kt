package co.electriccoin.zcash.ui.screen.migration.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
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
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIcons
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState
import org.koin.androidx.compose.koinViewModel
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun MigrationSetupScreen() {
    val vm = koinViewModel<MigrationSetupVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(
        state = state,
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { s ->
        BackHandler { s.onBack() }
        MigrationSetupView(s)
    }
}

@Composable
fun MigrationSetupView(state: MigrationSetupState) {
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
                            isKeystone = state.isKeystone,
                            badgeIcon = R.drawable.ic_migration_coins_swap,
                        )
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringRes(DesignR.string.migrationSetup_title).getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        buildMigrationBodyText(
                            zecAmount = state.orchardBalance.getValue(),
                            fiatAmount = state.fiatBalance?.getValue(),
                            emphasisColor = ZashiColors.Text.textPrimary,
                        ),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Text(
                    text = stringRes(DesignR.string.migrationSetup_findOutMore).getValue(),
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
                        MigrationDisclaimerRow(
                            text = stringRes(DesignR.string.migrationSetup_immediateWarning).getValue(),
                            tint = ZashiColors.Utility.WarningYellow.utilityOrange700,
                        )
                    }

                    MigrationMode.AUTOMATIC -> {
                        MigrationDisclaimerRow(
                            text = stringRes(DesignR.string.migrationSetup_automaticWarning).getValue(),
                            tint = ZashiColors.Text.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.general_next),
                            onClick = state.onConfirm,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// MOB-1620 (Figma node 3925:16408): the balance figure is a distinct, brighter emphasis color
// against the rest of this muted sentence, not just bold-in-the-same-tertiary-gray.
@Composable
private fun buildMigrationBodyText(
    zecAmount: String,
    fiatAmount: String?,
    emphasisColor: Color
): androidx.compose.ui.text.AnnotatedString {
    val prefix = stringRes(DesignR.string.migrationSetup_bodyPrefix).getValue()
    val fiatSuffix = fiatAmount?.let { stringRes(DesignR.string.migrationSetup_bodyFiatSuffix, it).getValue() }
    val suffix = stringRes(DesignR.string.migrationSetup_bodySuffix).getValue()
    return buildAnnotatedString {
        append(prefix)
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = emphasisColor)) {
            append(zecAmount)
        }
        if (fiatSuffix != null) {
            append(fiatSuffix)
        }
        append(suffix)
    }
}

@Composable
private fun MigrationDisclaimerRow(
    text: String,
    tint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = ZashiTypography.textXs,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.weight(1f),
        )
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
            mode = MigrationMode.AUTOMATIC,
            title = stringRes(DesignR.string.migrationSetup_automaticTitle).getValue(),
            subtitle = stringRes(DesignR.string.migrationSetup_automaticSubtitle).getValue(),
            isWarning = false,
            selected = selected,
            onSelect = onSelect,
        )
        MigrationModeOption(
            mode = MigrationMode.IMMEDIATE,
            title = stringRes(DesignR.string.migrationSetup_immediateTitle).getValue(),
            subtitle = stringRes(DesignR.string.migrationSetup_immediateSubtitle).getValue(),
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
        modifier =
            Modifier
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
                }.clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) ZashiColors.Surfaces.bgPrimary else ZashiColors.Surfaces.bgSecondary)
                .let {
                    when {
                        isWarningSelected -> it.border(1.dp, warningBorder, RoundedCornerShape(16.dp))
                        isSelected -> it.border(1.dp, ZashiColors.Text.textPrimary, RoundedCornerShape(16.dp))
                        else -> it
                    }
                }.selectable(
                    selected = isSelected,
                    onClick = { onSelect(mode) },
                    role = Role.RadioButton,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
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
private fun Preview() =
    ZcashTheme {
        MigrationSetupView(
            state =
                MigrationSetupState(
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
private fun PreviewImmediateSelected() =
    ZcashTheme {
        MigrationSetupView(
            state =
                MigrationSetupState(
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
