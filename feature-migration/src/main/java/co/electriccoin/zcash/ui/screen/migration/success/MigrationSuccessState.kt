package co.electriccoin.zcash.ui.screen.migration.success

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import co.electriccoin.zcash.ui.design.R as DesignR

data class MigrationSuccessState(
    val onViewTransaction: (() -> Unit)?,
    val onClose: () -> Unit,
)

@Composable
fun MigrationSuccessScreen(args: MigrationSuccessArgs) {
    val vm = koinViewModel<MigrationSuccessVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(
        state = state,
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { MigrationSuccessView(it) }
}

// Figma node 2618:6895 ("Success") — same green-gradient + fist-punch celebratory treatment
// MigrationScheduledScreen.kt already established for its own success moment within this feature.
// A plan's *last* transfer sending never lands here — MigrationSendingVM routes that case to
// MigrationCompleteScreen instead, so foreground and background completion share one screen.
@Composable
fun MigrationSuccessView(state: MigrationSuccessState) {
    GradientBgScaffold(
        startColor = ZashiColors.Utility.SuccessGreen.utilitySuccess100,
        endColor = ZashiColors.Surfaces.bgPrimary,
    ) { padding ->
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
                painter = painterResource(R.drawable.ic_fist_punch),
                contentDescription = null,
                modifier = Modifier.size(148.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringRes(DesignR.string.migrationSuccess_title).getValue(),
                style = ZashiTypography.header5,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(DesignR.string.migrationSuccess_subtitle).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            state.onViewTransaction?.let { onView ->
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes(DesignR.string.migrationSuccess_viewTransaction),
                            onClick = onView
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
                )
                Spacer(Modifier.height(8.dp))
            }
            ZashiButton(
                state = ButtonState(text = stringRes(DesignR.string.general_close), onClick = state.onClose),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        MigrationSuccessView(
            state =
                MigrationSuccessState(
                    onViewTransaction = {},
                    onClose = {},
                )
        )
    }
