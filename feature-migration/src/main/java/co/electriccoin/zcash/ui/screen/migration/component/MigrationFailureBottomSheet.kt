package co.electriccoin.zcash.ui.screen.migration.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiModalBottomSheet
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.R as DesignR

// Shared across migration screens (Progress, Sending, NoteSplit) so Retry/Dismiss failure
// handling stays visually and behaviorally consistent everywhere a broadcast can fail.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationFailureBottomSheet(state: MigrationTransferFailureState?) {
    if (state == null) return
    ZashiModalBottomSheet(onDismissRequest = state.onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringRes(DesignR.string.migrationFailureSheet_title).getValue(),
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(28.dp))
            val onRetry = state.onRetry
            if (onRetry != null) {
                ZashiButton(
                    state = ButtonState(text = stringRes(DesignR.string.migration_common_retry), onClick = onRetry),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            ZashiButton(
                state =
                    ButtonState(
                        text = stringRes(DesignR.string.migration_common_dismiss),
                        onClick = state.onDismiss
                    ),
                modifier = Modifier.fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
            )
        }
    }
}
