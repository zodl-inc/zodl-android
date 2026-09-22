@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.VotingDbSession
import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.VotingSdk
import cash.z.ecc.android.sdk.VotingShareTrackingSession
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.voting.VotingBallotIntent
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSignatureBatchResult
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSignatureInput
import cash.z.ecc.android.sdk.model.voting.VotingNoteInfo
import cash.z.ecc.android.sdk.model.voting.VotingNoteScope
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import cash.z.ecc.android.sdk.model.voting.VotingRoundPhase
import cash.z.ecc.android.sdk.model.voting.VotingRoundPlan
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import cash.z.ecc.android.sdk.model.voting.VotingRoundState
import cash.z.ecc.android.sdk.model.voting.VotingWitness
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundStateInfo
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationPirPrecomputeResult
import co.electriccoin.zcash.ui.common.model.voting.VotingHotkey
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingPirWarmupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSnapshotBundlePrecomputeResult
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import cash.z.ecc.android.sdk.model.voting.VotingBundleSetupResult as SdkVotingBundleSetupResult
import cash.z.ecc.android.sdk.model.voting.VotingDelegationPirPrecomputeResult as SdkVotingDelegationPirPrecomputeResult
import cash.z.ecc.android.sdk.model.voting.VotingHotkey as SdkVotingHotkey
import cash.z.ecc.android.sdk.model.voting.VotingPirPrecomputeResult as SdkVotingPirPrecomputeResult
import cash.z.ecc.android.sdk.model.voting.VotingSnapshotBundlePrecomputeReport as SdkVotingSnapshotBundlePrecomputeReport

/**
 * Kotlin surface over the voting-crypto backend, delegating to the public [VotingSdk] and its
 * per-round [VotingDbSession] handles. All failures surface as [RuntimeException] from the
 * native layer - every method below is annotated accordingly.
 *
 * **voting-5.0.0 round-driver port note:** this interface used to expose ~45 granular per-step
 * methods (build a PCZT, extract a sighash, build a vote commitment, store a tx hash, ...). The
 * SDK's own `RoundExecutor`/`RoundDriver`/`DelegationPipeline` now owns that sequencing
 * internally (see [openRoundSession]/[VotingRoundSession.run]) — every one of those old methods
 * was confirmed (real compile errors against the ported SDK, not inferred) to have no surviving
 * equivalent, and was deleted rather than shimmed. Only DB lifecycle, bundle/notes setup, and a
 * handful of standalone crypto helpers survive unchanged from the pre-4.0 interface.
 */
@Suppress("TooManyFunctions")
interface VotingCryptoClient {
    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun openVotingDb(dbPath: String): Long

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun closeVotingDb(dbHandle: Long)

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun setWalletId(
        dbHandle: Long,
        walletId: String,
        networkId: Int
    )

