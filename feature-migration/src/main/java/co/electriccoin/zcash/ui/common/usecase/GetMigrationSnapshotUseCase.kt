package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationTransferStates
import co.electriccoin.zcash.ui.common.model.migration.LiveMigrationSnapshot
import co.electriccoin.zcash.ui.common.model.migration.toSnapshot
import kotlin.time.Clock

/**
 * The one way display consumers read the migration plan: a live [LiveMigrationSnapshot] derived
 * from the engine's persisted state (nothing app-persisted — see
 * `spec/2026-07-30-plan-cache-elimination-proposal.md`). Returns `null` when the account has no
 * committed migration.
 *
 * [invoke] itself does a fresh SDK read every call — correct for one-shot, correctness-critical
 * callers (e.g. `MigrationTransferInvalidVM`'s init) that need the freshest possible state. A
 * caller that already has a [MigrationTransferStates] in hand (e.g. from
 * `MigrationTransferStateRepository`'s cached readout) should call [migrationSnapshotFrom] directly
 * instead of round-tripping through another SDK call for the same derivation.
 */
class GetMigrationSnapshotUseCase(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
) {
    suspend operator fun invoke(accountKeyId: String? = null): LiveMigrationSnapshot? {
        val sdk =
            (if (accountKeyId != null) getOrchardMigrationSdk(accountKeyId) else getOrchardMigrationSdk())
                ?: return null
        val states = sdk.getMigrationTransferStates() ?: return null
        return migrationSnapshotFrom(
            states = states,
            estimatedTip = sdk.estimatedChainTip(),
            secondsPerBlock = sdk.estimatedSecondsPerBlock(),
            nu7ActivationHeight = sdk.nu7ActivationHeight,
        )
    }
}

/**
 * The pure derivation [GetMigrationSnapshotUseCase.invoke] itself delegates to — no SDK call, so a
 * caller already holding a fresh [MigrationTransferStates] + tip-estimate pair (e.g. a
 * `MigrationLiveReadout`) can derive the same [LiveMigrationSnapshot] without a second, independent
 * SDK read. One derivation, two entry points — kept in sync by construction rather than by
 * convention.
 */
fun migrationSnapshotFrom(
    states: MigrationTransferStates,
    estimatedTip: Long,
    secondsPerBlock: Long,
    nu7ActivationHeight: Long?,
): LiveMigrationSnapshot =
    states.toSnapshot(
        estimatedTip = estimatedTip,
        secondsPerBlock = secondsPerBlock,
        nu7ActivationHeight = nu7ActivationHeight,
        nowEpochSeconds = Clock.System.now().epochSeconds,
    )
