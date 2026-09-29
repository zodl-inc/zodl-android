package co.electriccoin.zcash.ui.screen.connectkeystone.explainer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoneExplainerView(
    state: KeystoneExplainerState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        dragHandle = null,
    ) { state, contentPadding ->
        val hazeState = rememberZashiFrostState()
        var headerHeight by remember { mutableStateOf(0.dp) }
        Box(modifier = Modifier.weight(1f, false)) {
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
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
            ) {
                Text(
                    text = stringResource(R.string.hardwareWalletExplainer_description),
                    color = ZashiColors.Text.textTertiary,
                    style = ZashiTypography.textSm,
                )
                Spacer(24.dp)
                ExplainerRow(
                    icon = R.drawable.ic_link_03,
                    title = stringResource(R.string.hardwareWalletExplainer_featureTitle1),
                    message = stringResource(R.string.hardwareWalletExplainer_featureDescription1),
                )
                Spacer(16.dp)
                ExplainerRow(
                    icon = R.drawable.ic_shield_tick,
                    title = stringResource(R.string.hardwareWalletExplainer_featureTitle2),
                    message = stringResource(R.string.hardwareWalletExplainer_featureDescription2),
                )
                Spacer(16.dp)
                ExplainerRow(
                    icon = R.drawable.ic_cryptocurrency_04,
                    title = stringResource(R.string.hardwareWalletExplainer_featureTitle3),
                    message = stringResource(R.string.hardwareWalletExplainer_featureDescription3),
                )
                Spacer(24.dp)
                ZashiCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone),
                            contentDescription = null,
                        )
                        Spacer(8.dp)
                        Text(
                            text = stringResource(R.string.hardwareWalletExplainer_infoBoxTitle),
                            color = ZashiColors.Text.textPrimary,
                            style = ZashiTypography.textSm,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(8.dp)
                    Text(
                        text = stringResource(R.string.hardwareWalletExplainer_infoBoxDescription),
                        color = ZashiColors.Text.textTertiary,
                        style = ZashiTypography.textSm,
                    )
                }
                Spacer(32.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(R.string.hardwareWalletExplainer_cta),
                            onClick = state.onBack,
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
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        text = stringResource(R.string.hardwareWalletExplainer_title),
                        color = ZashiColors.Text.textPrimary,
                        style = ZashiTypography.textXl,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            )
        }
    }
}

@Composable
private fun ExplainerRow(
    icon: Int,
    title: String,
    message: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
            )
            Text(
                text = title,
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = message,
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        KeystoneExplainerView(
            state = KeystoneExplainerState(onBack = {}),
        )
    }