    /**
     * Bootstraps (or validates) [roundId]'s round row — replaces the pre-4.0 `initializeRound`.
     * Required before [setupBundles] or a delegation-enabled [openRoundSession]/
     * [VotingRoundSession.run] can do anything for a round that has never been through this call
     * before. See [VotingRoundSession.run]'s SDK-side doc comment for why neither call alone can
     * bootstrap a virgin round.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun ensureRound(
        dbHandle: Long,
        roundId: String,
        anchorTreeStateBytes: ByteArray,
        snapshotHeight: Long,
        eaPk: ByteArray,
        ncRoot: ByteArray,
        nullifierImtRoot: ByteArray
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo?

    /**
     * Clears unsigned/unproved delegation setup for this round (preserving submitted bundles and
     * bundles with a persisted Keystone signature) so an interrupted or corrupted per-bundle setup
     * can be rebuilt from scratch.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun resetVotingSessionState(
        dbHandle: Long,
        roundId: String
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun listRoundsJson(dbHandle: Long): String

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getBundleCount(
        dbHandle: Long,
        roundId: String
    ): Int

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun clearRound(
        dbHandle: Long,
        roundId: String
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun deleteSkippedBundles(
        dbHandle: Long,
        roundId: String,
        keepCount: Int
    ): Long

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun setupBundles(
        dbHandle: Long,
        roundId: String,
        notesJson: String
    ): VotingBundleSetupResult

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun computeBundleSetup(notesJson: String): VotingBundleSetupResult

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun generateHotkey(
        dbHandle: Long,
        storedSecret: ByteArray
    ): VotingHotkey

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getWalletNotesJson(
        walletDbPath: String,
        snapshotHeight: Long,
        networkId: Int,
        accountUuidBytes: ByteArray
    ): String

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun deriveHotkeyRawAddress(
        hotkeySeed: ByteArray,
        networkId: Int
    ): ByteArray

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun precomputeDelegationPir(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String
    ): VotingDelegationPirPrecomputeResult

    /**
     * Warms the bundle- and round-independent PIR proof cache for [notesJson]'s nullifiers, so a
     * later [precomputeDelegationPir]/[precomputeSnapshotBundles] call (or vote construction)
     * finds proofs already cached instead of paying PIR latency synchronously. Not scoped to a
     * round or bundle -- safe to call as a background pre-warming step before any round is
     * selected, whenever wallet notes are available.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun precomputePirProofs(
        dbHandle: Long,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String
    ): VotingPirWarmupResult

    /**
     * Persists (or validates) [roundId]'s canonical bundle plan for [notesJson] and warms PIR for
     * every bundle in that plan -- the whole-round background pre-warming entry point,
     * complementing [precomputePirProofs] (round-independent, no bundle layout) and
     * [precomputeDelegationPir] (one already-persisted bundle at a time). Verified strict
     * superset of [precomputeDelegationPir] -- prefer this over per-bundle calls whenever the
     * round's full snapshot note set is available.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun precomputeSnapshotBundles(
        dbHandle: Long,
        roundId: String,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String
    ): VotingSnapshotBundlePrecomputeResult

    /**
     * Opens a round-driver session: binds a `RoundExecutor` to [roundId]'s roster and hotkey,
     * wires its chain-submission and helper transports through [torRuntime]. Callers must
     * [VotingRoundSession.close] it when done.
     *
     * Returns the raw SDK session type directly — the round-driver's plan/ballot-intent/run
     * surface is new with this port and has no pre-4.0 app-level equivalent to preserve, so this
     * deliberately does not wrap it in another app-side abstraction layer (see this interface's
     * class doc for why the old per-step methods below it were deleted rather than shimmed).
     *
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Suppress("LongParameterList")
    @Throws(RuntimeException::class)
    suspend fun openRoundSession(
        dbHandle: Long,
        torRuntime: Long,
        roundId: String,
        proposals: List<VotingProposalRosterEntry>,
        hotkeySecret: ByteArray?,
        chainEndpoints: List<String>,
        operationEpoch: Long,
        configuredHelperUrls: List<String>,
        voteTreeNodeUrls: List<String>,
        ceremonyStartSeconds: Long?,
        voteEndTimeSeconds: Long?
    ): VotingRoundSession

    /**
     * Atomically persists a batch of Keystone-signed delegation bundle signatures for [roundId],
     * so a later [resetVotingSessionState] preserves those bundles instead of wiping their
     * unsigned setup for a rebuild. VotingDb-scoped rather than round-session-scoped: it can be
     * called independently of whether a [VotingRoundSession] is currently open.
     *
     * Pass `sig`/`sighash`/`rk` produced by a prior
     * [VotingRoundSession.getKeystoneSigningRequests]-driven signing flow, not arbitrary values —
     * see [VotingDbSession.storeKeystoneSignatures]'s own doc comment.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun storeKeystoneSignatures(
        dbHandle: Long,
        roundId: String,
        signatures: List<VotingKeystoneSignatureInput>
    ): VotingKeystoneSignatureBatchResult

    /**
     * Opens a cancellable share-tracking session for [roundId]. See
     * [VotingDbSession.openShareTrackingSession]'s doc comment.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun openShareTrackingSession(
        dbHandle: Long,
        roundId: String
    ): VotingShareTrackingSession

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun computeShareNullifier(
        voteCommitment: ByteArray,
        shareIndex: Int,
        blind: ByteArray
    ): ByteArray

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun syncVoteTree(
        dbHandle: Long,
        roundId: String,
        nodeUrl: String
    ): Long

    /**
     * Computes when a delegated helper share should submit, honoring the ceremony's
     * last-moment buffer window. Sources its own entropy natively; callers must not
     * reimplement this scheduling in Kotlin. Returns unix seconds; `0` means "submit
     * immediately".
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun scheduledShareSubmitAt(
        nowSeconds: Long,
        ceremonyStartSeconds: Long,
        voteEndTimeSeconds: Long,
        singleShare: Boolean
    ): Long

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun warmProvingCaches()

    /**
     * Fixes the process-wide proving-pool policy once at startup, before any voting round work.
     * Must be called exactly once, before the first [warmProvingCaches]/round-session call — see
     * [VotingSdk.configureVoting]'s doc comment for why.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun configureVoting()

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun ballotDivisorZatoshi(): Long

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun verifyWitness(witness: VotingWitness): Boolean

    /**
     * Extracts the 32-byte ZIP-244 shielded sighash from finalized PCZT bytes. Stateless — needs
     * neither a DB handle nor an open round session. Used by the Keystone signing flow to recover
     * the sighash a hardware wallet actually signed over, once the signed PCZT is scanned back.
     * @throws RuntimeException if [pcztBytes] is not a parseable PCZT.
     */
    @Throws(RuntimeException::class)
    suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray

