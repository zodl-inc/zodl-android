package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.voting.VoteIneligibilityReason
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingEligibility
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.File
import java.security.SecureRandom

/**
 * voting-5.0.0 round-driver port note: this use case is substantially simplified from its pre-4.0
 * form for this benchmark pass (Task 8's scope-cut default — "recovery = call run() again", no
 * persisted `VotingRecoveryPhase` state machine). Witness generation
 * (`generateNoteWitnessesJson`/`storeWitnesses`) and the software-wallet PIR/delegation-proof
 * background precompute optimization were both confirmed to have no direct equivalent needed here
 * — witness generation is now fully internal to the crate's `run()` call (Task 3 finding), and the
 * precompute optimization has no straightforward new-architecture equivalent in this pass's scope
 * and was dropped rather than half-ported. See the port plan's Task 8/9 notes before treating this
 * as a full replacement for the pre-4.0 implementation.
 */
class PrepareVotingRoundUseCase(
    private val resolveVotingRoundSession: ResolveVotingRoundSessionUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingHotkeySeedProvider: VotingHotkeySeedProvider,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getWalletSeedBytes: GetWalletSeedBytesUseCase
) {
    private val secureRandom = SecureRandom()

    suspend operator fun invoke(roundId: String): VotingRoundPreparationResult =
        withContext(Dispatchers.IO) {
            votingProofPrecomputeRepository.warmProvingCaches()
            val sessionContext = resolveVotingRoundSession(roundId)
            val session = sessionContext.session
            val sessionRoundId = session.voteRoundId.toHex()

            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val recoveryAccountUuid =
                getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
            votingRecoveryRepository.storeVoteEndEpochSeconds(
                accountUuid = recoveryAccountUuid,
                roundId = roundId,
                voteEndEpochSeconds = session.voteEndTime.epochSecond
            )
            // Without this, `recovery.voteServerUrls` is always empty and TrackVotingSharesUseCase
            // falls back to re-fetching the service config on every share-tracking pass -- the
            // ~15s timeout/self-cancel/reschedule loop seen under poor connectivity. Same
            // derivation SubmitVotesUseCase uses to build its own VotingDelegationInputs
            // chainEndpoints, so the persisted list matches what submission actually drove.
            votingRecoveryRepository.storeVoteServerUrls(
                accountUuid = recoveryAccountUuid,
                roundId = roundId,
                voteServerUrls =
                    sessionContext.serviceConfig.voteServers
                        .map { endpoint -> endpoint.url.trimEnd('/') }
                        .distinct()
            )

            val synchronizer = synchronizerProvider.getSynchronizer()
            val scannedHeight = awaitFullyScannedHeight(synchronizer)
            if (scannedHeight == null || scannedHeight < session.snapshotHeight) {
                Log.i(
                    TAG,
                    "WalletSyncing gate tripped for round $roundId: scannedHeight=$scannedHeight " +
                        "snapshotHeight=${session.snapshotHeight} network=${synchronizer.network.networkName}"
                )
                votingSessionStore.setEligibility(VotingEligibility.WALLET_SYNCING)
                return@withContext VotingRoundPreparationResult.WalletSyncing(
                    scannedHeight = scannedHeight,
                    snapshotHeight = session.snapshotHeight
                )
            }

            val selectedAccount = getSelectedWalletAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid
            val accountUuidString = accountUuid.toVotingAccountScopeId()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val votingDbPath =
                File(walletDbPath)
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            val preparationResult =
                try {
                    votingCryptoClient.setWalletId(dbHandle, accountUuidString, networkId)
                    val existingRoundState = votingCryptoClient.getRoundState(dbHandle, roundId)

                    val (preparedBundleCount, eligibleWeight) =
                        if (existingRoundState == null) {
                            val notesJson =
                                votingCryptoClient.getWalletNotesJson(
                                    walletDbPath = walletDbPath,
                                    snapshotHeight = session.snapshotHeight,
                                    networkId = networkId,
                                    accountUuidBytes = accountUuid.value
                                )
                            if (JSONArray(notesJson).length() == 0) {
                                votingSessionStore.setEligibility(VotingEligibility.INELIGIBLE)
                                return@withContext VotingRoundPreparationResult.Ineligible(
                                    reason = VoteIneligibilityReason.NO_NOTES,
                                    eligibleWeight = 0L,
                                    bundleCount = 0
                                )
                            }

                            val treeStateBytes = synchronizer.getTreeState(BlockHeight.new(session.snapshotHeight))
                            votingCryptoClient.ensureRound(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                anchorTreeStateBytes = treeStateBytes,
                                snapshotHeight = session.snapshotHeight,
                                eaPk = session.eaPK,
                                ncRoot = session.ncRoot,
                                nullifierImtRoot = session.nullifierIMTRoot
                            )

                            votingCryptoClient
                                .setupBundles(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    notesJson = notesJson
                                ).also { setup ->
                                    votingRecoveryRepository.storeBundleSetup(
                                        accountUuid = accountUuidString,
                                        roundId = roundId,
                                        bundleCount = setup.bundleCount,
                                        eligibleWeight = setup.eligibleWeight,
                                        bundleWeights = setup.bundleWeights
                                    )
                                }.let { setup -> setup.bundleCount to setup.eligibleWeight }
                        } else {
                            recoverExistingBundleSetup(
                                accountUuid = accountUuidString,
                                roundId = roundId,
                                dbHandle = dbHandle,
                                walletDbPath = walletDbPath,
                                snapshotHeight = session.snapshotHeight,
                                networkId = networkId,
                                accountUuidBytes = accountUuid.value
                            ).let { setup -> setup.bundleCount to setup.eligibleWeight }
                        }

                    if (eligibleWeight <= 0L) {
                        votingSessionStore.setEligibility(VotingEligibility.INELIGIBLE)
                        return@withContext VotingRoundPreparationResult.Ineligible(
                            reason = VoteIneligibilityReason.BALANCE_TOO_LOW,
                            eligibleWeight = eligibleWeight,
                            bundleCount = preparedBundleCount
                        )
                    }

                    val hotkeyBound = existingRoundState?.hotkeyAddress != null
                    val hotkeySeed =
                        getOrCreateHotkeySeed(
                            accountUuid = accountUuidString,
                            roundId = roundId,
                            hotkeyBound = hotkeyBound
                        )
                    val hotkeyAddress =
                        if (!hotkeyBound) {
                            val hotkey =
                                votingCryptoClient.generateHotkey(
                                    dbHandle = dbHandle,
                                    storedSecret = hotkeySeed
                                )
                            votingRecoveryRepository.storeHotkey(
                                accountUuid = accountUuidString,
                                roundId = roundId,
                                hotkeyAddress = hotkey.address
                            )
                            hotkey.address
                        } else {
                            checkNotNull(existingRoundState.hotkeyAddress) {
                                "Missing hotkey address for resumed voting round $roundId"
                            }
                        }
                    votingSessionStore.setEligibility(VotingEligibility.ELIGIBLE)

                    VotingRoundPreparationResult.Ready(
                        roundId = roundId,
                        bundleCount = preparedBundleCount,
                        eligibleWeight = eligibleWeight,
                        hotkeyAddress = hotkeyAddress
                    )
                } finally {
                    withContext(NonCancellable) {
                        votingCryptoClient.closeVotingDb(dbHandle)
                    }
                }
            preparationResult
        }

    private suspend fun recoverExistingBundleSetup(
        accountUuid: String,
        roundId: String,
        dbHandle: Long,
        walletDbPath: String,
        snapshotHeight: Long,
        networkId: Int,
        accountUuidBytes: ByteArray
    ): VotingBundleSetupResult {
        val dbBundleCount = votingCryptoClient.getBundleCount(dbHandle, roundId)
        require(dbBundleCount >= 0) {
            "Failed to recover voting bundle count for round $roundId"
        }

        val notesJson =
            votingCryptoClient.getWalletNotesJson(
                walletDbPath = walletDbPath,
                snapshotHeight = snapshotHeight,
                networkId = networkId,
                accountUuidBytes = accountUuidBytes
            )
        val computedSetup = votingCryptoClient.computeBundleSetup(notesJson)
        require(computedSetup.bundleCount >= dbBundleCount) {
            "Voting round $roundId has $dbBundleCount DB bundles but only ${computedSetup.bundleCount} snapshot bundles"
        }

        val recoveredWeights = computedSetup.bundleWeights.take(dbBundleCount)
        val recoveredSetup =
            VotingBundleSetupResult(
                bundleCount = dbBundleCount,
                eligibleWeight = recoveredWeights.sum(),
                bundleWeights = recoveredWeights
            )
        votingRecoveryRepository.storeBundleSetup(
            accountUuid = accountUuid,
            roundId = roundId,
            bundleCount = recoveredSetup.bundleCount,
            eligibleWeight = recoveredSetup.eligibleWeight,
            bundleWeights = recoveredSetup.bundleWeights
        )
        return recoveredSetup
    }

    private suspend fun getOrCreateHotkeySeed(
        accountUuid: String,
        roundId: String,
        hotkeyBound: Boolean
    ): ByteArray {
        val recoverySnapshot = votingRecoveryRepository.get(accountUuid, roundId)
        val legacySeed = recoverySnapshot?.decodeHotkeySeed()
        val storedSeed = votingHotkeySeedProvider.get(accountUuid)

        if (legacySeed != null && storedSeed == null) {
            votingHotkeySeedProvider.store(accountUuid, legacySeed)
        }

        return legacySeed ?: storedSeed ?: run {
            if (hotkeyBound) {
                error("Missing stored hotkey seed for resumed round $roundId")
            }

            ByteArray(HOTKEY_SEED_BYTES)
                .also(secureRandom::nextBytes)
                .also { seed -> votingHotkeySeedProvider.store(accountUuid, seed) }
        }
    }

    private suspend fun awaitFullyScannedHeight(synchronizer: Synchronizer): Long? {
        synchronizer.fullyScannedHeight.value
            ?.value
            ?.takeIf { it > 0 }
            ?.let { return it }

        withTimeoutOrNull(ENGINE_BOOT_TIMEOUT_MS) {
            synchronizer.status.first { it != Synchronizer.Status.INITIALIZING }
        }

        return withTimeoutOrNull(FULLY_SCANNED_HEIGHT_TIMEOUT_MS) {
            synchronizer.fullyScannedHeight
                .filterNotNull()
                .map { it.value }
                .first { it > 0 }
        } ?: synchronizer.fullyScannedHeight.value?.value
    }

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 1 else 0

    private companion object {
        const val TAG = "PrepareVotingRound"
        const val ENGINE_BOOT_TIMEOUT_MS = 30_000L
        const val FULLY_SCANNED_HEIGHT_TIMEOUT_MS = 5_000L
        const val HOTKEY_SEED_BYTES = 64
    }
}
