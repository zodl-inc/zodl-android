package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.selectVotingBundleNotesJson
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingDelegationPirPrecomputeRequest
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.repository.VotingEligibility
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.SecureRandom

class PrepareVotingRoundUseCase(
    private val votingConfigRepository: VotingConfigRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val refreshActiveVotingSession: RefreshActiveVotingSessionUseCase
) {
    private val secureRandom = SecureRandom()

    suspend operator fun invoke(roundId: String): VotingRoundPreparationResult =
        withContext(Dispatchers.IO) {
            votingProofPrecomputeRepository.warmProvingCaches()
            refreshActiveVotingSession()
            val config = requireNotNull(
                votingConfigRepository.currentConfig.value ?: votingConfigRepository.get()
            ) {
                "No active voting session is loaded"
            }
            val session = config.session
            val sessionRoundId = session.voteRoundId.toHex()

            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val synchronizer = synchronizerProvider.getSynchronizer()
            val scannedHeight = awaitFullyScannedHeight(synchronizer)
            if (scannedHeight == null || scannedHeight < session.snapshotHeight) {
                votingSessionStore.setEligibility(VotingEligibility.WALLET_SYNCING)
                return@withContext VotingRoundPreparationResult.WalletSyncing(
                    scannedHeight = scannedHeight,
                    snapshotHeight = session.snapshotHeight
                )
            }

            val selectedAccount = getSelectedWalletAccount()
            val accountUuid = selectedAccount.sdkAccount.accountUuid
            val accountUuidString = accountUuid.toVotingAccountScopeId()
            val walletDbPath = synchronizer.getWalletDbPath()
            val votingDbPath = File(walletDbPath)
                .parentFile
                ?.resolve("voting.sqlite3")
                ?.absolutePath
                ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val recoverySnapshot = votingRecoveryRepository.get(accountUuidString, roundId)
            val bundleNotesJsonByIndex = mutableMapOf<Int, String>()
            val pendingPrecomputeRequests = mutableListOf<VotingDelegationPirPrecomputeRequest>()

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            val preparationResult = try {
                votingCryptoClient.setWalletId(dbHandle, accountUuid.toString())
                val existingRoundState = votingCryptoClient.getRoundState(dbHandle, roundId)
                val (preparedBundleCount, eligibleWeight) = if (existingRoundState == null) {
                    votingCryptoClient.initializeRound(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        snapshotHeight = session.snapshotHeight,
                        eaPK = session.eaPK,
                        ncRoot = session.ncRoot,
                        nullifierIMTRoot = session.nullifierIMTRoot,
                        sessionJson = null
                    )

                    val notesJson = votingCryptoClient.getWalletNotesJson(
                        walletDbPath = walletDbPath,
                        snapshotHeight = session.snapshotHeight,
                        networkId = networkId,
                        accountUuidBytes = accountUuid.value
                    )
                    votingCryptoClient.setupBundles(
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
                    val setup = recoverySnapshot?.preparedBundleSetup()
                        ?: recoverExistingBundleSetup(
                            accountUuid = accountUuidString,
                            roundId = roundId,
                            dbHandle = dbHandle,
                            walletDbPath = walletDbPath,
                            snapshotHeight = session.snapshotHeight,
                            networkId = networkId,
                            accountUuidBytes = accountUuid.value
                        )
                    setup.bundleCount to setup.eligibleWeight
                }

                if (eligibleWeight <= 0L) {
                    votingSessionStore.setEligibility(VotingEligibility.INELIGIBLE)
                    return@withContext VotingRoundPreparationResult.Ineligible(
                        eligibleWeight = eligibleWeight,
                        bundleCount = preparedBundleCount
                    )
                }

                if (existingRoundState == null) {
                    val notesJson = votingCryptoClient.getWalletNotesJson(
                        walletDbPath = walletDbPath,
                        snapshotHeight = session.snapshotHeight,
                        networkId = networkId,
                        accountUuidBytes = accountUuid.value
                    )
                    val treeStateBytes = synchronizer.getTreeState(BlockHeight.new(session.snapshotHeight))
                    votingCryptoClient.storeTreeState(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        treeStateBytes = treeStateBytes
                    )
                    repeat(preparedBundleCount) { bundleIndex ->
                        val witnessesJson = votingCryptoClient.generateNoteWitnessesJson(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            walletDbPath = walletDbPath,
                            notesJson = notesJson
                        )
                        votingCryptoClient.storeWitnesses(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            witnessesJson = witnessesJson
                        )
                        bundleNotesJsonByIndex[bundleIndex] = notesJson.selectVotingBundleNotesJson(witnessesJson)
                    }
                }

                val storedHotkeySeed = recoverySnapshot?.decodeHotkeySeed()
                val hotkeySeed = when {
                    storedHotkeySeed != null -> storedHotkeySeed
                    existingRoundState?.phase != null && existingRoundState.phase != RoundPhase.INITIALIZED -> {
                        error("Missing stored hotkey seed for resumed round $roundId")
                    }

                    else -> ByteArray(HOTKEY_SEED_BYTES).also(secureRandom::nextBytes)
                }
                val hotkey = votingCryptoClient.generateHotkey(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    seed = hotkeySeed
                )
                votingRecoveryRepository.storeHotkey(
                    accountUuid = accountUuidString,
                    roundId = roundId,
                    hotkeySeed = hotkeySeed,
                    hotkeyAddress = hotkey.address
                )
                votingSessionStore.setEligibility(VotingEligibility.ELIGIBLE)
                if (existingRoundState == null && selectedAccount !is KeystoneAccount) {
                    runCatching {
                        pendingPrecomputeRequests += buildSoftwareDelegationPirPrecomputeRequests(
                            accountUuid = accountUuidString,
                            walletId = accountUuid.toString(),
                            votingDbPath = votingDbPath,
                            roundId = roundId,
                            networkId = networkId,
                            bundleCount = preparedBundleCount,
                            bundleNotesJsonByIndex = bundleNotesJsonByIndex,
                            dbHandle = dbHandle,
                            ufvk = requireNotNull(selectedAccount.sdkAccount.ufvk) {
                                "Software wallet account is missing UFVK for voting round $roundId"
                            },
                            accountIndex = selectedAccount.hdAccountIndex.index.toInt(),
                            hotkeySeed = hotkeySeed,
                            seedFingerprint = requireNotNull(selectedAccount.sdkAccount.seedFingerprint) {
                                "Software wallet account is missing seed fingerprint for voting round $roundId"
                            },
                            roundName = session.title,
                            pirEndpoints = config.serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                            expectedSnapshotHeight = session.snapshotHeight
                        )
                    }.onFailure { throwable ->
                        Log.w(TAG, "Skipping voting PIR precompute for round $roundId", throwable)
                    }
                }

                VotingRoundPreparationResult.Ready(
                    roundId = roundId,
                    bundleCount = preparedBundleCount,
                    eligibleWeight = eligibleWeight,
                    hotkeyAddress = hotkey.address
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
            pendingPrecomputeRequests.forEach(votingProofPrecomputeRepository::startDelegationPirPrecompute)
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

        val notesJson = votingCryptoClient.getWalletNotesJson(
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
        val recoveredSetup = VotingBundleSetupResult(
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

    private fun VotingRecoverySnapshot.preparedBundleSetup(): VotingBundleSetupResult? {
        val count = bundleCount ?: return null
        val weight = eligibleWeight ?: return null
        if (bundleWeights.size < count) {
            return null
        }
        return VotingBundleSetupResult(
            bundleCount = count,
            eligibleWeight = weight,
            bundleWeights = bundleWeights.take(count)
        )
    }

    private suspend fun buildSoftwareDelegationPirPrecomputeRequests(
        accountUuid: String,
        walletId: String,
        votingDbPath: String,
        roundId: String,
        networkId: Int,
        bundleCount: Int,
        bundleNotesJsonByIndex: Map<Int, String>,
        dbHandle: Long,
        ufvk: String,
        accountIndex: Int,
        hotkeySeed: ByteArray,
        seedFingerprint: ByteArray,
        roundName: String,
        pirEndpoints: List<String>,
        expectedSnapshotHeight: Long
    ): List<VotingDelegationPirPrecomputeRequest> {
        val requests = mutableListOf<VotingDelegationPirPrecomputeRequest>()
        repeat(bundleCount) { bundleIndex ->
            val bundleNotesJson = bundleNotesJsonByIndex[bundleIndex] ?: return@repeat
            runCatching {
                votingCryptoClient.buildGovernancePczt(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    ufvk = ufvk,
                    networkId = networkId,
                    accountIndex = accountIndex,
                    notesJson = bundleNotesJson,
                    hotkeyRawSeed = hotkeySeed,
                    seedFingerprint = seedFingerprint,
                    roundName = roundName
                )
                requests += VotingDelegationPirPrecomputeRequest(
                    accountUuid = accountUuid,
                    walletId = walletId,
                    votingDbPath = votingDbPath,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    pirEndpoints = pirEndpoints,
                    expectedSnapshotHeight = expectedSnapshotHeight,
                    networkId = networkId,
                    notesJson = bundleNotesJson
                )
            }.onFailure { throwable ->
                Log.w(TAG, "Skipping voting PIR precompute for round $roundId bundle $bundleIndex", throwable)
            }
        }
        return requests
    }

    private suspend fun awaitFullyScannedHeight(synchronizer: Synchronizer): Long? {
        synchronizer.fullyScannedHeight.value?.value
            ?.takeIf { it > 0 }
            ?.let { return it }

        return withTimeoutOrNull(FULLY_SCANNED_HEIGHT_TIMEOUT_MS) {
            synchronizer.fullyScannedHeight
                .filterNotNull()
                .map { it.value }
                .first { it > 0 }
        } ?: synchronizer.fullyScannedHeight.value?.value
    }

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 0 else 1

    private companion object {
        const val TAG = "PrepareVotingRound"
        const val FULLY_SCANNED_HEIGHT_TIMEOUT_MS = 5_000L
        const val HOTKEY_SEED_BYTES = 64
    }
}
