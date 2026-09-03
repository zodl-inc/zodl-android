@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.VotingDbSession
import cash.z.ecc.android.sdk.VotingSdk
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.voting.VotingCommitResult
import cash.z.ecc.android.sdk.model.voting.VotingCommitmentResult
import cash.z.ecc.android.sdk.model.voting.VotingDelegationPhase
import cash.z.ecc.android.sdk.model.voting.VotingDelegationProofResult
import cash.z.ecc.android.sdk.model.voting.VotingDelegationSubmissionResult
import cash.z.ecc.android.sdk.model.voting.VotingEncryptedShare
import cash.z.ecc.android.sdk.model.voting.VotingNoteInfo
import cash.z.ecc.android.sdk.model.voting.VotingNoteScope
import cash.z.ecc.android.sdk.model.voting.VotingRoundPhase
import cash.z.ecc.android.sdk.model.voting.VotingRoundState
import cash.z.ecc.android.sdk.model.voting.VotingSharePayload
import cash.z.ecc.android.sdk.model.voting.VotingVanWitness
import cash.z.ecc.android.sdk.model.voting.VotingWitness
import co.electriccoin.zcash.ui.common.model.voting.BundleDelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundStateInfo
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingCommitmentBundleRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingCommittedVoteRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationPirPrecomputeResult
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationProof
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationSubmission
import co.electriccoin.zcash.ui.common.model.voting.VotingGovernancePczt
import co.electriccoin.zcash.ui.common.model.voting.VotingHotkey
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingShareDelegationRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteCommitment
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteRecord
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import co.electriccoin.zcash.ui.common.model.voting.toVoteCommitmentBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import cash.z.ecc.android.sdk.model.voting.VotingBundleSetupResult as SdkVotingBundleSetupResult
import cash.z.ecc.android.sdk.model.voting.VotingCommitmentBundleRecord as SdkVotingCommitmentBundleRecord
import cash.z.ecc.android.sdk.model.voting.VotingCommittedVoteRecord as SdkVotingCommittedVoteRecord
import cash.z.ecc.android.sdk.model.voting.VotingDelegationPirPrecomputeResult as SdkVotingDelegationPirPrecomputeResult
import cash.z.ecc.android.sdk.model.voting.VotingGovernancePczt as SdkVotingGovernancePczt
import cash.z.ecc.android.sdk.model.voting.VotingHotkey as SdkVotingHotkey
import cash.z.ecc.android.sdk.model.voting.VotingShareDelegationRecord as SdkVotingShareDelegationRecord
import cash.z.ecc.android.sdk.model.voting.VotingTxHashLookup as SdkVotingTxHashLookup
import cash.z.ecc.android.sdk.model.voting.VotingVoteRecord as SdkVotingVoteRecord

