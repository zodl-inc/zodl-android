package co.electriccoin.zcash.ui.screen.migration.lockexplainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import co.electriccoin.zcash.ui.design.R as DesignR

@Serializable
data object MigrationLockExplainerArgs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationLockExplainerScreen() {
    val vm = koinViewModel<MigrationLockExplainerVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    MigrationLockExplainerView(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationLockExplainerView(
    state: MigrationLockExplainerState?,
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
                val bullet1Prefix = stringRes(DesignR.string.migrationLockExplainer_bullet1Prefix).getValue()
                val bullet1Bold = stringRes(DesignR.string.migrationLockExplainer_bullet1Bold).getValue()
                val bullet1Suffix = stringRes(DesignR.string.migrationLockExplainer_bullet1Suffix).getValue()
                LockExplainerBullet(
                    buildAnnotatedString {
                        append(bullet1Prefix)
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bullet1Bold) }
                        append(bullet1Suffix)
                    }
                )
                Spacer(16.dp)
                val bullet2Bold = stringRes(DesignR.string.migrationLockExplainer_bullet2Bold).getValue()
                val bullet2Suffix = stringRes(DesignR.string.migrationLockExplainer_bullet2Suffix).getValue()
                LockExplainerBullet(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(bullet2Bold)
                        }
                        append(bullet2Suffix)
                    }
                )
                Spacer(16.dp)
                val bullet3Prefix = stringRes(DesignR.string.migrationLockExplainer_bullet3Prefix).getValue()
                val bullet3Bold = stringRes(DesignR.string.migrationLockExplainer_bullet3Bold).getValue()
                LockExplainerBullet(
                    buildAnnotatedString {
                        append(bullet3Prefix)
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bullet3Bold) }
                    }
                )
                Spacer(32.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migration_common_gotIt),
                            onClick = innerState.onGotIt,
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
                        text = stringRes(DesignR.string.migrationLockExplainer_title).getValue(),
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
private fun LockExplainerBullet(text: androidx.compose.ui.text.AnnotatedString) {
    Text(
        text = text,
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        MigrationLockExplainerView(
            state = MigrationLockExplainerState(onGotIt = {}, onBack = {})
        )
    }
