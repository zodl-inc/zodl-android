package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
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
 * **Height source (fixed after review):** an earlier version of this use case resolved the PIR
 * server against the wallet's live `fullyScannedHeight`, which is a continuously-changing scan
 * tip. [co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver.resolve] requires an EXACT
 * height match against what a configured PIR server currently reports (`NoMatchingEndpoint`
 * otherwise) -- every other caller in this codebase
 * ([PrepareVotingRoundUseCase], [SubmitVotesUseCase], [PrecomputeVotingSnapshotBundlesUseCase])
 * resolves against a specific ROUND's fixed `snapshotHeight`, never a live/rolling height, which
 * is strong evidence PIR servers serve fixed round-snapshot roots rather than a rolling tip. Using
 * the live scan tip would make `resolve()` throw on effectively every call, silently swallowed by
 * the `runCatching` below -- permanently inert code with no visible symptom. Fixed: warm the cache
 * for every currently ACTIVE round's own `snapshotHeight` instead, mirroring
 * [PrecomputeVotingSnapshotBundlesUseCase]'s already-correct pattern.
 *
 * Deliberately still callable with no `roundId` argument (unlike
 * [PrecomputeVotingSnapshotBundlesUseCase]): the caller (poll list, proposal detail) doesn't pick
 * a specific round -- this resolves every active round's snapshot height itself, from whatever
 * [VotingApiRepository] has already loaded, and no-ops gracefully if no active round is known yet
 * at the moment this fires (a real "nothing to warm yet" state, not a failure).
 *
 * Never throws: every failure (no cached config yet, no active rounds yet, no notes, PIR proof
 * fetch failure, ...) is caught and logged -- this is a pure optimization and must never fail or
 * delay the screen that triggered it. The actual PIR round-trip work is fire-and-forget and
 * deduped inside [VotingProofPrecomputeRepository.startPirWarmup]; this use case's own job is
 * just resolving that call's parameters, so failures here are just as harmless to swallow.
 */
class WarmVotingPirProofsUseCase(
    private val votingApiRepository: VotingApiRepository,
    private val votingConfigRepository: VotingConfigRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository
) {
    // SwallowedException: TorUnavailableException means the user has Tor turned off -- an
    // expected configuration, not an error worth propagating or logging. Matches
    // VotingKeystoneRepositoryImpl.createPcztEncoder's identical suppression for the identical
    // pattern. The failure mode that MUST propagate (TorInitializationErrorException) is
    // deliberately not caught here.
    @Suppress("SwallowedException")
    suspend operator fun invoke() {
        runCatching {
            withContext(Dispatchers.IO) {
                val activeSnapshotHeights =
                    votingApiRepository.snapshot.value.rounds
                        .filter { round -> round.status == SessionStatus.ACTIVE }
                        .map { round -> round.snapshotHeight }
                        .distinct()
                if (activeSnapshotHeights.isEmpty()) return@withContext

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
                val synchronizer = synchronizerProvider.getSynchronizer()
                val networkId = synchronizer.network.toVotingNetworkId()
                // Same Tor-optional fallback as SubmitVotesUseCase.kt -- Tor is a preference, not
                // a hard requirement. Only TorUnavailableException (Tor disabled) falls back to
                // 0L; TorInitializationErrorException (Tor is ON but failed to bootstrap) must
                // propagate, same as every other caller of this handle.
                val torRuntime =
                    try {
                        synchronizer.getVotingTorRuntimeHandle()
                    } catch (e: TorUnavailableException) {
                        0L
                    }

                // Important #3 (final whole-plan review): each round's own iteration body gets
                // its own runCatching, rather than sharing the outer one. Fetching wallet notes
                // for a round the wallet hasn't fully scanned to yet is a normal state (a newer
                // active round the wallet hasn't caught up to) and throws -- with a single shared
                // try/catch around the whole loop, that would abort warmup for every OTHER
                // active round too, recurring on every screen visit for as long as the newer
                // round stays unscanned, silently starving an older, fully-scanned, genuinely
                // votable round of its warmup.
                activeSnapshotHeights.forEach { snapshotHeight ->
                    runCatching {
                        val notesJson =
                            votingCryptoClient.getWalletNotesJson(
                                walletDbPath = walletDbPath,
                                snapshotHeight = snapshotHeight,
                                networkId = networkId,
                                accountUuidBytes = accountUuid.value
                            )
                        if (JSONArray(notesJson).length() == 0) return@runCatching

                        votingProofPrecomputeRepository.startPirWarmup(
                            VotingPirWarmupRequest(
                                accountUuid = accountUuidString,
                                walletId = accountUuidString,
                                votingDbPath = votingDbPath,
                                snapshotHeight = snapshotHeight,
                                pirEndpoints = pirEndpoints,
                                pirLayout = serviceConfig.pirLayout,
                                networkId = networkId,
                                notesJson = notesJson,
                                torRuntime = torRuntime
                            )
                        )
                    }.onFailure { throwable ->
                        Log.w(TAG, "PIR proof warmup failed for snapshot height $snapshotHeight", throwable)
                    }
                }
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
