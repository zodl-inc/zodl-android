package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.AttentionReason
import cash.z.ecc.android.sdk.MigrationProgress
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.NoteSplitProposal
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Re-export SDK sealed types so the rest of the app imports from one place.
typealias SdkMigrationState = MigrationState
typealias SdkMigrationProgress = MigrationProgress
typealias SdkMigrationSchedule = MigrationSchedule
typealias SdkNoteSplitProposal = NoteSplitProposal
typealias SdkTransferResult = TransferResult
typealias SdkAttentionReason = AttentionReason
typealias SdkNetworkPrivacyOptions = NetworkPrivacyOptions

interface MigrationDataSource {
    suspend fun getMigrationState(): MigrationState

    suspend fun getMigrationProgress(): MigrationProgress?

    suspend fun isSyncRequiredBeforeNextTransfer(): Boolean

    suspend fun hasOverdueTransfers(): Boolean

    suspend fun hasInvalidTransfers(): Boolean

    suspend fun isNoteSplitNeeded(): Boolean

    suspend fun prepareNoteSplit(): NoteSplitProposal

    suspend fun submitNoteSplit(proposal: NoteSplitProposal): TransferResult

    suspend fun proposeMigrationTransfers(): MigrationSchedule

    suspend fun signAndStoreMigrationSchedule(schedule: MigrationSchedule)

    suspend fun executeNextPendingTransfer(options: NetworkPrivacyOptions): TransferResult?

    suspend fun restartCurrentMigrationStep(): MigrationSchedule

    fun initializePostUpgrade()
}

class MigrationDataSourceImpl(
    private val synchronizerProvider: SynchronizerProvider,
) : MigrationDataSource {
    private suspend fun sdk(): OrchardMigrationSdk =
        // TODO [MOB-IRONWOOD]: expose orchardMigration accessor on Synchronizer once the SDK
        // property is wired in SdkSynchronizer. Replace with: synchronizerProvider.synchronizer.orchardMigration
        error("OrchardMigrationSdk not yet accessible from Synchronizer — wire after SDK integration")

    override suspend fun getMigrationState(): MigrationState =
        withContext(Dispatchers.IO) { sdk().getMigrationState() }

    override suspend fun getMigrationProgress(): MigrationProgress? =
        withContext(Dispatchers.IO) { sdk().getMigrationProgress() }

    override suspend fun isSyncRequiredBeforeNextTransfer(): Boolean =
        withContext(Dispatchers.IO) { sdk().isSyncRequiredBeforeNextTransfer() }

    override suspend fun hasOverdueTransfers(): Boolean =
        withContext(Dispatchers.IO) { sdk().hasOverdueTransfers() }

    override suspend fun hasInvalidTransfers(): Boolean =
        withContext(Dispatchers.IO) { sdk().hasInvalidTransfers() }

    override suspend fun isNoteSplitNeeded(): Boolean =
        withContext(Dispatchers.IO) { sdk().isNoteSplitNeeded() }

    override suspend fun prepareNoteSplit(): NoteSplitProposal =
        withContext(Dispatchers.IO) { sdk().prepareNoteSplit() }

    override suspend fun submitNoteSplit(proposal: NoteSplitProposal): TransferResult =
        withContext(Dispatchers.IO) { sdk().submitNoteSplit(proposal) }

    override suspend fun proposeMigrationTransfers(): MigrationSchedule =
        withContext(Dispatchers.IO) { sdk().proposeMigrationTransfers() }

    override suspend fun signAndStoreMigrationSchedule(schedule: MigrationSchedule) =
        withContext(Dispatchers.IO) {
            // SDK signs internally — no spending key needed here.
            // SECURITY: the SDK is responsible for key management during signing.
            sdk().signAndStoreMigrationSchedule(schedule)
        }

    override suspend fun executeNextPendingTransfer(options: NetworkPrivacyOptions): TransferResult? =
        withContext(Dispatchers.IO) {
            // SECURITY: isSyncRequiredBeforeNextTransfer() is checked in MigrationRepository
            // before this call. Do not call here to avoid double-checking in wrong order.
            sdk().executeNextPendingTransfer(options)
        }

    override suspend fun restartCurrentMigrationStep(): MigrationSchedule =
        withContext(Dispatchers.IO) { sdk().restartCurrentMigrationStep() }

    override fun initializePostUpgrade() {
        // TODO [MOB-IRONWOOD]: call after Ironwood network upgrade detection in app lifecycle.
        // Obtain sdk() synchronously or wire via a lifecycle-aware observer.
    }
}