    /**
     * Extracts the 64-byte RedPallas spend-authorization signature from a Keystone-signed PCZT.
     * Stateless, like [extractPcztSighash]. [actionIndex] is the expected action index; the
     * backend tries it first and otherwise scans every action, which stays unambiguous because a
     * governance PCZT has exactly one signable action.
     * @throws RuntimeException if [signedPcztBytes] is not a parseable PCZT or carries no signed
     * action.
     */
    @Throws(RuntimeException::class)
    suspend fun extractSpendAuthSig(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): ByteArray
}

class VotingCryptoClientImpl : VotingCryptoClient {
    private val nextDbHandle = AtomicLong(1)
    private val sdkMutex = Mutex()
    private var sdk: VotingSdk? = null
    private val dbPaths = mutableMapOf<Long, String>()
    private val sessions = mutableMapOf<Long, VotingDbSession>()

    private suspend fun votingSdk(): VotingSdk =
        sdk ?: sdkMutex.withLock {
            sdk ?: VotingSdk.new().also {
                // configureVoting() fixes the process-wide proving-pool policy and must run
                // exactly once, before any warmProvingCaches()/round-session call -- see its
                // own doc comment. Every native call in this class funnels through votingSdk(),
                // so doing it here, still under sdkMutex right after construction and before
                // the new instance is published to `sdk`, is the one place that can guarantee
                // "before the first real call" for every caller (openRoundSession,
                // warmProvingCaches, precomputeDelegationPir, ...) without each of them having
                // to remember to call it themselves.
                it.configureVoting()
                sdk = it
            }
        }

    private fun session(dbHandle: Long): VotingDbSession =
        checkNotNull(sessions[dbHandle]) {
            "Voting DB handle is not open: $dbHandle"
        }

    override suspend fun openVotingDb(dbPath: String): Long {
        val handle = nextDbHandle.getAndIncrement()
        dbPaths[handle] = dbPath
        return handle
    }

    override suspend fun closeVotingDb(dbHandle: Long) {
        withContext(Dispatchers.IO) {
            sessions.remove(dbHandle)?.close()
            dbPaths.remove(dbHandle)
        }
    }

    override suspend fun setWalletId(
        dbHandle: Long,
        walletId: String,
        networkId: Int
    ) =
        withContext(Dispatchers.IO) {
            val dbPath =
                checkNotNull(dbPaths[dbHandle]) {
                    "Voting DB handle is not registered: $dbHandle"
                }
            sessions.remove(dbHandle)?.close()
            sessions[dbHandle] = votingSdk().openDb(dbPath, walletId, networkId)
        }

    override suspend fun ensureRound(
        dbHandle: Long,
        roundId: String,
        anchorTreeStateBytes: ByteArray,
        snapshotHeight: Long,
        eaPk: ByteArray,
        ncRoot: ByteArray,
        nullifierImtRoot: ByteArray
    ) = withContext(Dispatchers.IO) {
        session(dbHandle).ensureRound(roundId, anchorTreeStateBytes, snapshotHeight, eaPk, ncRoot, nullifierImtRoot)
    }

