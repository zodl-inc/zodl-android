package co.electriccoin.zcash.ui.screen.migration.customservertor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import co.electriccoin.zcash.ui.design.R as DesignR

@Serializable
data class MigrationCustomServerTorArgs(
    val mode: MigrationMode
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationCustomServerTorScreen(args: MigrationCustomServerTorArgs) {
    val vm = koinViewModel<MigrationCustomServerTorVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    MigrationCustomServerTorView(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationCustomServerTorView(
    state: MigrationCustomServerTorState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        dragHandle = null,
    ) { innerState, contentPadding ->
        val hazeState = rememberZashiFrostState()
        var headerHeight by remember { mutableStateOf(0.dp) }
        Box {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostSource(hazeState)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = headerHeight,
                            bottom = contentPadding.calculateBottomPadding()
                        ),
            ) {
                Text(
                    text = innerState.body.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(24.dp)
                RiskCard(
                    title = stringRes(DesignR.string.migration_common_whatAreTheRisks).getValue(),
                    body = innerState.riskBody.getValue(),
                )
                Spacer(32.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migration_common_continueWithoutTor),
                            onClick = innerState.onContinueWithoutTor
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors(),
                )
                Spacer(8.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migrationCustomServerTor_switchServer),
                            onClick = innerState.onSwitchServer
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ZashiFrostedSheetHeader(
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                onHeightChanged = { headerHeight = it },
                title = {
                    Text(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                        text = stringRes(DesignR.string.migrationCustomServerTor_title).getValue(),
                        style = ZashiTypography.textXl,
                        fontWeight = FontWeight.SemiBold,
                        color = ZashiColors.Text.textPrimary,
                    )
                }
            )
        }
    }
}

@Composable
private fun RiskCard(title: String, body: String) {
    Surface(
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(2.dp)
            Text(
                text = body,
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        MigrationCustomServerTorView(
            state =
                MigrationCustomServerTorState(
                    body = stringRes(DesignR.string.migrationCustomServerTor_bodyAutomatic),
                    riskBody = stringRes(DesignR.string.migrationCustomServerTor_riskBodyAutomatic),
                    onContinueWithoutTor = {},
                    onSwitchServer = {},
                    onBack = {},
                )
        )
    }
