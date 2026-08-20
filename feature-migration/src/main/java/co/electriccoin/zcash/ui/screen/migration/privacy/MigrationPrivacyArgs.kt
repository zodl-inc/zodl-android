package co.electriccoin.zcash.ui.screen.migration.privacy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
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
import co.electriccoin.zcash.ui.design.theme.colors.ZashiLightColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import co.electriccoin.zcash.ui.design.R as DesignR

@Serializable
data class MigrationPrivacyArgs(
    val mode: MigrationMode
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationPrivacyScreen(args: MigrationPrivacyArgs) {
    val vm = koinViewModel<MigrationPrivacyVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    MigrationPrivacyView(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationPrivacyView(
    state: MigrationPrivacyState?,
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
                Spacer(32.dp)
                TorToggleCard(innerState.checkbox)
                Spacer(32.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migration_common_gotIt),
                            onClick = innerState.onConfirm
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ZashiFrostedSheetHeader(
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                onHeightChanged = { headerHeight = it },
                title = {
                    Column(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
                    ) {
                        Image(
                            painter = painterResource(co.electriccoin.zcash.ui.R.drawable.ic_tor_settings),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(16.dp)
                        Text(
                            text = stringRes(DesignR.string.migration_common_enableTorProtectionTitle).getValue(),
                            style = ZashiTypography.textXl,
                            fontWeight = FontWeight.SemiBold,
                            color = ZashiColors.Text.textPrimary,
                        )
                    }
                }
            )
        }
    }
}

// Hand-styled pill switch (Material3's default Switch renders solid black-on-black in this app's
// theme). Based on RestoreTorView.kt's toggle, but per the migration Figma the thumb is a
// theme-independent white and the card shows no border highlight in EITHER state (MOB-1620: the
// OFF state was still showing one after the ON-state border was removed). If the two Tor toggles
// must stay identical, mirror these two tweaks in RestoreTorView.kt as well.
@Suppress("MagicNumber")
@Composable
private fun TorToggleCard(state: CheckboxState) {
    Surface(
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, Color.Transparent),
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        onClick = state.onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                state.subtitle?.let {
                    Spacer(2.dp)
                    Text(
                        text = it.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
            }
            Spacer(20.dp)
            val switchColor by animateColorAsState(
                if (state.isChecked) {
                    ZashiColors.Accents.green
                } else {
                    ZashiColors.Utility.Gray.utilityGray200
                }
            )
            val offset by animateDpAsState(if (state.isChecked) 21.dp else 0.dp)
            Surface(
                modifier =
                    Modifier
                        .width(64.dp)
                        .height(28.dp),
                color = switchColor,
                shape = CircleShape,
            ) {
                Box(modifier = Modifier.padding(2.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .offset(x = offset)
                                .width(39.dp)
                                .height(24.dp)
                                .clip(CircleShape)
                                .background(ZashiLightColors.Surfaces.bgPrimary)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        MigrationPrivacyView(
            state =
                MigrationPrivacyState(
                    body = stringRes(DesignR.string.migrationPrivacy_bodyAutomatic),
                    checkbox =
                        CheckboxState(
                            title = stringRes(DesignR.string.migration_common_enableTorProtectionTitle),
                            subtitle = stringRes(DesignR.string.migration_common_torCheckboxSubtitle),
                            isChecked = true,
                            onClick = {},
                        ),
                    onConfirm = {},
                    onBack = {},
                )
        )
    }
