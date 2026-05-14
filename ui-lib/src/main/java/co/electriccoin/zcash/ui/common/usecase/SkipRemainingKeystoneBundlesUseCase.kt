package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SkipRemainingKeystoneBundlesUseCase(
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val synchronizerProvider: SynchronizerProvider,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingRecoveryRepository: VotingRecoveryRepository
) {
    suspend operator fun invoke(
        accountUuid: String,
        roundId: String
    ): SkippedKeystoneBundles =
        withContext(Dispatchers.IO) {
            val selectedAccount = getSelectedWalletAccount()
            require(selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId() == accountUuid) {
                "Selected account changed during the Keystone voting signature flow"
            }

            val recovery =
                requireNotNull(votingRecoveryRepository.get(accountUuid, roundId)) {
                    "Voting round $roundId has not been prepared"
                }
            val bundleCount = recovery.bundleCount ?: error("Voting round $roundId has no prepared bundle count")
            val keepCount =
                (0 until bundleCount)
                    .takeWhile { bundleIndex -> bundleIndex in recovery.keystoneBundleSignatures }
                    .count()
            require(keepCount > 0) {
                "At least one Keystone voting bundle must be signed before skipping"
            }
            require(keepCount < bundleCount) {
                "All Keystone voting bundles are already signed"
            }
            require(recovery.bundleWeights.size >= bundleCount) {
                "Voting round $roundId is missing per-bundle voting weights"
            }

            val signedWeight = recovery.bundleWeights.take(keepCount).sum()
            val skippedWeight = recovery.bundleWeights.subList(keepCount, bundleCount).sum()

            val votingDbPath =
                File(synchronizerProvider.getVotingWalletDbPath())
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from wallet DB")
            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, selectedAccount.sdkAccount.accountUuid.toString())
                // If the snapshot write below fails, retrying this DB delete is safe: deleting rows at or after
                // keepCount is idempotent once those rows are already gone. The reverse order would hide the retry.
                votingCryptoClient.deleteSkippedBundles(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    keepCount = keepCount
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }

            val updatedRecovery =
                votingRecoveryRepository.skipRemainingKeystoneBundles(
                    accountUuid = accountUuid,
                    roundId = roundId,
                    keepCount = keepCount
                )

            SkippedKeystoneBundles(
                signedBundleCount = keepCount,
                skippedBundleCount = bundleCount - keepCount,
                signedWeight = updatedRecovery.eligibleWeight ?: signedWeight,
                skippedWeight = skippedWeight
            )
        }
}

data class SkippedKeystoneBundles(
    val signedBundleCount: Int,
    val skippedBundleCount: Int,
    val signedWeight: Long,
    val skippedWeight: Long
)
