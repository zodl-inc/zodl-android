package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSignatureInput
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSigningRequest
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
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

/**
 * voting-5.0.0 production-completion note: rebuilt against the SDK's already-existing
 * [cash.z.ecc.android.sdk.VotingRoundSession.getKeystoneSigningRequests]/
 * [cash.z.ecc.android.sdk.VotingDbSession.storeKeystoneSignatures] -- both were fully implemented
 * on the SDK side already; this class's job is only to (a) get a [VotingKeystoneSessionHolder]-
 * retained session's delegation pipeline built, (b) turn one [VotingKeystoneSigningRequest] into a
 * scannable [UREncoder] and back, and (c) re-implement the duplicate/wrong-device-scan detection
 * client-side, since the crate's own `store_keystone_signatures_batch` guard only fires on a
 * genuine sighash/rk mismatch against already-*persisted* bundle columns, not against the
 * currently-pending, not-yet-signed request.
 *
 * Idempotent per-call design (mirroring Vizor Wallet's shipping implementation on the same
 * crate): [createPcztEncoder] always derives the next bundle still needing a signature from
 * durable state rather than tracking "what have I already asked for" itself, so a killed and
 * relaunched app simply re-derives the same state without bespoke recovery bookkeeping beyond
 * what `VotingHomeHooksImpl.recoverPendingRouteIfNeeded` already does (untouched by this task).
 */
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