    override suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo? =
        withContext(Dispatchers.IO) {
            session(dbHandle).getRoundState(roundId)?.toAppModel()
        }

    override suspend fun resetVotingSessionState(
        dbHandle: Long,
        roundId: String
    ) = withContext(Dispatchers.IO) {
        session(dbHandle).resetVotingSessionState(roundId)
    }

    override suspend fun listRoundsJson(dbHandle: Long): String =
        withContext(Dispatchers.IO) {
            JSONArray()
                .apply {
                    session(dbHandle).listRounds().forEach { round ->
                        put(
                            JSONObject()
                                .put("round_id", round.roundId)
                                .put("phase", round.phase.toWireInt())
                                .put("snapshot_height", round.snapshotHeight)
                                .put("created_at", round.createdAt)
                        )
                    }
                }.toString()
        }

    override suspend fun getBundleCount(
        dbHandle: Long,
        roundId: String
    ): Int =
        withContext(Dispatchers.IO) {
            session(dbHandle).getBundleCount(roundId)
        }

    override suspend fun clearRound(
        dbHandle: Long,
        roundId: String
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).clearRound(roundId)
        }

    override suspend fun deleteSkippedBundles(
        dbHandle: Long,
        roundId: String,
        keepCount: Int
    ): Long =
        withContext(Dispatchers.IO) {
            session(dbHandle).deleteSkippedBundles(roundId, keepCount)
        }

    override suspend fun setupBundles(
        dbHandle: Long,
        roundId: String,
        notesJson: String
    ): VotingBundleSetupResult =
        withContext(Dispatchers.IO) {
            session(dbHandle).setupBundles(roundId, notesJson.toVotingNoteInfos()).toAppModel()
        }

    override suspend fun computeBundleSetup(notesJson: String): VotingBundleSetupResult =
        withContext(Dispatchers.IO) {
            votingSdk().computeBundleSetup(notesJson.toVotingNoteInfos()).toAppModel()
        }

    override suspend fun generateHotkey(
        dbHandle: Long,
        storedSecret: ByteArray
    ): VotingHotkey =
        withContext(Dispatchers.IO) {
            session(dbHandle).generateHotkey(storedSecret).toAppModel()
        }

    override suspend fun getWalletNotesJson(
        walletDbPath: String,
        snapshotHeight: Long,
        networkId: Int,
        accountUuidBytes: ByteArray
    ): String =
        withContext(Dispatchers.IO) {
            votingSdk()
                .getWalletNotes(
                    walletDbPath,
                    BlockHeight.new(snapshotHeight),
                    networkId,
                    AccountUuid.new(accountUuidBytes)
                ).toNotesJson()
        }

    override suspend fun deriveHotkeyRawAddress(
        hotkeySeed: ByteArray,
        networkId: Int
    ): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().deriveHotkeyRawAddress(hotkeySeed, networkId)
        }

    override suspend fun precomputeDelegationPir(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String
    ): VotingDelegationPirPrecomputeResult =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .precomputeDelegationPir(
                    roundId,
                    bundleIndex,
                    pirServerUrl,
                    pirLayout.pirDepth,
                    pirLayout.tier0Layers,
                    pirLayout.tier1Layers,
                    pirLayout.requireKnownPolyLen().polyLen,
                    notesJson.toVotingNoteInfos()
                ).toAppModel()
        }

    override suspend fun precomputePirProofs(
        dbHandle: Long,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String
    ): VotingPirWarmupResult =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .precomputePirProofs(
                    pirServerUrl,
                    pirLayout.pirDepth,
                    pirLayout.tier0Layers,
                    pirLayout.tier1Layers,
                    pirLayout.requireKnownPolyLen().polyLen,
                    notesJson.toVotingNoteInfos()
                ).toAppModel()
        }

    override suspend fun precomputeSnapshotBundles(
        dbHandle: Long,
        roundId: String,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String
    ): VotingSnapshotBundlePrecomputeResult =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .precomputeSnapshotBundles(
                    roundId,
                    pirServerUrl,
                    pirLayout.pirDepth,
                    pirLayout.tier0Layers,
                    pirLayout.tier1Layers,
                    pirLayout.requireKnownPolyLen().polyLen,
                    notesJson.toVotingNoteInfos()
                ).toAppModel()
        }

    override suspend fun openRoundSession(
        dbHandle: Long,
        torRuntime: Long,
        roundId: String,
        proposals: List<VotingProposalRosterEntry>,
        hotkeySecret: ByteArray?,
        chainEndpoints: List<String>,
        operationEpoch: Long,
        configuredHelperUrls: List<String>,
        voteTreeNodeUrls: List<String>,
        ceremonyStartSeconds: Long?,
        voteEndTimeSeconds: Long?
    ): VotingRoundSession =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .openRoundSession(
                    torRuntime,
                    roundId,
                    proposals,
                    hotkeySecret,
                    chainEndpoints,
                    operationEpoch,
                    configuredHelperUrls,
                    voteTreeNodeUrls,
                    ceremonyStartSeconds,
                    voteEndTimeSeconds
                )
        }

    override suspend fun storeKeystoneSignatures(
        dbHandle: Long,
        roundId: String,
        signatures: List<VotingKeystoneSignatureInput>
    ): VotingKeystoneSignatureBatchResult =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeKeystoneSignatures(roundId, signatures)
        }

    override suspend fun openShareTrackingSession(
        dbHandle: Long,
        roundId: String
    ): VotingShareTrackingSession =
        withContext(Dispatchers.IO) {
            session(dbHandle).openShareTrackingSession(roundId)
        }

    override suspend fun computeShareNullifier(
        voteCommitment: ByteArray,
        shareIndex: Int,
        blind: ByteArray
    ): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().computeShareNullifier(voteCommitment, shareIndex, blind)
        }

    override suspend fun syncVoteTree(
        dbHandle: Long,
        roundId: String,
        nodeUrl: String
    ): Long =
        withContext(Dispatchers.IO) {
            session(dbHandle).syncVoteTree(roundId, nodeUrl)
        }

    override suspend fun scheduledShareSubmitAt(
        nowSeconds: Long,
        ceremonyStartSeconds: Long,
        voteEndTimeSeconds: Long,
        singleShare: Boolean
    ): Long =
        withContext(Dispatchers.IO) {
            votingSdk().scheduledShareSubmitAt(nowSeconds, ceremonyStartSeconds, voteEndTimeSeconds, singleShare)
        }

    override suspend fun warmProvingCaches() =
        withContext(Dispatchers.IO) {
            votingSdk().warmProvingCaches()
        }

    override suspend fun configureVoting() =
        withContext(Dispatchers.IO) {
            votingSdk().configureVoting()
        }

    override suspend fun ballotDivisorZatoshi(): Long = BALLOT_DIVISOR_ZATOSHI

    override suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().extractOrchardFvkFromUfvk(ufvk, networkId)
        }

    override suspend fun verifyWitness(witness: VotingWitness): Boolean =
        withContext(Dispatchers.IO) {
            votingSdk().verifyWitness(witness)
        }

    override suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().extractPcztSighash(pcztBytes)
        }

    override suspend fun extractSpendAuthSig(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().extractSpendAuthSig(signedPcztBytes, actionIndex)
        }
}

