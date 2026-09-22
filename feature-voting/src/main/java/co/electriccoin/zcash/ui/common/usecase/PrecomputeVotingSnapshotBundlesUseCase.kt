package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingSnapshotBundlePrecomputeRequest
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * voting-5.0.0 background-precompute port (Task 4): triggers Task 2's whole-round background
 * precompute (`VotingCryptoClient.precomputeSnapshotBundles`) -- persists the round's canonical
 * bundle plan and warms PIR for every bundle in it, ahead of vote submission -- mirroring Vizor's
 * proposal-detail/review screen-entry precompute (`voting_proposal_detail_screen.dart:444-473`,
 * `voting_review_screen.dart:137-169`, both gated on "eligibility confirmed").
 *
 * Unlike [WarmVotingPirProofsUseCase], this is round-scoped: [precomputeSnapshotBundles]
 * persists a bundle plan tied to [roundId]'s snapshot, so it can only run once a specific round
 * is selected (proposal detail / review) -- never from a round-agnostic screen like the poll
 * list. A verified strict superset of the old per-bundle delegation-PIR precompute this use case
 * replaces as the app's only snapshot-bundle precompute entry point.
 *
 * Never throws: every failure is caught and logged -- this is a pure optimization and must never
 * fail or delay the screen that triggered it. The actual precompute work is fire-and-forget and
 * deduped per round inside [VotingProofPrecomputeRepository.startSnapshotBundlePrecompute]; this
 * use case's own job is just resolving that call's parameters.
 */
class PrecomputeVotingSnapshotBundlesUseCase(
    private val votingApiRepository: VotingApiRepository,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository
) {
    suspend operator fun invoke(roundId: String) {
        if (roundId.isEmpty()) return

        runCatching {
            withContext(Dispatchers.IO) {
                val round =
                    votingApiRepository.snapshot.value.rounds
                        .firstOrNull { candidate -> candidate.id == roundId }
                        ?: return@withContext
                val serviceConfig = votingConfigRepository.get()?.serviceConfig ?: return@withContext
                val pirEndpoints = serviceConfig.pirEndpoints.map { endpoint -> endpoint.url }
                if (pirEndpoints.isEmpty()) return@withContext

                val selectedAccount = getSelectedWalletAccount()
                val accountUuid = selectedAccount.sdkAccount.accountUuid
                val accountUuidString = accountUuid.toVotingAccountScopeId()
                val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
                val votingDbPath =
                    File(walletDbPath)
                        .parentFile
                        ?.resolve("voting.sqlite3")
                        ?.absolutePath
                        ?: return@withContext
                val networkId = synchronizerProvider.getSynchronizer().network.toVotingNetworkId()

                val notesJson =
                    votingCryptoClient.getWalletNotesJson(
                        walletDbPath = walletDbPath,
                        snapshotHeight = round.snapshotHeight,
                        networkId = networkId,
                        accountUuidBytes = accountUuid.value
                    )
                if (JSONArray(notesJson).length() == 0) return@withContext

                votingProofPrecomputeRepository.startSnapshotBundlePrecompute(
                    VotingSnapshotBundlePrecomputeRequest(
                        accountUuid = accountUuidString,
                        walletId = accountUuidString,
                        votingDbPath = votingDbPath,
                        roundId = roundId,
                        pirEndpoints = pirEndpoints,
                        pirLayout = serviceConfig.pirLayout,
                        expectedSnapshotHeight = round.snapshotHeight,
                        networkId = networkId,
                        notesJson = notesJson
                    )
                )
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Snapshot bundle precompute request could not be built for round $roundId", throwable)
        }
    }

    private companion object {
        const val TAG = "PrecomputeVotingSnapshotBundles"
    }
}

private fun ZcashNetwork.toVotingNetworkId() = if (isMainnet()) 1 else 0
