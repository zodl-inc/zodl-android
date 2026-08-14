package co.electriccoin.zcash.ui.common.repository

import android.util.Log
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.isDelegationSetupOverwrite
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import co.electriccoin.zcash.ui.common.model.voting.votingBundleRawWeights
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VotingKeystoneSigningBundle(
    val roundId: String,
    val roundTitle: String,
    val bundleIndex: Int,
    val bundleCount: Int,
    val actionIndex: Int,
    val memoWeightZatoshi: Long,
    val encoder: UREncoder,
)

sealed class VotingKeystoneResumeSubmissionException(
    message: String
) : Exception(message)

class VotingKeystoneBundlesAlreadySignedException(
    roundId: String
) : VotingKeystoneResumeSubmissionException(
        "All Keystone voting bundles are already signed for round $roundId"
    )

sealed class VotingKeystoneSignatureRejectedException(
    message: String
) : Exception(message)

class VotingKeystoneDuplicateSignatureException(
    val signedBundleIndex: Int,
    val currentBundleIndex: Int,
    val bundleCount: Int
) : VotingKeystoneSignatureRejectedException(
        "Keystone signature for bundle $signedBundleIndex was scanned while waiting for bundle $currentBundleIndex"
    )

class VotingKeystoneWrongSignatureException(
    val currentBundleIndex: Int,
    val bundleCount: Int
) : VotingKeystoneSignatureRejectedException(
        "Signed Keystone PCZT does not match pending bundle $currentBundleIndex"
    )

interface VotingKeystoneRepository {
    suspend fun createPcztEncoder(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle

    suspend fun storeBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    )
}

class VotingKeystoneRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val resolveVotingRoundSession: ResolveVotingRoundSessionUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingHotkeySeedProvider: VotingHotkeySeedProvider,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val keystoneSDKProvider: KeystoneSDKProvider
) : VotingKeystoneRepository {
    override suspend fun createPcztEncoder(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle =
        withContext(Dispatchers.IO) {
            val selectedAccount =
                requireNotNull(accountDataSource.getSelectedAccount() as? KeystoneAccount) {
                    "Keystone account is required for voting signature flow"
                }
            val selectedAccountUuid = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            require(selectedAccountUuid == accountUuid) {
                "Selected Keystone account changed during the voting signature flow"
            }
            val sessionContext = resolveVotingRoundSession(roundId)
            val session = sessionContext.session
            val sessionRoundId = session.voteRoundId.toLowerHex()
            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val recovery =
                requireNotNull(votingRecoveryRepository.get(accountUuid, roundId)) {
                    "Voting round $roundId has not been prepared"
                }
            val bundleCount = recovery.bundleCount ?: error("Voting round $roundId has no prepared bundle count")
            val nextUnsignedBundleIndex =
                (0 until bundleCount)
                    .firstOrNull { index -> index !in recovery.keystoneBundleSignatures }
                    ?: throw VotingKeystoneBundlesAlreadySignedException(roundId)

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val networkId = synchronizer.network.toVotingNetworkId()
            val allNotesJson =
                votingCryptoClient.getWalletNotesJson(
                    walletDbPath = walletDbPath,
                    snapshotHeight = session.snapshotHeight,
                    networkId = networkId,
                    accountUuidBytes = selectedAccount.sdkAccount.accountUuid.value
                )
            val bundleRawWeights = votingBundleRawWeights(allNotesJson)
            val memoWeightZatoshi =
                bundleRawWeights.getOrNull(nextUnsignedBundleIndex)
                    ?: error("Voting round $roundId has no raw memo weight for bundle $nextUnsignedBundleIndex")

            val pendingRequest =
                recovery.pendingKeystoneRequest
                    ?.takeIf { request ->
                        request.bundleIndex == nextUnsignedBundleIndex &&
                            request.bundleIndex !in recovery.keystoneBundleSignatures
                    }
            if (pendingRequest != null) {
                return@withContext VotingKeystoneSigningBundle(
                    roundId = roundId,
                    roundTitle = session.title,
                    bundleIndex = pendingRequest.bundleIndex,
                    bundleCount = bundleCount,
                    actionIndex = pendingRequest.actionIndex,
                    memoWeightZatoshi = memoWeightZatoshi,
                    encoder = keystoneSDKProvider.generatePczt(pendingRequest.decodeRedactedPczt())
                )
            }
            if (recovery.pendingKeystoneRequest != null) {
                Log.i(TAG, "Clearing stale Keystone voting request for round $roundId")
                votingRecoveryRepository.clearPendingKeystoneRequest(accountUuid, roundId)
            }

            val hotkeySeed = getHotkeySeed(accountUuid, roundId, recovery)
            val bundleIndex = nextUnsignedBundleIndex

            val accountIndex =
                selectedAccount.sdkAccount.hdAccountIndex
                    ?.index
                    ?.toInt()
                    ?: error("Keystone account is missing ZIP-32 account index")
            val ufvk =
                selectedAccount.sdkAccount.ufvk
                    ?: error("Keystone account is missing UFVK")
            val seedFingerprint =
                selectedAccount.sdkAccount.seedFingerprint
                    ?: error("Keystone account is missing seed fingerprint")

            val votingDbPath = deriveVotingDbPath(walletDbPath)

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            val (signingBundle, pendingPrecomputeRequest) =
                try {
                    votingCryptoClient.setWalletId(
                        dbHandle,
                        accountUuid,
                        networkId
                    )
                    votingCryptoClient.generateNoteWitnessesJson(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        walletDbPath = walletDbPath,
                        networkId = networkId,
                        notesJson = allNotesJson
                    )
                    val fvkBytes = votingCryptoClient.extractOrchardFvkFromUfvk(ufvk, networkId)
                    // Keystone signing starts by building a governance PCZT. Multi-bundle rounds
                    // legitimately have other bundles already past this point (round-level phase
                    // can't tell them apart from this one — 2026-08-10), so this always attempts
                    // construct rather than pre-checking phase. By this point the caller has
                    // already returned early via `pendingRequest` above for any bundle whose
                    // redacted PCZT is still cached app-side, so a setup-overwrite refusal here
                    // means the Rust-side PCZT survived while the app's own cache of it didn't.
                    // In practice this is triggered by Android process death: buildGovernancePczt
                    // commits its native-side state synchronously inside this JNI call, but
                    // storePendingKeystoneRequest() below (which persists the app's own redacted-
                    // PCZT cache) is a distinct, later step — if the OS kills the process in that
                    // window (routine backgrounding/memory pressure, made more likely by a slow
                    // Halo2 proving operation), the native side is left committed while the app's
                    // cache never got written. This is NOT reachable via a plain reinstall: the
                    // crate's SQLite DB and this recovery cache both live in the same app-private
                    // storage, so a real reinstall wipes them symmetrically. Either way, there's
                    // no redacted PCZT left to hand back to the user, so the only way forward is
                    // to reset this round's unsigned setup and mint a fresh one — which draws a
                    // fresh `alpha`, so the rebuilt bundle is flagged via
                    // markBundleRebuiltSinceProof below to force SubmitVotesUseCase to re-prove
                    // rather than pairing a stale proof (computed for the old alpha) with the new
                    // bundle fields.
                    val governancePczt =
                        runCatching {
                            votingCryptoClient.buildGovernancePczt(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                fvkBytes = fvkBytes,
                                hotkeySeed = hotkeySeed,
                                accountIndex = accountIndex,
                                notesJson = allNotesJson,
                                seedFingerprint = seedFingerprint,
                                roundName = session.title
                            )
                        }.recoverCatching { throwable ->
                            if (!throwable.isDelegationSetupOverwrite()) throw throwable
                            Log.i(
                                TAG,
                                "Keystone governance PCZT setup for round $roundId bundle $bundleIndex " +
                                    "looks corrupted; resetting and rebuilding once"
                            )
                            votingCryptoClient.resetVotingSessionState(dbHandle, roundId)
                            votingCryptoClient
                                .buildGovernancePczt(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    fvkBytes = fvkBytes,
                                    hotkeySeed = hotkeySeed,
                                    accountIndex = accountIndex,
                                    notesJson = allNotesJson,
                                    seedFingerprint = seedFingerprint,
                                    roundName = session.title
                                ).also {
                                    votingRecoveryRepository.markBundleRebuiltSinceProof(
                                        accountUuid = accountUuid,
                                        roundId = roundId,
                                        bundleIndex = bundleIndex
                                    )
                                }
                        }.getOrThrow()
                    val redactedPcztBytes =
                        synchronizer
                            .redactPcztForSigner(Pczt(governancePczt.pcztBytes))
                            .toByteArray()
                    votingRecoveryRepository.storePendingKeystoneRequest(
                        accountUuid = accountUuid,
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        actionIndex = governancePczt.actionIndex,
                        redactedPczt = redactedPcztBytes,
                        expectedSighash = governancePczt.sighash,
                        expectedRk = governancePczt.rk
                    )
                    val precomputeRequest =
                        VotingDelegationPirPrecomputeRequest(
                            accountUuid = accountUuid,
                            walletId = accountUuid,
                            votingDbPath = votingDbPath,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            pirEndpoints = sessionContext.serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                            pirLayout = sessionContext.serviceConfig.pirLayout.requireKnownPolyLen(),
                            expectedSnapshotHeight = session.snapshotHeight,
                            networkId = networkId,
                            notesJson = allNotesJson
                        )
                    VotingKeystoneSigningBundle(
                        roundId = roundId,
                        roundTitle = session.title,
                        bundleIndex = bundleIndex,
                        bundleCount = bundleCount,
                        actionIndex = governancePczt.actionIndex,
                        memoWeightZatoshi = memoWeightZatoshi,
                        encoder = keystoneSDKProvider.generatePczt(redactedPcztBytes)
                    ) to precomputeRequest
                } finally {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            runCatching {
                votingProofPrecomputeRepository.startDelegationPirPrecompute(pendingPrecomputeRequest)
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "Skipping Keystone voting PIR precompute for round $roundId bundle $bundleIndex",
                    throwable
                )
            }
            signingBundle
        }

    override suspend fun storeBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    ) = withContext(Dispatchers.IO) {
        val selectedAccount =
            requireNotNull(accountDataSource.getSelectedAccount() as? KeystoneAccount) {
                "Keystone account is required for voting signature flow"
            }
        require(selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId() == accountUuid) {
            "Selected Keystone account changed during the voting signature flow"
        }
        val recovery =
            requireNotNull(votingRecoveryRepository.get(accountUuid, roundId)) {
                "Voting round $roundId has not been prepared"
            }
        val pendingRequest =
            requireNotNull(recovery.pendingKeystoneRequest) {
                "No pending Keystone voting request exists for round $roundId"
            }
        require(pendingRequest.bundleIndex == bundleIndex) {
            "Signed Keystone bundle $bundleIndex does not match pending bundle ${pendingRequest.bundleIndex}"
        }
        require(pendingRequest.actionIndex == actionIndex) {
            "Signed Keystone action $actionIndex does not match pending action ${pendingRequest.actionIndex}"
        }
        val bundleCount = recovery.bundleCount ?: error("Voting round $roundId has no prepared bundle count")
        val signedPcztBytes = keystoneSDKProvider.parsePczt(signedPcztUr)
        val sighash = votingCryptoClient.extractPcztSighash(signedPcztBytes)
        rejectMismatchedKeystoneSighash(
            scannedSighash = sighash,
            expectedSighash = pendingRequest.decodeExpectedSighash(),
            existingSignatures = recovery.keystoneBundleSignatures,
            currentBundleIndex = bundleIndex,
            bundleCount = bundleCount
        )
        val spendAuthSig =
            votingCryptoClient.extractSpendAuthSignatureFromSignedPczt(
                signedPcztBytes = signedPcztBytes,
                actionIndex = actionIndex
            )
        // Persist the signature crate-side first, so it's protected by
        // `resetVotingSessionState`'s preservation guard even if the local recovery-repository
        // write below never happens. The rk/sighash passed here are the crate-computed values
        // from the governance PCZT this signature was produced for (checked for consistency
        // above via rejectMismatchedKeystoneSighash), the best available proxy at this call site
        // for the SDK's documented "already verified" pair — the actual spend-auth-sig-vs-rk
        // check only becomes possible once a delegation proof exists, at submission time in
        // SubmitVotesUseCase, which independently re-asserts these same values against the
        // crate's own submission result.
        pendingRequest.decodeExpectedRk()?.let { rk ->
            persistKeystoneSignatureCrateSide(
                roundId = roundId,
                bundleIndex = bundleIndex,
                keystoneSig = spendAuthSig,
                keystoneSighash = sighash,
                rk = rk
            )
        }
        votingRecoveryRepository.storeKeystoneBundleSignature(
            accountUuid = accountUuid,
            roundId = roundId,
            bundleIndex = bundleIndex,
            spendAuthSig = spendAuthSig,
            sighash = sighash,
            rk = pendingRequest.decodeExpectedRk()
        )
    }

    private suspend fun persistKeystoneSignatureCrateSide(
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray,
        rk: ByteArray
    ) {
        val votingDbPath = resolveVotingDbPath()
        val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
        check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }
        try {
            votingCryptoClient.storeKeystoneSignature(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                keystoneSig = keystoneSig,
                keystoneSighash = keystoneSighash,
                rk = rk
            )
        } finally {
            votingCryptoClient.closeVotingDb(dbHandle)
        }
    }

    private suspend fun resolveVotingDbPath(): String =
        deriveVotingDbPath(synchronizerProvider.getVotingWalletDbPath())

    private fun deriveVotingDbPath(walletDbPath: String): String =
        File(walletDbPath)
            .parentFile
            ?.resolve("voting.sqlite3")
            ?.absolutePath
            ?: error("Unable to derive voting DB path from $walletDbPath")

    private fun ZcashNetwork.toVotingNetworkId() =
        when (this) {
            ZcashNetwork.Mainnet -> 1
            ZcashNetwork.Testnet -> 0
            else -> error("Unsupported voting network: $this")
        }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    private suspend fun getHotkeySeed(
        accountUuid: String,
        roundId: String,
        recovery: VotingRecoverySnapshot
    ): ByteArray {
        recovery.decodeHotkeySeed()?.let { legacySeed ->
            if (votingHotkeySeedProvider.get(accountUuid) == null) {
                votingHotkeySeedProvider.store(accountUuid, legacySeed)
            }
            return legacySeed
        }

        return votingHotkeySeedProvider.get(accountUuid)
            ?: error("Voting round $roundId has no stored hotkey seed")
    }

    private fun rejectMismatchedKeystoneSighash(
        scannedSighash: ByteArray,
        expectedSighash: ByteArray,
        existingSignatures: Map<Int, VotingKeystoneBundleSignature>,
        currentBundleIndex: Int,
        bundleCount: Int
    ) {
        if (scannedSighash.contentEquals(expectedSighash)) {
            return
        }

        val duplicateBundleIndex =
            existingSignatures.entries
                .firstOrNull { (_, signature) ->
                    scannedSighash.contentEquals(signature.decodeSighash())
                }?.key

        if (duplicateBundleIndex != null) {
            throw VotingKeystoneDuplicateSignatureException(
                signedBundleIndex = duplicateBundleIndex,
                currentBundleIndex = currentBundleIndex,
                bundleCount = bundleCount
            )
        }

        throw VotingKeystoneWrongSignatureException(
            currentBundleIndex = currentBundleIndex,
            bundleCount = bundleCount
        )
    }

    private companion object {
        const val TAG = "VotingKeystoneRepository"
        const val BYTE_MASK = 0xff
    }
}