private fun SdkVotingBundleSetupResult.toAppModel() =
    VotingBundleSetupResult(
        bundleCount = bundleCount,
        eligibleWeight = eligibleWeight,
        bundleWeights = bundleWeights
    )

private fun SdkVotingHotkey.toAppModel() =
    VotingHotkey(
        rawAddress = rawAddress.copyOf(),
        address = address
    )

private fun VotingRoundState.toAppModel() =
    RoundStateInfo(
        roundId = roundId,
        phase = phase.toAppModel(),
        snapshotHeight = snapshotHeight,
        hotkeyAddress = hotkeyAddress,
        delegatedWeight = delegatedWeight,
        proofGenerated = proofGenerated
    )

private fun VotingRoundPhase.toAppModel() =
    when (this) {
        VotingRoundPhase.INITIALIZED -> RoundPhase.INITIALIZED
        VotingRoundPhase.HOTKEY_GENERATED -> RoundPhase.HOTKEY
        VotingRoundPhase.DELEGATION_CONSTRUCTED -> RoundPhase.DELEGATION
        VotingRoundPhase.DELEGATION_PROVED -> RoundPhase.PROVED
        VotingRoundPhase.VOTE_READY -> RoundPhase.VOTE_READY
    }

// Must match the crate's PHASE_* wire constants, preserved here only for listRoundsJson's
// on-the-wire JSON shape.
private const val WIRE_ROUND_PHASE_INITIALIZED = 0
private const val WIRE_ROUND_PHASE_HOTKEY_GENERATED = 1
private const val WIRE_ROUND_PHASE_DELEGATION_CONSTRUCTED = 2
private const val WIRE_ROUND_PHASE_DELEGATION_PROVED = 3
private const val WIRE_ROUND_PHASE_VOTE_READY = 4

