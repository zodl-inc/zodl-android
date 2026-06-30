package co.electriccoin.zcash.ui.screen.migration

// TODO [MOB-IRONWOOD]: Replace placeholder strings with proper resources (strings.xml).
// TODO [MOB-IRONWOOD]: Replace placeholder UI with finalised designs from 3.7.1(4) equivalent.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.NoteSplitProposal
import co.electriccoin.zcash.ui.common.repository.MigrationUiState

@Composable
fun MigrationScreen(viewModel: MigrationViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onResume()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val state = uiState.migrationState) {
            MigrationUiState.Loading -> {
                CircularProgressIndicator()
            }

            MigrationUiState.NotStarted -> {
                Unit
            }

            // no-op; banner in HomeScreen drives entry

            is MigrationUiState.AwaitingNoteSplitConfirm -> {
                NoteSplitConfirmContent(
                    proposal = state.proposal,
                    torEnabled = uiState.torEnabled,
                    onTorToggled = viewModel::onTorToggled,
                    onConfirm = { viewModel.onNoteSplitConfirm(state.proposal) },
                )
            }

            MigrationUiState.AwaitingSplitConfirmation -> {
                WaitingContent(
                    title = "Preparing your funds",
                    detail = "Splitting notes for privacy — waiting for on-chain confirmation.",
                )
            }

            is MigrationUiState.ProposalReview -> {
                ScheduleReviewContent(
                    schedule = state.schedule,
                    torEnabled = uiState.torEnabled,
                    onTorToggled = viewModel::onTorToggled,
                    onConfirm = { viewModel.onScheduleConfirm(state.schedule) },
                )
            }

            is MigrationUiState.InProgress -> {
                ProgressContent(
                    progress = state.progress,
                    torEnabled = uiState.torEnabled,
                    onTorToggled = viewModel::onTorToggled,
                    onSendNext = viewModel::onExecuteNextTransfer,
                )
            }

            is MigrationUiState.RequiresAttention -> {
                AttentionContent(
                    reason = state.reason,
                    onRetry = viewModel::onRestart,
                )
            }

            MigrationUiState.Complete -> {
                CompleteContent()
            }

            is MigrationUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = viewModel::onRestart,
                )
            }
        }
    }
}

@Composable
private fun NoteSplitConfirmContent(
    proposal: NoteSplitProposal,
    torEnabled: Boolean,
    onTorToggled: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Prepare your Orchard funds", textAlign = TextAlign.Center)
        Text(
            "Before migrating, your funds need to be split into smaller amounts for privacy. This requires one on-chain transaction.",
            textAlign = TextAlign.Center,
        )
        TorToggleRow(enabled = torEnabled, onToggled = onTorToggled)
        Button(onClick = onConfirm) { Text("Prepare funds") }
    }
}

@Composable
private fun ScheduleReviewContent(
    schedule: MigrationSchedule,
    torEnabled: Boolean,
    onTorToggled: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Review migration plan", textAlign = TextAlign.Center)
        Text(
            "Your funds will be moved in ${schedule.transfers.size} transfers over approximately " +
                "${schedule.estimatedDurationHours} hours. Open ZODL each day to continue.",
            textAlign = TextAlign.Center,
        )
        TorToggleRow(enabled = torEnabled, onToggled = onTorToggled)
        Button(onClick = onConfirm) { Text("Start migration") }
    }
}

@Composable
private fun ProgressContent(
    progress: MigrationProgress,
    torEnabled: Boolean,
    onTorToggled: (Boolean) -> Unit,
    onSendNext: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Migration in progress", textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { progress.completedTransfers.toFloat() / progress.totalTransfers.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${progress.completedTransfers} of ${progress.totalTransfers} transfers complete")
        if (progress.nextTransferReadyAtHeight != null) {
            Text("Next transfer available around block ${progress.nextTransferReadyAtHeight}")
        } else {
            TorToggleRow(enabled = torEnabled, onToggled = onTorToggled)
            Button(onClick = onSendNext) { Text("Send next transfer") }
        }
    }
}

@Composable
private fun WaitingContent(title: String, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(title)
        Text(detail, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AttentionContent(reason: AttentionReason, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            when (reason) {
                is AttentionReason.InvalidTransfer -> "Transfer already sent"
                AttentionReason.TransferExpired -> "Transfer expired"
                AttentionReason.SyncRequiredBeforeNext -> "Sync required"
            },
            textAlign = TextAlign.Center,
        )
        Text(
            when (reason) {
                is AttentionReason.InvalidTransfer -> {
                    "This transfer was already sent, possibly from another device. " +
                        "Open ZODL on your primary device to continue."
                }

                AttentionReason.TransferExpired -> {
                    "A scheduled transfer expired before it could be sent. ZODL will prepare a new one."
                }

                AttentionReason.SyncRequiredBeforeNext -> {
                    "ZODL needs to sync with the network before sending the next transfer. " +
                        "Please stay connected and try again."
                }
            },
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun CompleteContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Migration complete", textAlign = TextAlign.Center)
        Text("All your funds have been moved to the Ironwood pool.", textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Migration error", textAlign = TextAlign.Center)
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun TorToggleRow(enabled: Boolean, onToggled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Use Tor")
            Text("Recommended — hides your IP during migration")
        }
        Switch(checked = enabled, onCheckedChange = onToggled)
    }
}
