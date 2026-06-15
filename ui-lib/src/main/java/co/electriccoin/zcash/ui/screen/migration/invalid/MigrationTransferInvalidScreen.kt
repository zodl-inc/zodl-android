package co.electriccoin.zcash.ui.screen.migration.invalid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
data object MigrationTransferInvalidArgs

@Composable
fun MigrationTransferInvalidScreen() {
    val vm = koinViewModel<MigrationTransferInvalidVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        MigrationTransferInvalidView(s)
    }
}

@Composable
fun MigrationTransferInvalidView(state: MigrationTransferInvalidState) {
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
            Text(
                text = "Transfer No Longer Valid",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Transfers ${state.invalidRange.getValue()} were pre-signed for a balance that has since changed. The migration plan needs to be re-created for the remaining amount. No funds are lost — only the pre-signed transactions are discarded.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "What Happens Next",
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            WhatHappensNextItem(
                number = 1,
                text = "Invalid transfers are discarded",
            )
            WhatHappensNextItem(
                number = 2,
                text = "Remaining balance is re-proposed",
            )
            WhatHappensNextItem(
                number = 3,
                text = "${state.completedCount} of ${state.totalCount} transfers done; migration will continue.",
            )
            Spacer(Modifier.height(48.dp))
            ZashiButton(
                state = ButtonState(text = stringRes("Continue"), onClick = state.onContinue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WhatHappensNextItem(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(ZashiColors.Surfaces.bgSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = ZashiTypography.textXs,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() = ZcashTheme {
    MigrationTransferInvalidView(
        state = MigrationTransferInvalidState(
            completedCount = 2,
            totalCount = 5,
            remainingCount = 3,
            invalidRange = stringRes("3–5"),
            onContinue = {},
            onBack = {},
        )
    )
}