private fun VotingRoundPhase.toWireInt(): Int =
    when (this) {
        VotingRoundPhase.INITIALIZED -> WIRE_ROUND_PHASE_INITIALIZED
        VotingRoundPhase.HOTKEY_GENERATED -> WIRE_ROUND_PHASE_HOTKEY_GENERATED
        VotingRoundPhase.DELEGATION_CONSTRUCTED -> WIRE_ROUND_PHASE_DELEGATION_CONSTRUCTED
        VotingRoundPhase.DELEGATION_PROVED -> WIRE_ROUND_PHASE_DELEGATION_PROVED
        VotingRoundPhase.VOTE_READY -> WIRE_ROUND_PHASE_VOTE_READY
    }

private const val BALLOT_DIVISOR_ZATOSHI = 12_500_000L

private fun SdkVotingDelegationPirPrecomputeResult.toAppModel() =
    VotingDelegationPirPrecomputeResult(
        cachedCount = cachedCount,
        fetchedCount = fetchedCount
    )

private fun SdkVotingPirPrecomputeResult.toAppModel() =
    VotingPirWarmupResult(
        cachedCount = cachedCount,
        fetchedCount = fetchedCount,
        servedRoot = servedRoot.copyOf()
    )

private fun SdkVotingSnapshotBundlePrecomputeReport.toAppModel() =
    VotingSnapshotBundlePrecomputeResult(
        bundleCount = layout.bundleCount,
        eligibleWeight = layout.eligibleWeightZatoshi,
        bundleReports =
            bundles.map { report ->
                VotingDelegationPirPrecomputeResult(
                    cachedCount = report.cachedCount,
                    fetchedCount = report.fetchedCount
                )
            }
    )

private const val HEX_BYTE_CHARS = 2
private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff

private fun String.toVotingNoteInfos(): List<VotingNoteInfo> {
    val notes = JSONArray(this)
    return buildList {
        for (index in 0 until notes.length()) {
            val note = notes.getJSONObject(index)
            add(
                VotingNoteInfo(
                    commitment = note.getString("commitment").hexStringToBytes(),
                    nullifier = note.getString("nullifier").hexStringToBytes(),
                    value = note.getLong("value"),
                    position = note.getLong("position"),
                    diversifier = note.getString("diversifier").hexStringToBytes(),
                    rho = note.getString("rho").hexStringToBytes(),
                    rseed = note.getString("rseed").hexStringToBytes(),
                    scope = note.getInt("scope").toVotingNoteScope(),
                    ufvk = note.getString("ufvk")
                )
            )
        }
    }
}

private fun Int.toVotingNoteScope(): VotingNoteScope =
    if (this == 0) VotingNoteScope.EXTERNAL else VotingNoteScope.INTERNAL

private fun VotingNoteScope.toWireInt(): Int =
    if (this == VotingNoteScope.EXTERNAL) 0 else 1

private fun List<VotingNoteInfo>.toNotesJson(): String =
    JSONArray(
        map { note ->
            JSONObject()
                .put("commitment", note.commitment.toHexString())
                .put("nullifier", note.nullifier.toHexString())
                .put("value", note.value)
                .put("position", note.position)
                .put("diversifier", note.diversifier.toHexString())
                .put("rho", note.rho.toHexString())
                .put("rseed", note.rseed.toHexString())
                .put("scope", note.scope.toWireInt())
                .put("ufvk", note.ufvk)
        }
    ).toString()

private fun String.hexStringToBytes(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    return chunked(HEX_BYTE_CHARS)
        .map { chunk -> chunk.toInt(HEX_RADIX).toByte() }
        .toByteArray()
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
