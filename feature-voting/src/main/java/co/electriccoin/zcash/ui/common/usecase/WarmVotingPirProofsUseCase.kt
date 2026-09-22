package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingPirWarmupRequest
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * voting-5.0.0 background-precompute port (Task 4): triggers Task 1's bundle- and
 * round-independent PIR proof cache warm-up (`VotingCryptoClient.precomputePirProofs`) as a
 * best-effort background step, mirroring Vizor's poll-list/proposal-detail screen-entry warm-up
 * (`voting_polls_screen.dart:75`, `voting_proposal_detail_screen.dart:93`).
 *
 * Deliberately round-independent: unlike [PrecomputeVotingSnapshotBundlesUseCase], this needs no
 * `roundId` -- it warms the cache for the wallet's currently spendable notes at the current fully
 * scanned height, which is exactly what the SDK call itself does not require a round selection
 * for. Safe to call from a screen where no round has been chosen yet (poll list).
 *
 * Never throws: every failure (no cached config yet, wallet still syncing, no notes, PIR proof
 * fetch failure, ...) is caught and logged -- this is a pure optimization and must never fail or
 * delay the screen that triggered it. The actual PIR round-trip work is fire-and-forget and
 * deduped inside [VotingProofPrecomputeRepository.startPirWarmup]; this use case's own job is
 * just resolving that call's parameters, so failures here are just as harmless to swallow.
 */
class WarmVotingPirProofsUseCase(
    private val votingConfigRepository: VotingConfigRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository
) {
    suspend operator fun invoke() {
        runCatching {
            withContext(Dispatchers.IO) {
                val serviceConfig = votingConfigRepository.get()?.serviceConfig ?: return@withContext
                val pirEndpoints = serviceConfig.pirEndpoints.map { endpoint -> endpoint.url }
                if (pirEndpoints.isEmpty()) return@withContext

                val synchronizer = synchronizerProvider.getSynchronizer()
                val snapshotHeight = synchronizer.fullyScannedHeight.value?.value?.takeIf { it > 0 } ?: return@withContext

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
                val networkId = synchronizer.network.toVotingNetworkId()

                val notesJson =
                    votingCryptoClient.getWalletNotesJson(
                        walletDbPath = walletDbPath,
                        snapshotHeight = snapshotHeight,
                        networkId = networkId,
                        accountUuidBytes = accountUuid.value
                    )
                if (JSONArray(notesJson).length() == 0) return@withContext

                votingProofPrecomputeRepository.startPirWarmup(
                    VotingPirWarmupRequest(
                        accountUuid = accountUuidString,
                        walletId = accountUuidString,
                        votingDbPath = votingDbPath,
                        snapshotHeight = snapshotHeight,
                        pirEndpoints = pirEndpoints,
                        pirLayout = serviceConfig.pirLayout,
                        networkId = networkId,
                        notesJson = notesJson
                    )
                )
            }
        }.onFailure { throwable ->
            Log.w(TAG, "PIR proof warmup request could not be built", throwable)
        }
    }

    private companion object {
        const val TAG = "WarmVotingPirProofs"
    }
}

private fun ZcashNetwork.toVotingNetworkId() = if (isMainnet()) 1 else 0
