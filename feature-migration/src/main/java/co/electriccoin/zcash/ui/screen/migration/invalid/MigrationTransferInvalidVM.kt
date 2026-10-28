package co.electriccoin.zcash.ui.screen.migration.invalid

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationRestartOutcome
import cash.z.ecc.android.sdk.MigrationState
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.MigrationAttentionKind
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.affectedTransferIndices
import co.electriccoin.zcash.ui.common.model.migration.toMigrationRangeText
import co.electriccoin.zcash.ui.common.model.migration.toUiKind
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationSnapshotUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class MigrationTransferInvalidVM(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getMigrationSnapshot: GetMigrationSnapshotUseCase,
    private val restartMigrationScheduleRepository: RestartMigrationScheduleRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val loadLce = mutableLce<Unit>()
    private val restartLce = mutableLce<Unit>()

    // Populated once at load from the SDK's actual MigrationState.RequiresAttention.reason — the
    // real distinction between spec §6.2 (InvalidTransfer) and §6.3 (TransferExpired), replacing
    // the old hasInvalidTransfers()-only boolean that could never tell the two apart. The live
    // snapshot is read alongside it (same load pass) so affectedTransferIndices() correlates by
    // the transfer's stable engine id.
    private data class RecoveryInfo(
        val reason: AttentionReason?,
        val snapshot: LiveMigrationSnapshot?
    )

    private val recoveryInfo = MutableStateFlow(RecoveryInfo(null, null))

    init {
        loadLce.execute {
            val sdk = getOrchardMigrationSdk()
            // getMigrationStateUnreconciled(), not getMigrationState(): this screen only reads,
            // never mutates (2026-08-07 read/write-separation design), and RequiresAttention's
            // reason doesn't depend on the mark-mined promotion pass — this screen is only
            // reachable after the state was already observed as RequiresAttention elsewhere.
            val reason = (sdk.getMigrationStateUnreconciled() as? MigrationState.RequiresAttention)?.reason
            recoveryInfo.value = RecoveryInfo(reason, getMigrationSnapshot())
        }
    }

    val state: StateFlow<LceState<MigrationTransferInvalidState>> =
        recoveryInfo
            .map { info -> buildState(info) }
            .withLce(groupLce(loadLce, restartLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun buildState(info: RecoveryInfo): MigrationTransferInvalidState {
        val snapshot = info.snapshot
        val completed = snapshot?.completedCount ?: 0
        val total = snapshot?.totalCount ?: 0
        val reason = info.reason
        val kind = reason?.toUiKind() ?: MigrationAttentionKind.TRANSFER_EXPIRED
        val affectedIndices =
            if (snapshot != null && reason != null) {
                reason.affectedTransferIndices(snapshot, Clock.System.now())
            } else {
                emptyList()
            }
        // Falls back to the old "everything after the last completed transfer" guess only when the
        // real affected set couldn't be determined (reason not yet loaded) — never silently blank.
        val rangeText =
            affectedIndices.toMigrationRangeText() ?: run {
                val firstInvalid = completed + 1
                if (total > firstInvalid) "$firstInvalid–$total" else "$firstInvalid"
            }
        return MigrationTransferInvalidState(
            kind = kind,
            completedCount = completed,
            totalCount = total,
            remainingCount = affectedIndices.size.takeIf { it > 0 } ?: (total - completed),
            invalidRange = stringRes(rangeText),
            onContinue = ::onContinue,
            onBack = ::onBack,
        )
    }

    private fun onContinue() =
        restartLce.execute {
            val sdk = getOrchardMigrationSdk()
            // restartCurrentMigrationStep()'s own doc requires its returned schedule to go through the
            // normal user confirmation flow rather than being discarded — hand it to MigrationReviewVM
            // instead of letting it independently re-propose (see RestartMigrationScheduleRepository's
            // doc for why this is a separate slot from the Keystone sign/scan hand-off).
            when (val outcome = sdk.restartCurrentMigrationStep()) {
                is MigrationRestartOutcome.Replanned -> {
                    val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
                    restartMigrationScheduleRepository.set(accountKeyId, outcome.schedule)
                    navigationRouter.replace(MigrationReviewArgs(MigrationMode.AUTOMATIC))
                }

                is MigrationRestartOutcome.CancelledUntilNetworkUpgrade -> {
                    navigationRouter.replace(MigrationSetupArgs)
                }
            }
        }

    private fun onBack() = navigationRouter.back()
}