@Suppress("LongParameterList")
class VotingKeystoneRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val votingKeystoneSessionHolder: VotingKeystoneSessionHolder,
    private val votingCryptoClient: VotingCryptoClient,
    private val resolveVotingRoundSession: ResolveVotingRoundSessionUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingHotkeySeedProvider: VotingHotkeySeedProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val keystoneSDKProvider: KeystoneSDKProvider,
) : VotingKeystoneRepository {
    // SwallowedException: TorUnavailableException means the user has Tor turned off -- an
    // expected configuration, not an error worth propagating or logging. The failure mode that
    // MUST propagate (TorInitializationErrorException: Tor is on but failed to bootstrap) is
    // deliberately not caught here.
    @Suppress("LongMethod", "SwallowedException")
    override suspend fun createPcztEncoder(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle =
        withContext(Dispatchers.IO) {
            val selectedAccount = requireSelectedKeystoneAccount(accountUuid)
            val sessionContext = resolveVotingRoundSession(roundId)
            val session = sessionContext.session
            val sessionRoundId = session.voteRoundId.toHex()
            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val recovery =
                requireNotNull(votingRecoveryRepository.get(accountUuid, roundId)) {
                    "Voting round $roundId has not been prepared"
                }
            val bundleCount =
                recovery.bundleCount
                    ?: error("Voting round $roundId has no prepared bundle count")
            val nextUnsignedBundleIndex =
                (0 until bundleCount)
                    .firstOrNull { index -> index !in recovery.keystoneBundleSignatures }
                    ?: throw VotingKeystoneBundlesAlreadySignedException(roundId)

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val votingDbPath = deriveVotingDbPath(walletDbPath)
            val networkId = synchronizer.network.toVotingNetworkId()
            val hotkeySecret = getHotkeySeed(accountUuid, roundId, recovery)
            val voteServerUrls =
                sessionContext.serviceConfig.voteServers
                    .map { endpoint -> endpoint.url.trimEnd('/') }
                    .distinct()
            val pirLayout = sessionContext.serviceConfig.pirLayout
            val treeStateBytes = synchronizer.getTreeState(BlockHeight.new(session.snapshotHeight))
            // Same Tor policy as SubmitVotesUseCase's non-Keystone path: `0L` is the SDK's
            // "no Tor runtime" sentinel and is only used when Tor is genuinely disabled.
            // TorInitializationErrorException (Tor is ON but failed to bootstrap) must propagate
            // rather than silently deanonymizing this round's delegation traffic.
            val torRuntime =
                try {
                    synchronizer.getVotingTorRuntimeHandle()
                } catch (e: TorUnavailableException) {
                    0L
                }

            val delegationInputs =
                VotingDelegationInputs(
                    walletDbPath = walletDbPath,
                    // Canonical dashed UUID, NOT the hex account-scope id used for setWalletId --
                    // the native side parses this with `uuid::Uuid::parse_str` to look the
                    // account up inside the real wallet database (see toCanonicalUuidString).
                    accountUuid = selectedAccount.sdkAccount.accountUuid.toCanonicalUuidString(),
                    anchorTreeStateBytes = treeStateBytes,
                    hotkeySecret = hotkeySecret,
                    pirEndpoints = sessionContext.serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                    pirDepth = pirLayout.requireKnownPolyLen().pirDepth,
                    pirTier0Layers = pirLayout.tier0Layers,
                    pirTier1Layers = pirLayout.tier1Layers,
                    pirPolyLen = pirLayout.polyLen,
                    keystone = true,
                    softwareSeed = null,
                    keystoneSig = null,
                    keystoneSighash = null,
                    snapshotHeight = session.snapshotHeight,
                    eaPk = session.eaPK,
                    ncRoot = session.ncRoot,
                    nullifierImtRoot = session.nullifierIMTRoot
                )

            votingKeystoneSessionHolder.ensureDelegationPipeline(
                roundId = roundId,
                votingDbPath = votingDbPath,
                accountUuidString = accountUuid,
                networkId = networkId,
                torRuntime = torRuntime,
                proposals =
                    session.proposals.map { proposal ->
                        VotingProposalRosterEntry(proposalId = proposal.id, numOptions = proposal.options.size)
                    },
                hotkeySecret = hotkeySecret,
                chainEndpoints = voteServerUrls,
                ceremonyStartSeconds = session.ceremonyStart.epochSecond,
                voteEndTimeSeconds = session.voteEndTime.epochSecond,
                delegationInputs = delegationInputs
            )

            val request =
                votingKeystoneSessionHolder
                    .getKeystoneSigningRequests(roundId, listOf(nextUnsignedBundleIndex))
                    .firstOrNull { it.bundleIndex == nextUnsignedBundleIndex }
                    ?: error("Round $roundId returned no signing request for bundle $nextUnsignedBundleIndex")

            votingRecoveryRepository.storePendingKeystoneRequest(
                accountUuid = accountUuid,
                roundId = roundId,
                bundleIndex = request.bundleIndex,
                actionIndex = request.actionIndex,
                redactedPczt = request.redactedPcztBytes,
                expectedSighash = request.pcztSighash,
                expectedRk = request.rk
            )

            VotingKeystoneSigningBundle(
                roundId = roundId,
                roundTitle = session.title,
                bundleIndex = request.bundleIndex,
                bundleCount = request.bundleCount,
                actionIndex = request.actionIndex,
                memoWeightZatoshi = request.delegatedWeightZatoshi,
                encoder = keystoneSDKProvider.generatePczt(request.redactedPcztBytes)
            )
        }

    override suspend fun storeBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    ) = withContext(Dispatchers.IO) {
        requireSelectedKeystoneAccount(accountUuid)
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
        val bundleCount =
            recovery.bundleCount
                ?: error("Voting round $roundId has no prepared bundle count")

        val signedPcztBytes = keystoneSDKProvider.parsePczt(signedPcztUr)
        val (spendAuthSig, scannedSighash) =
            extractSpendAuthSignatureAndSighash(
                signedPcztBytes = signedPcztBytes,
                actionIndex = actionIndex
            )

        rejectMismatchedKeystoneSighash(
            scannedSighash = scannedSighash,
            pendingBundleIndex = pendingRequest.bundleIndex,
            requests = knownSighashRequests(recovery, pendingRequest, bundleCount)
        )

        // Persist the signature crate-side first, so it is protected by
        // `resetVotingSessionState`'s preservation guard even if the local recovery-repository
        // write below never happens. The rk/sighash passed here are the crate's own values from
        // the governance PCZT this signature was produced for (consistency-checked above via
        // rejectMismatchedKeystoneSighash), matching the SDK's documented "already verified"
        // contract -- the native side additionally re-verifies `sig` against `rk`/`sighash`
        // with RedPallas before writing (see storeKeystoneSignaturesNative).
        pendingRequest.decodeExpectedRk()?.let { rk ->
            persistKeystoneSignatureCrateSide(
                accountUuid = accountUuid,
                roundId = roundId,
                bundleIndex = bundleIndex,
                keystoneSig = spendAuthSig,
                keystoneSighash = scannedSighash,
                rk = rk
            )
        }

        votingRecoveryRepository.storeKeystoneBundleSignature(
            accountUuid = accountUuid,
            roundId = roundId,
            bundleIndex = bundleIndex,
            spendAuthSig = spendAuthSig,
            sighash = scannedSighash,
            rk = pendingRequest.decodeExpectedRk()
        )
    }

    /**
     * Extracts the 64-byte RedPallas spend-auth signature and the 32-byte PCZT sighash out of the
     * Keystone-returned signed PCZT.
     *
     * **BLOCKED — needs an SDK-side API that no longer exists.** The pre-round-driver
     * implementation (commit `c62038c3a`, `VotingKeystoneRepository.kt:330-343`) did this with
     * `votingCryptoClient.extractPcztSighash(signedPcztBytes)` +
     * `votingCryptoClient.extractSpendAuthSignatureFromSignedPczt(signedPcztBytes, actionIndex)`,
     * which bottomed out in `VotingSdk.extractPcztSighash`/`extractSpendAuthSig` and, below
     * those, the `extractPcztSighashNative`/`extractSpendAuthSigNative` JNI exports wrapping
     * `zcash_voting::action::extract_pczt_sighash`/`extract_spend_auth_sig`.
     *
     * Both JNI exports and both Kotlin wrappers were deleted from the SDK by the round-driver
     * port (`zcash-android-wallet-sdk` commits `c7b9cc99` and `5f5795b9`), with no replacement:
     * there is no public API on the current SDK branch that turns a signed PCZT into its
     * spend-auth signature. The underlying crate functions DO still exist upstream
     * (`zcash_voting` rev `c2c99eb5`, `zcash_voting/src/action.rs:799` and `:850`), so the fix is
     * re-exposing them, not reimplementing anything.
     *
     * This is deliberately NOT reimplemented in Kotlin: the crate-side body is a full
     * `pczt::Pczt::parse` of the PCZT v5 binary format followed by
     * `ironwood().actions()[i].spend().spend_auth_sig()` — hand-rolling that byte layout in
     * Kotlin would be exactly the kind of guessed cryptographic parsing that can silently yield
     * a wrong-but-well-formed signature.
     *
     * Note the shape correction versus the task brief: the brief specified
     * `extractSpendAuthSignatureAndRk(UR): Pair<sig, rk>`, but `rk` is never recoverable from the
     * signed PCZT — Keystone redacts it. `rk` is the crate's own value, carried on
     * [VotingKeystoneSigningRequest.rk] and persisted in the pending request; the second value
     * that genuinely must come out of the scanned PCZT is the sighash, which the
     * duplicate/wrong-signature check needs.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun extractSpendAuthSignatureAndSighash(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): Pair<ByteArray, ByteArray> =
        error(
            "Keystone voting signature extraction needs VotingSdk.extractSpendAuthSig/" +
                "extractPcztSighash, both removed from the SDK by the round-driver port " +
                "(zcash-android-wallet-sdk c7b9cc99/5f5795b9). Re-expose " +
                "zcash_voting::action::extract_spend_auth_sig/extract_pczt_sighash through the " +
                "voting JNI layer before enabling Keystone voting."
        )

    /**
     * The sighash-bearing requests this device can compare a freshly scanned signature against:
     * the currently-pending, not-yet-signed bundle plus every bundle already carrying a stored
     * signature. Reconstructed from durable local state rather than re-fetched from the round
     * session, because only the pending bundle's request is live at scan time.
     */
    private fun knownSighashRequests(
        recovery: VotingRecoverySnapshot,
        pendingRequest: VotingPendingKeystoneRequest,
        bundleCount: Int
    ): List<VotingKeystoneSigningRequest> =
        buildList {
            add(
                sighashOnlyRequest(
                    bundleIndex = pendingRequest.bundleIndex,
                    actionIndex = pendingRequest.actionIndex,
                    sighash = pendingRequest.decodeExpectedSighash(),
                    rk = pendingRequest.decodeExpectedRk(),
                    bundleCount = bundleCount
                )
            )
            recovery.keystoneBundleSignatures.forEach { (signedBundleIndex, signature) ->
                if (signedBundleIndex != pendingRequest.bundleIndex) {
                    add(
                        sighashOnlyRequest(
                            bundleIndex = signedBundleIndex,
                            actionIndex = 0,
                            sighash = signature.decodeSighash(),
                            rk = signature.decodeRk(),
                            bundleCount = bundleCount
                        )
                    )
                }
            }
        }

    /**
     * A [VotingKeystoneSigningRequest] carrying only the fields
     * [rejectMismatchedKeystoneSighash] actually compares. Not a real signing request — never
     * hand one of these to the SDK.
     */
    private fun sighashOnlyRequest(
        bundleIndex: Int,
        actionIndex: Int,
        sighash: ByteArray,
        rk: ByteArray?,
        bundleCount: Int
    ) = VotingKeystoneSigningRequest(
        pcztBytes = ByteArray(0),
        redactedPcztBytes = ByteArray(0),
        pcztSighash = sighash,
        rk = rk ?: ByteArray(0),
        actionIndex = actionIndex,
        displayMemo = "",
        eligibleWeightZatoshi = 0,
        delegatedWeightZatoshi = 0,
        bundleCount = bundleCount,
        bundleIndex = bundleIndex
    )

    private suspend fun persistKeystoneSignatureCrateSide(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray,
        rk: ByteArray
    ) {
        val votingDbPath = deriveVotingDbPath(synchronizerProvider.getVotingWalletDbPath())
        val networkId = synchronizerProvider.getSynchronizer().network.toVotingNetworkId()
        val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
        check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }
        try {
            votingCryptoClient.setWalletId(dbHandle, accountUuid, networkId)
            votingCryptoClient.storeKeystoneSignatures(
                dbHandle = dbHandle,
                roundId = roundId,
                signatures =
                    listOf(
                        VotingKeystoneSignatureInput(
                            bundleIndex = bundleIndex,
                            sig = keystoneSig,
                            sighash = keystoneSighash,
                            rk = rk
                        )
                    )
            )
        } finally {
            votingCryptoClient.closeVotingDb(dbHandle)
        }
    }

    private suspend fun requireSelectedKeystoneAccount(accountUuid: String): KeystoneAccount {
        val selectedAccount =
            requireNotNull(accountDataSource.getSelectedAccount() as? KeystoneAccount) {
                "Keystone account is required for voting signature flow"
            }
        require(selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId() == accountUuid) {
            "Selected Keystone account changed during the voting signature flow"
        }
        return selectedAccount
    }

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

    private fun deriveVotingDbPath(walletDbPath: String): String =
        File(walletDbPath)
            .parentFile
            ?.resolve("voting.sqlite3")
            ?.absolutePath
            ?: error("Unable to derive voting DB path from $walletDbPath")
}