/**
 * Kotlin surface over the voting-crypto backend, delegating to the public [VotingSdk] and its
 * per-round [VotingDbSession] handles. All failures surface as [RuntimeException] from the
 * native layer - every method below is annotated accordingly.
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

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun initializeRound(
        dbHandle: Long,
        roundId: String,
        snapshotHeight: Long,
        eaPK: ByteArray,
        ncRoot: ByteArray,
        nullifierIMTRoot: ByteArray,
        sessionJson: String?
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo?

    /**
     * The canonical, per-bundle delegation phase for every bundle in the round. Callers deciding
     * whether to (re)construct/prove/submit a specific bundle must use this, not [getRoundState]'s
     * round-level phase — see [BundleDelegationPhase].
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun delegationPhases(
        dbHandle: Long,
        roundId: String
    ): List<BundleDelegationPhase>

    /**
     * Clears unsigned/unproved delegation setup for this round (preserving submitted bundles and
     * bundles with a persisted Keystone signature) so an interrupted or corrupted per-bundle setup
     * can be rebuilt from scratch. Only call in response to a delegation-setup-overwrite error
     * (see [isDelegationSetupOverwrite]), not routinely.
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
    suspend fun getVotes(
        dbHandle: Long,
        roundId: String
    ): List<VotingVoteRecord>

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
    suspend fun storeTreeState(
        dbHandle: Long,
        roundId: String,
        treeStateBytes: ByteArray
    )

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
    suspend fun generateNoteWitnessesJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        networkId: Int,
        notesJson: String
    ): String

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun storeWitnesses(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        notesJson: String,
        witnessesJson: String
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun buildGovernancePczt(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        fvkBytes: ByteArray,
        hotkeySeed: ByteArray,
        accountIndex: Int,
        notesJson: String,
        seedFingerprint: ByteArray,
        roundName: String
    ): VotingGovernancePczt

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun buildGovernancePcztFromSeed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        ufvk: String,
        networkId: Int,
        accountIndex: Int,
        notesJson: String,
        walletSeed: ByteArray,
        hotkeySeed: ByteArray,
        seedFingerprint: ByteArray,
        roundName: String
    ): VotingGovernancePczt

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun extractSpendAuthSignatureFromSignedPczt(
        signedPcztBytes: ByteArray,
        actionIndex: Int
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

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun buildAndProveDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String,
        fvkBytes: ByteArray,
        hotkeySeed: ByteArray,
        seedFingerprint: ByteArray,
        accountIndex: Int,
        roundName: String,
        proofProgress: ((Double) -> Unit)? = null
    ): VotingDelegationProof

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getDelegationSubmission(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        accountUuid: String,
        hotkeySeed: ByteArray,
        roundName: String,
        senderSeed: ByteArray
    ): VotingDelegationSubmission

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getDelegationSubmissionWithKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray
    ): VotingDelegationSubmission

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun storeDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        txHash: String
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): VotingTxHashLookup

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun storeVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        txHash: String
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingTxHashLookup

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommitmentBundleRecord?

    /**
     * Records the confirmed vote-commitment-tree position for an already-committed vote, once
     * its cast-vote transaction has been mined.
     *
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun recordVcPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        vcTreePosition: Long
    )

    /**
     * Recovers the signed `vote::commit` result for an already-committed vote, together with
     * its confirmed vote-commitment-tree position recorded by [recordVcPosition].
     *
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun recoverCommittedVote(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommittedVoteRecord

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun clearRecoveryState(
        dbHandle: Long,
        roundId: String
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun recordShareDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        sentToUrls: List<String>,
        nullifier: ByteArray,
        submitAt: Long
    )

    /**
     * Persists a Keystone-signed delegation bundle's signature so a later round-wide
     * [resetVotingSessionState] preserves this bundle instead of wiping its unsigned setup
     * fields for a rebuild. Pass the `rk`/`sighash` the app already verified the signature
     * against (the crate-computed values from the governance PCZT that was signed), not
     * arbitrary caller-supplied values — this call does not itself re-verify the signature.
     * @throws RuntimeException if the native layer reports a failure.
     */
    @Throws(RuntimeException::class)
    suspend fun storeKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray,
        rk: ByteArray
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun getShareDelegations(
        dbHandle: Long,
        roundId: String
    ): List<VotingShareDelegationRecord>

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun markShareConfirmed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun addSentServers(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        newUrls: List<String>
    )

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

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun storeVanPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        position: Int
    )

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun generateVanWitnessJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        anchorHeight: Int
    ): String

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun buildVoteCommitment(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        hotkeySeed: ByteArray,
        proposalId: Int,
        choice: Int,
        numOptions: Int,
        witnessJson: String,
        vanPosition: Int,
        anchorHeight: Int,
        singleShare: Boolean = false,
        proofProgress: ((Double) -> Unit)? = null
    ): VotingVoteCommitment

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun buildSharePayloadsJson(
        encSharesJson: String,
        commitmentJson: String,
        voteDecision: Int,
        numOptions: Int,
        vcTreePosition: Long,
        singleShareMode: Boolean = false
    ): String

    /**
     * Computes when a delegated helper share should submit, honoring the ceremony's
     * last-moment buffer window. Sources its own entropy natively; callers must not
     * reimplement this scheduling in Kotlin. Returns unix seconds; `0` means "submit
     * immediately".
     *
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

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun ballotDivisorZatoshi(): Long

    /** @throws RuntimeException if the native layer reports a failure. */
    @Throws(RuntimeException::class)
    suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
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
            sdk ?: VotingSdk.new().also { sdk = it }
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

    override suspend fun initializeRound(
        dbHandle: Long,
        roundId: String,
        snapshotHeight: Long,
        eaPK: ByteArray,
        ncRoot: ByteArray,
        nullifierIMTRoot: ByteArray,
        sessionJson: String?
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).initRound(roundId, snapshotHeight, eaPK, ncRoot, nullifierIMTRoot, sessionJson)
        }

    override suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo? =
        withContext(Dispatchers.IO) {
            session(dbHandle).getRoundState(roundId)?.toAppModel()
        }

    override suspend fun delegationPhases(
        dbHandle: Long,
        roundId: String
    ): List<BundleDelegationPhase> =
        withContext(Dispatchers.IO) {
            session(dbHandle).delegationPhases(roundId).map(VotingDelegationPhase::toAppModel)
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

    override suspend fun getVotes(
        dbHandle: Long,
        roundId: String
    ): List<VotingVoteRecord> =
        withContext(Dispatchers.IO) {
            session(dbHandle).getVotes(roundId).map(SdkVotingVoteRecord::toAppModel)
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

    override suspend fun storeTreeState(
        dbHandle: Long,
        roundId: String,
        treeStateBytes: ByteArray
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeTreeState(roundId, treeStateBytes)
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

    override suspend fun generateNoteWitnessesJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        networkId: Int,
        notesJson: String
    ): String =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .generateNoteWitnesses(roundId, bundleIndex, walletDbPath, networkId, notesJson.toVotingNoteInfos())
                .toWitnessesJson()
        }

    override suspend fun storeWitnesses(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        notesJson: String,
        witnessesJson: String
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeWitnesses(
                roundId,
                bundleIndex,
                notesJson.toVotingNoteInfos(),
                witnessesJson.toVotingWitnesses()
            )
        }

    override suspend fun buildGovernancePczt(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        fvkBytes: ByteArray,
        hotkeySeed: ByteArray,
        accountIndex: Int,
        notesJson: String,
        seedFingerprint: ByteArray,
        roundName: String
    ): VotingGovernancePczt =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .buildGovernancePczt(
                    roundId,
                    bundleIndex,
                    fvkBytes,
                    hotkeySeed,
                    accountIndex,
                    notesJson.toVotingNoteInfos(),
                    seedFingerprint,
                    roundName
                ).toAppModel()
        }

    override suspend fun buildGovernancePcztFromSeed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        ufvk: String,
        networkId: Int,
        accountIndex: Int,
        notesJson: String,
        walletSeed: ByteArray,
        hotkeySeed: ByteArray,
        seedFingerprint: ByteArray,
        roundName: String
    ): VotingGovernancePczt =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .buildGovernancePcztFromSeed(
                    roundId,
                    bundleIndex,
                    ufvk,
                    networkId,
                    accountIndex,
                    notesJson.toVotingNoteInfos(),
                    walletSeed,
                    hotkeySeed,
                    seedFingerprint,
                    roundName
                ).toAppModel()
        }

    override suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().extractPcztSighash(pcztBytes)
        }

    override suspend fun extractSpendAuthSignatureFromSignedPczt(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().extractSpendAuthSig(signedPcztBytes, actionIndex)
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

    override suspend fun buildAndProveDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        pirLayout: VotingPirLayout,
        notesJson: String,
        fvkBytes: ByteArray,
        hotkeySeed: ByteArray,
        seedFingerprint: ByteArray,
        accountIndex: Int,
        roundName: String,
        proofProgress: ((Double) -> Unit)?
    ): VotingDelegationProof =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .buildAndProveDelegation(
                    roundId,
                    bundleIndex,
                    pirServerUrl,
                    pirLayout.pirDepth,
                    pirLayout.tier0Layers,
                    pirLayout.tier1Layers,
                    pirLayout.requireKnownPolyLen().polyLen,
                    notesJson.toVotingNoteInfos(),
                    fvkBytes,
                    hotkeySeed,
                    seedFingerprint,
                    accountIndex,
                    roundName,
                    proofProgress
                ).toAppModel()
        }

    override suspend fun getDelegationSubmission(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        accountUuid: String,
        hotkeySeed: ByteArray,
        roundName: String,
        senderSeed: ByteArray
    ): VotingDelegationSubmission =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .getDelegationSubmission(
                    roundId,
                    bundleIndex,
                    walletDbPath,
                    accountUuid,
                    hotkeySeed,
                    roundName,
                    senderSeed
                ).toAppModel()
        }

    override suspend fun getDelegationSubmissionWithKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray
    ): VotingDelegationSubmission =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .getDelegationSubmissionWithKeystoneSig(roundId, bundleIndex, keystoneSig, keystoneSighash)
                .toAppModel()
        }

    override suspend fun storeDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        txHash: String
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeDelegationTxHash(roundId, bundleIndex, txHash)
        }

    override suspend fun getDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): VotingTxHashLookup =
        withContext(Dispatchers.IO) {
            runExpectedMissingRowLookup {
                session(dbHandle).getDelegationTxHash(roundId, bundleIndex).toAppModel()
            } ?: VotingTxHashLookup.NotFound
        }

    override suspend fun storeVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        txHash: String
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeVoteTxHash(roundId, bundleIndex, proposalId, txHash)
        }

    override suspend fun getVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingTxHashLookup =
        withContext(Dispatchers.IO) {
            runExpectedMissingRowLookup {
                session(dbHandle).getVoteTxHash(roundId, bundleIndex, proposalId).toAppModel()
            } ?: VotingTxHashLookup.NotFound
        }

    override suspend fun getCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommitmentBundleRecord? =
        withContext(Dispatchers.IO) {
            runExpectedMissingRowLookup {
                session(dbHandle)
                    .getCommitmentBundle(roundId, bundleIndex, proposalId)
                    ?.toAppModel()
            }
        }

    override suspend fun recordVcPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        vcTreePosition: Long
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).recordVcPosition(roundId, bundleIndex, proposalId, vcTreePosition)
        }

    override suspend fun recoverCommittedVote(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommittedVoteRecord =
        withContext(Dispatchers.IO) {
            session(dbHandle).recoverCommittedVote(roundId, bundleIndex, proposalId).toAppModel()
        }

    override suspend fun clearRecoveryState(
        dbHandle: Long,
        roundId: String
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).clearRecoveryState(roundId)
        }

    override suspend fun recordShareDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        sentToUrls: List<String>,
        nullifier: ByteArray,
        submitAt: Long
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).recordShareDelegation(
                roundId,
                bundleIndex,
                proposalId,
                shareIndex,
                sentToUrls,
                nullifier,
                submitAt
            )
        }

    override suspend fun storeKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray,
        rk: ByteArray
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeKeystoneSignature(roundId, bundleIndex, keystoneSig, keystoneSighash, rk)
        }

    override suspend fun getShareDelegations(
        dbHandle: Long,
        roundId: String
    ): List<VotingShareDelegationRecord> =
        withContext(Dispatchers.IO) {
            session(dbHandle).getShareDelegations(roundId).map(SdkVotingShareDelegationRecord::toAppModel)
        }

    override suspend fun markShareConfirmed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).markShareConfirmed(roundId, bundleIndex, proposalId, shareIndex)
        }

    override suspend fun addSentServers(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        newUrls: List<String>
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).addSentServers(roundId, bundleIndex, proposalId, shareIndex, newUrls)
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

    override suspend fun storeVanPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        position: Int
    ) =
        withContext(Dispatchers.IO) {
            session(dbHandle).storeVanPosition(roundId, bundleIndex, position.toLong())
        }

    override suspend fun generateVanWitnessJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        anchorHeight: Int
    ): String =
        withContext(Dispatchers.IO) {
            session(dbHandle).generateVanWitness(roundId, bundleIndex, anchorHeight.toLong()).toJson()
        }

    override suspend fun buildVoteCommitment(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        hotkeySeed: ByteArray,
        proposalId: Int,
        choice: Int,
        numOptions: Int,
        witnessJson: String,
        vanPosition: Int,
        anchorHeight: Int,
        singleShare: Boolean,
        proofProgress: ((Double) -> Unit)?
    ): VotingVoteCommitment =
        withContext(Dispatchers.IO) {
            session(dbHandle)
                .buildVoteCommitment(
                    roundId,
                    bundleIndex,
                    hotkeySeed,
                    proposalId,
                    choice,
                    numOptions,
                    witnessJson.toVotingVanWitness(vanPosition, anchorHeight),
                    singleShare,
                    proofProgress
                ).toAppModel()
        }

    override suspend fun buildSharePayloadsJson(
        encSharesJson: String,
        commitmentJson: String,
        voteDecision: Int,
        numOptions: Int,
        vcTreePosition: Long,
        singleShareMode: Boolean
    ): String =
        withContext(Dispatchers.IO) {
            votingSdk()
                .buildSharePayloads(
                    commitmentJson.toVotingCommitmentResult(encSharesJson.toVotingEncryptedShares()),
                    voteDecision,
                    numOptions,
                    vcTreePosition,
                    singleShareMode
                ).toSharePayloadsJson()
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

    override suspend fun ballotDivisorZatoshi(): Long = BALLOT_DIVISOR_ZATOSHI

    override suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray =
        withContext(Dispatchers.IO) {
            votingSdk().extractOrchardFvkFromUfvk(ufvk, networkId)
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

private fun VotingDelegationPhase.toAppModel() =
    BundleDelegationPhase(
        bundleIndex = bundleIndex,
        phase = DelegationPhase.fromWireValue(phase)
    )

private const val BALLOT_DIVISOR_ZATOSHI = 12_500_000L
private const val HEX_BYTE_CHARS = 2
private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff

private fun SdkVotingGovernancePczt.toAppModel() =
    VotingGovernancePczt(
        pcztBytes = pcztBytes.copyOf(),
        rk = rk.copyOf(),
        sighash = sighash.copyOf(),
        actionIndex = actionIndex
    )

private fun VotingDelegationProofResult.toAppModel() =
    VotingDelegationProof(
        proof = proof.copyOf(),
        publicInputs = publicInputs.map(ByteArray::copyOf),
        nfSigned = nfSigned.copyOf(),
        cmxNew = cmxNew.copyOf(),
        govNullifiers = govNullifiers.map(ByteArray::copyOf),
        vanComm = vanComm.copyOf(),
        rk = rk.copyOf()
    )

private fun SdkVotingDelegationPirPrecomputeResult.toAppModel() =
    VotingDelegationPirPrecomputeResult(
        cachedCount = cachedCount,
        fetchedCount = fetchedCount
    )

private fun VotingDelegationSubmissionResult.toAppModel() =
    VotingDelegationSubmission(
        proof = proof.copyOf(),
        rk = rk.copyOf(),
        spendAuthSig = spendAuthSig.copyOf(),
        sighash = sighash.copyOf(),
        tx1Effects = tx1Effects.copyOf(),
        nfSigned = nfSigned.copyOf(),
        cmxNew = cmxNew.copyOf(),
        govComm = govComm.copyOf(),
        govNullifiers = govNullifiers.map(ByteArray::copyOf),
        alpha = ByteArray(0),
        voteRoundId = voteRoundId
    )

private fun VotingCommitResult.toAppModel() =
    VotingVoteCommitment(
        vanNullifier = vanNullifier.copyOf(),
        voteAuthorityNoteNew = voteAuthorityNoteNew.copyOf(),
        voteCommitment = voteCommitment.copyOf(),
        rVpk = rVpk.copyOf(),
        voteAuthSig = voteAuthSig.copyOf(),
        anchorHeight = anchorHeight.toInt(),
        encSharesJson = encShares.toEncryptedSharesJson(),
        rawBundleJson = toStorageJson()
    )

private fun VotingCommitResult.toStorageJson(): String =
    JSONObject()
        .put("van_nullifier", vanNullifier.toHexString())
        .put("vote_authority_note_new", voteAuthorityNoteNew.toHexString())
        .put("vote_commitment", voteCommitment.toHexString())
        .put("proposal_id", proposalId)
        .put("bundle_index", bundleIndex)
        .put("proof", proof.toHexString())
        .put("enc_shares", encShares.toEncryptedSharesJsonArray())
        .put("anchor_height", anchorHeight)
        .put("vote_round_id", voteRoundId)
        .put("shares_hash", sharesHash.toHexString())
        .put("share_comms", shareComms.toHexJsonArray())
        .put("r_vpk_bytes", rVpk.toHexString())
        .toString()

private fun SdkVotingCommittedVoteRecord.toAppModel() =
    VotingCommittedVoteRecord(
        bundleIndex = commit.bundleIndex,
        proposalId = commit.proposalId,
        vcTreePosition = vcTreePosition,
        sharePayloadsJson = commit.sharePayloads.toSharePayloadsJson()
    )

@Suppress("TooGenericExceptionCaught")
private suspend fun <T> runExpectedMissingRowLookup(block: suspend () -> T): T? =
    try {
        block()
    } catch (exception: RuntimeException) {
        // Recovery lookups are cache probes. Older native layers can surface a
        // missing row as RuntimeException instead of returning null/NotFound.
        if (exception.isQueryReturnedNoRows()) {
            null
        } else {
            throw exception
        }
    }

private fun Throwable.isQueryReturnedNoRows(): Boolean =
    generateSequence(this) { throwable -> throwable.cause }
        .any { throwable ->
            throwable.message
                ?.contains("Query returned no rows", ignoreCase = true) == true
        }

private fun SdkVotingTxHashLookup.toAppModel(): VotingTxHashLookup =
    when (this) {
        is SdkVotingTxHashLookup.Missing -> VotingTxHashLookup.NotFound
        is SdkVotingTxHashLookup.Found -> VotingTxHashLookup.Present(txHash)
    }

private fun SdkVotingCommitmentBundleRecord.toAppModel() =
    VotingCommitmentBundleRecord(
        bundleJson = commitment.toStorageJson(),
        bundle = commitment.toStorageJson().toVoteCommitmentBundle(),
        vcTreePosition = vcTreePosition
    )

private fun SdkVotingVoteRecord.toAppModel() =
    VotingVoteRecord(
        proposalId = proposalId,
        bundleIndex = bundleIndex,
        choice = choice,
        submitted = submitted
    )

private fun SdkVotingShareDelegationRecord.toAppModel() =
    VotingShareDelegationRecord(
        roundId = roundId,
        bundleIndex = bundleIndex,
        proposalId = proposalId,
        shareIndex = shareIndex,
        sentToUrls = sentToUrls,
        nullifier = nullifier.copyOf(),
        confirmed = confirmed,
        submitAt = submitAt,
        createdAt = createdAt
    )

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

private fun String.toVotingWitnesses(): List<VotingWitness> {
    val witnesses = JSONArray(this)
    return buildList {
        for (index in 0 until witnesses.length()) {
            val witness = witnesses.getJSONObject(index)
            add(
                VotingWitness(
                    noteCommitment = witness.getString("note_commitment").hexStringToBytes(),
                    position = witness.getLong("position"),
                    root = witness.getString("root").hexStringToBytes(),
                    authPath = witness.getJSONArray("auth_path").toByteArrays()
                )
            )
        }
    }
}

private fun List<VotingWitness>.toWitnessesJson(): String =
    JSONArray(
        map { witness ->
            JSONObject()
                .put("note_commitment", witness.noteCommitment.toHexString())
                .put("position", witness.position)
                .put("root", witness.root.toHexString())
                .put("auth_path", witness.authPath.toHexJsonArray())
        }
    ).toString()

private fun VotingVanWitness.toJson(): String =
    JSONObject()
        .put("auth_path", authPath.toHexJsonArray())
        .put("position", position)
        .put("anchor_height", anchorHeight)
        .toString()

private fun String.toVotingVanWitness(
    position: Int,
    anchorHeight: Int
): VotingVanWitness {
    val json = JSONObject(this)
    return VotingVanWitness(
        authPath = json.getJSONArray("auth_path").toByteArrays(),
        position = json.optLong("position", position.toLong()),
        anchorHeight = json.optLong("anchor_height", anchorHeight.toLong())
    )
}

private fun String.toVotingCommitmentResult(
    encSharesOverride: List<VotingEncryptedShare>? = null,
    fallbackBundleIndex: Int = 0
): VotingCommitmentResult {
    val json = JSONObject(this)
    return VotingCommitmentResult(
        vanNullifier = json.getString("van_nullifier").hexStringToBytes(),
        voteAuthorityNoteNew = json.getString("vote_authority_note_new").hexStringToBytes(),
        voteCommitment = json.getString("vote_commitment").hexStringToBytes(),
        proposalId = json.getInt("proposal_id"),
        bundleIndex = json.optInt("bundle_index", fallbackBundleIndex),
        proof = json.getString("proof").hexStringToBytes(),
        encShares = encSharesOverride ?: json.optJSONArray("enc_shares").toVotingEncryptedShares(),
        anchorHeight = json.getLong("anchor_height"),
        voteRoundId = json.getString("vote_round_id"),
        sharesHash = json.getString("shares_hash").hexStringToBytes(),
        shareBlinds = json.optJSONArray("share_blinds").toByteArrays(),
        shareComms = json.optJSONArray("share_comms").toByteArrays(),
        rVpk =
            json
                .optString("r_vpk_bytes")
                .takeIf(String::isNotEmpty)
                ?.hexStringToBytes()
                ?: ByteArray(0),
        alphaV =
            json
                .optString("alpha_v")
                .takeIf(String::isNotEmpty)
                ?.hexStringToBytes()
                ?: ByteArray(0)
    )
}

private fun VotingCommitmentResult.toStorageJson(): String =
    JSONObject()
        .put("van_nullifier", vanNullifier.toHexString())
        .put("vote_authority_note_new", voteAuthorityNoteNew.toHexString())
        .put("vote_commitment", voteCommitment.toHexString())
        .put("proposal_id", proposalId)
        .put("bundle_index", bundleIndex)
        .put("proof", proof.toHexString())
        .put("enc_shares", encShares.toEncryptedSharesJsonArray())
        .put("anchor_height", anchorHeight)
        .put("vote_round_id", voteRoundId)
        .put("shares_hash", sharesHash.toHexString())
        .put("share_blinds", shareBlinds.toHexJsonArray())
        .put("share_comms", shareComms.toHexJsonArray())
        .put("r_vpk_bytes", rVpk.toHexString())
        .put("alpha_v", alphaV.toHexString())
        .toString()

private fun String.toVotingEncryptedShares(): List<VotingEncryptedShare> =
    JSONArray(this).toVotingEncryptedShares()

private fun JSONArray?.toVotingEncryptedShares(): List<VotingEncryptedShare> {
    if (this == null) return emptyList()

    return buildList {
        for (index in 0 until length()) {
            val share = getJSONObject(index)
            add(
                VotingEncryptedShare(
                    c1 = share.getString("c1").hexStringToBytes(),
                    c2 = share.getString("c2").hexStringToBytes(),
                    shareIndex = share.getInt("share_index")
                )
            )
        }
    }
}

private fun List<VotingEncryptedShare>.toEncryptedSharesJson(): String =
    toEncryptedSharesJsonArray().toString()

private fun List<VotingEncryptedShare>.toEncryptedSharesJsonArray(): JSONArray =
    JSONArray(map(VotingEncryptedShare::toJson))

private fun VotingEncryptedShare.toJson(): JSONObject =
    JSONObject()
        .put("c1", c1.toHexString())
        .put("c2", c2.toHexString())
        .put("share_index", shareIndex)

private fun List<VotingSharePayload>.toSharePayloadsJson(): String =
    JSONArray(
        map { payload ->
            JSONObject()
                .put("shares_hash", payload.sharesHash.toHexString())
                .put("proposal_id", payload.proposalId)
                .put("vote_decision", payload.voteDecision)
                .put("enc_share", payload.encShare.toJson())
                .put("tree_position", payload.treePosition)
                .put("vote_round_id", payload.voteRoundId)
                .put("all_enc_shares", payload.allEncShares.toEncryptedSharesJsonArray())
                .put("share_comms", payload.shareComms.toHexJsonArray())
                .put("primary_blind", payload.primaryBlind.toHexString())
        }
    ).toString()

private fun JSONArray?.toByteArrays(): List<ByteArray> {
    if (this == null) return emptyList()

    return buildList {
        for (index in 0 until length()) {
            add(getString(index).hexStringToBytes())
        }
    }
}

private fun List<ByteArray>.toHexJsonArray(): JSONArray =
    JSONArray(map(ByteArray::toHexString))

private fun String.hexStringToBytes(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    return chunked(HEX_BYTE_CHARS)
        .map { chunk -> chunk.toInt(HEX_RADIX).toByte() }
        .toByteArray()
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