/**
 * Distinguishes a stale re-scanned QR (matches an already-recorded signature for a *different*
 * bundle than [pendingBundleIndex]) from a genuinely wrong device/PCZT (matches neither) -- has
 * no SDK equivalent (the crate's own batch-store guard only fires post-persistence), so this
 * client-side check is a deliberate re-implementation of the pre-4.0 behavior, not a gap.
 */
internal fun rejectMismatchedKeystoneSighash(
    scannedSighash: ByteArray,
    pendingBundleIndex: Int,
    requests: List<VotingKeystoneSigningRequest>
) {
    val matchingRequest = requests.firstOrNull { it.pcztSighash.contentEquals(scannedSighash) }
    when {
        matchingRequest == null -> {
            throw VotingKeystoneWrongSignatureException(
                currentBundleIndex = pendingBundleIndex,
                bundleCount = requests.firstOrNull()?.bundleCount ?: 0
            )
        }

        matchingRequest.bundleIndex != pendingBundleIndex -> {
            throw VotingKeystoneDuplicateSignatureException(
                signedBundleIndex = matchingRequest.bundleIndex,
                currentBundleIndex = pendingBundleIndex,
                bundleCount = matchingRequest.bundleCount
            )
        }

        else -> {
            Unit
        }
    }
}

private fun ZcashNetwork.toVotingNetworkId() = if (isMainnet()) 1 else 0
