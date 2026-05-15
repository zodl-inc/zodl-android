@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.internal.jni.VotingProofProgressCallback
import cash.z.ecc.android.sdk.internal.jni.VotingRustBackend
import cash.z.ecc.android.sdk.internal.model.voting.JniBundleSetupResult
import cash.z.ecc.android.sdk.internal.model.voting.JniCommitmentBundleRecord
import cash.z.ecc.android.sdk.internal.model.voting.JniDelegationPirPrecomputeResult
import cash.z.ecc.android.sdk.internal.model.voting.JniDelegationProofResult
import cash.z.ecc.android.sdk.internal.model.voting.JniDelegationSubmissionResult
import cash.z.ecc.android.sdk.internal.model.voting.JniGovernancePczt
import cash.z.ecc.android.sdk.internal.model.voting.JniNoteInfo
import cash.z.ecc.android.sdk.internal.model.voting.JniRoundPhase
import cash.z.ecc.android.sdk.internal.model.voting.JniRoundState
import cash.z.ecc.android.sdk.internal.model.voting.JniShareDelegationRecord
import cash.z.ecc.android.sdk.internal.model.voting.JniSharePayload
import cash.z.ecc.android.sdk.internal.model.voting.JniVanWitness
import cash.z.ecc.android.sdk.internal.model.voting.JniVoteCommitmentResult
import cash.z.ecc.android.sdk.internal.model.voting.JniVoteRecord
import cash.z.ecc.android.sdk.internal.model.voting.JniVotingHotkey
import cash.z.ecc.android.sdk.internal.model.voting.JniWireEncryptedShare
import cash.z.ecc.android.sdk.internal.model.voting.JniWitnessData
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundStateInfo
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
import co.electriccoin.zcash.ui.common.model.voting.VotingCommitmentBundleRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationPirPrecomputeResult
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationProof
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationSubmission
import co.electriccoin.zcash.ui.common.model.voting.VotingGovernancePczt
import co.electriccoin.zcash.ui.common.model.voting.VotingHotkey
import co.electriccoin.zcash.ui.common.model.voting.VotingShareDelegationRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteCommitment
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteRecord
import co.electriccoin.zcash.ui.common.model.voting.toVoteCommitmentBundle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

@Suppress("TooManyFunctions")
interface VotingCryptoClient {
    suspend fun openVotingDb(dbPath: String): Long

    suspend fun closeVotingDb(dbHandle: Long)

    suspend fun setWalletId(
        dbHandle: Long,
        walletId: String
    )

    suspend fun initializeRound(
        dbHandle: Long,
        roundId: String,
        snapshotHeight: Long,
        eaPK: ByteArray,
        ncRoot: ByteArray,
        nullifierIMTRoot: ByteArray,
        sessionJson: String?
    )

    suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo?

    suspend fun listRoundsJson(dbHandle: Long): String

    suspend fun getBundleCount(
        dbHandle: Long,
        roundId: String
    ): Int

    suspend fun getVotes(
        dbHandle: Long,
        roundId: String
    ): List<VotingVoteRecord>

    suspend fun clearRound(
        dbHandle: Long,
        roundId: String
    )

    suspend fun deleteSkippedBundles(
        dbHandle: Long,
        roundId: String,
        keepCount: Int
    ): Long

    suspend fun setupBundles(
        dbHandle: Long,
        roundId: String,
        notesJson: String
    ): VotingBundleSetupResult

    suspend fun computeBundleSetup(notesJson: String): VotingBundleSetupResult

    suspend fun generateHotkey(
        dbHandle: Long,
        roundId: String,
        seed: ByteArray
    ): VotingHotkey

    suspend fun storeTreeState(
        dbHandle: Long,
        roundId: String,
        treeStateBytes: ByteArray
    )

    suspend fun getWalletNotesJson(
        walletDbPath: String,
        snapshotHeight: Long,
        networkId: Int,
        accountUuidBytes: ByteArray
    ): String

    suspend fun deriveHotkeyRawAddress(
        hotkeySeed: ByteArray,
        networkId: Int
    ): ByteArray

    suspend fun generateNoteWitnessesJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        networkId: Int,
        notesJson: String
    ): String

    suspend fun storeWitnesses(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        notesJson: String,
        witnessesJson: String
    )

    suspend fun buildGovernancePczt(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        fvkBytes: ByteArray,
        hotkeyRawAddress: ByteArray,
        networkId: Int,
        accountIndex: Int,
        notesJson: String,
        seedFingerprint: ByteArray,
        roundName: String
    ): VotingGovernancePczt

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

    suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray

    suspend fun extractSpendAuthSignatureFromSignedPczt(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): ByteArray

    suspend fun precomputeDelegationPir(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        networkId: Int,
        notesJson: String
    ): VotingDelegationPirPrecomputeResult

    suspend fun buildAndProveDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        networkId: Int,
        notesJson: String,
        hotkeyRawAddress: ByteArray,
        proofProgress: ((Double) -> Unit)? = null
    ): VotingDelegationProof

    suspend fun getDelegationSubmission(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        senderSeed: ByteArray,
        networkId: Int,
        accountIndex: Int
    ): VotingDelegationSubmission

    suspend fun getDelegationSubmissionWithKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray
    ): VotingDelegationSubmission

    suspend fun storeDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        txHash: String
    )

    suspend fun getDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): VotingTxHashLookup

    suspend fun storeVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        txHash: String
    )

    suspend fun getVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingTxHashLookup

    suspend fun markVoteSubmitted(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    )

    suspend fun storeCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        bundleJson: String,
        vcTreePosition: Long
    )

    suspend fun getCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommitmentBundleRecord?

    suspend fun clearRecoveryState(
        dbHandle: Long,
        roundId: String
    )

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

    suspend fun getShareDelegations(
        dbHandle: Long,
        roundId: String
    ): List<VotingShareDelegationRecord>

    suspend fun markShareConfirmed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int
    )

    suspend fun addSentServers(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        newUrls: List<String>
    )

    suspend fun computeShareNullifier(
        voteCommitment: ByteArray,
        shareIndex: Int,
        blind: ByteArray
    ): ByteArray

    suspend fun syncVoteTree(
        dbHandle: Long,
        roundId: String,
        nodeUrl: String
    ): Long

    suspend fun storeVanPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        position: Int
    )

    suspend fun generateVanWitnessJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        anchorHeight: Int
    ): String

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
        networkId: Int,
        accountIndex: Int,
        singleShare: Boolean = false,
        proofProgress: ((Double) -> Unit)? = null
    ): VotingVoteCommitment

    suspend fun buildSharePayloadsJson(
        encSharesJson: String,
        commitmentJson: String,
        voteDecision: Int,
        numOptions: Int,
        vcTreePosition: Long,
        singleShareMode: Boolean = false
    ): String

    suspend fun signCastVote(
        hotkeySeed: ByteArray,
        networkId: Int,
        accountIndex: Int,
        commitmentJson: String
    ): ByteArray

    suspend fun warmProvingCaches()

    suspend fun ballotDivisorZatoshi(): Long

    suspend fun decomposeWeight(weight: Long): List<Long>

    suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray
}

class VotingCryptoClientImpl : VotingCryptoClient {
    private val nextDbHandle = AtomicLong(1)
    private val backendMutex = Mutex()
    private var backend: VotingRustBackend? = null
    private val dbPaths = mutableMapOf<Long, String>()
    private val dbs = mutableMapOf<Long, VotingRustBackend.VotingDb>()

    private suspend fun rustBackend(): VotingRustBackend =
        backend ?: backendMutex.withLock {
            backend ?: VotingRustBackend.new().also { backend = it }
        }

    private fun db(dbHandle: Long): VotingRustBackend.VotingDb =
        checkNotNull(dbs[dbHandle]) {
            "Voting DB handle is not open: $dbHandle"
        }

    override suspend fun openVotingDb(dbPath: String): Long {
        val handle = nextDbHandle.getAndIncrement()
        dbPaths[handle] = dbPath
        return handle
    }

    override suspend fun closeVotingDb(dbHandle: Long) {
        dbs.remove(dbHandle)?.close()
        dbPaths.remove(dbHandle)
    }

    override suspend fun setWalletId(
        dbHandle: Long,
        walletId: String
    ) {
        val dbPath =
            checkNotNull(dbPaths[dbHandle]) {
                "Voting DB handle is not registered: $dbHandle"
            }
        dbs.remove(dbHandle)?.close()
        dbs[dbHandle] = rustBackend().openVotingDb(dbPath, walletId)
    }

    override suspend fun initializeRound(
        dbHandle: Long,
        roundId: String,
        snapshotHeight: Long,
        eaPK: ByteArray,
        ncRoot: ByteArray,
        nullifierIMTRoot: ByteArray,
        sessionJson: String?
    ) = db(dbHandle).initRound(roundId, snapshotHeight, eaPK, ncRoot, nullifierIMTRoot, sessionJson)

    override suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo? = db(dbHandle).getRoundState(roundId)?.toAppModel()

    override suspend fun listRoundsJson(dbHandle: Long): String =
        JSONArray()
            .apply {
                db(dbHandle).listRounds().forEach { round ->
                    put(
                        JSONObject()
                            .put("round_id", round.roundId)
                            .put("phase", round.phase)
                            .put("snapshot_height", round.snapshotHeight)
                            .put("created_at", round.createdAt)
                    )
                }
            }.toString()

    override suspend fun getBundleCount(
        dbHandle: Long,
        roundId: String
    ): Int =
        db(dbHandle).getBundleCount(roundId)

    override suspend fun getVotes(
        dbHandle: Long,
        roundId: String
    ): List<VotingVoteRecord> =
        db(dbHandle).getVotes(roundId).map(JniVoteRecord::toAppModel)

    override suspend fun clearRound(
        dbHandle: Long,
        roundId: String
    ) =
        db(dbHandle).clearRound(roundId)

    override suspend fun deleteSkippedBundles(
        dbHandle: Long,
        roundId: String,
        keepCount: Int
    ): Long = db(dbHandle).deleteSkippedBundles(roundId, keepCount)

    override suspend fun setupBundles(
        dbHandle: Long,
        roundId: String,
        notesJson: String
    ): VotingBundleSetupResult =
        db(dbHandle).setupBundles(roundId, notesJson.toJniNoteInfos()).toAppModel()

    override suspend fun computeBundleSetup(notesJson: String): VotingBundleSetupResult =
        rustBackend().computeBundleSetup(notesJson.toJniNoteInfos()).toAppModel()

    override suspend fun generateHotkey(
        dbHandle: Long,
        roundId: String,
        seed: ByteArray
    ): VotingHotkey = db(dbHandle).generateHotkey(roundId, seed).toAppModel()

    override suspend fun storeTreeState(
        dbHandle: Long,
        roundId: String,
        treeStateBytes: ByteArray
    ) = db(dbHandle).storeTreeState(roundId, treeStateBytes)

    override suspend fun getWalletNotesJson(
        walletDbPath: String,
        snapshotHeight: Long,
        networkId: Int,
        accountUuidBytes: ByteArray
    ): String =
        rustBackend()
            .getWalletNotes(walletDbPath, snapshotHeight, networkId, accountUuidBytes)
            .asList()
            .toNotesJson()

    override suspend fun deriveHotkeyRawAddress(
        hotkeySeed: ByteArray,
        networkId: Int
    ): ByteArray =
        rustBackend().deriveHotkeyRawAddress(hotkeySeed, networkId)

    override suspend fun generateNoteWitnessesJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        networkId: Int,
        notesJson: String
    ): String =
        db(dbHandle)
            .generateNoteWitnesses(roundId, bundleIndex, walletDbPath, networkId, notesJson.toJniNoteInfos())
            .asList()
            .toWitnessesJson()

    override suspend fun storeWitnesses(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        notesJson: String,
        witnessesJson: String
    ) = db(dbHandle).storeWitnesses(
        roundId,
        bundleIndex,
        notesJson.toJniNoteInfos(),
        witnessesJson.toJniWitnesses()
    )

    override suspend fun buildGovernancePczt(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        fvkBytes: ByteArray,
        hotkeyRawAddress: ByteArray,
        networkId: Int,
        accountIndex: Int,
        notesJson: String,
        seedFingerprint: ByteArray,
        roundName: String
    ): VotingGovernancePczt =
        db(dbHandle)
            .buildGovernancePczt(
                roundId,
                bundleIndex,
                fvkBytes,
                hotkeyRawAddress,
                networkId,
                accountIndex,
                notesJson.toJniNoteInfos(),
                seedFingerprint,
                roundName
            ).toAppModel()

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
        db(dbHandle)
            .buildGovernancePcztFromSeed(
                roundId,
                bundleIndex,
                ufvk,
                networkId,
                accountIndex,
                notesJson.toJniNoteInfos(),
                walletSeed,
                hotkeySeed,
                seedFingerprint,
                roundName
            ).toAppModel()

    override suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray =
        rustBackend().extractPcztSighash(pcztBytes)

    override suspend fun extractSpendAuthSignatureFromSignedPczt(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): ByteArray = rustBackend().extractSpendAuthSig(signedPcztBytes, actionIndex)

    override suspend fun precomputeDelegationPir(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        networkId: Int,
        notesJson: String
    ): VotingDelegationPirPrecomputeResult =
        db(dbHandle)
            .precomputeDelegationPir(roundId, bundleIndex, pirServerUrl, networkId, notesJson.toJniNoteInfos())
            .toAppModel()

    override suspend fun buildAndProveDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        networkId: Int,
        notesJson: String,
        hotkeyRawAddress: ByteArray,
        proofProgress: ((Double) -> Unit)?
    ): VotingDelegationProof =
        db(dbHandle)
            .buildAndProveDelegation(
                roundId,
                bundleIndex,
                pirServerUrl,
                networkId,
                notesJson.toJniNoteInfos(),
                hotkeyRawAddress,
                proofProgress?.asVotingProgressCallback()
            ).toAppModel()

    override suspend fun getDelegationSubmission(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        senderSeed: ByteArray,
        networkId: Int,
        accountIndex: Int
    ): VotingDelegationSubmission =
        db(dbHandle).getDelegationSubmission(roundId, bundleIndex, senderSeed, networkId, accountIndex).toAppModel()

    override suspend fun getDelegationSubmissionWithKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray
    ): VotingDelegationSubmission =
        db(dbHandle)
            .getDelegationSubmissionWithKeystoneSig(roundId, bundleIndex, keystoneSig, keystoneSighash)
            .toAppModel()

    override suspend fun storeDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        txHash: String
    ) = db(dbHandle).storeDelegationTxHash(roundId, bundleIndex, txHash)

    override suspend fun getDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): VotingTxHashLookup =
        runExpectedMissingRowLookup {
            db(dbHandle).getDelegationTxHash(roundId, bundleIndex).toVotingTxHashLookup()
        } ?: VotingTxHashLookup.NotFound

    override suspend fun storeVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        txHash: String
    ) = db(dbHandle).storeVoteTxHash(roundId, bundleIndex, proposalId, txHash)

    override suspend fun getVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingTxHashLookup =
        runExpectedMissingRowLookup {
            db(dbHandle).getVoteTxHash(roundId, bundleIndex, proposalId).toVotingTxHashLookup()
        } ?: VotingTxHashLookup.NotFound

    override suspend fun markVoteSubmitted(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ) = db(dbHandle).markVoteSubmitted(roundId, bundleIndex, proposalId)

    override suspend fun storeCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        bundleJson: String,
        vcTreePosition: Long
    ) = db(dbHandle).storeCommitmentBundle(
        roundId,
        bundleIndex,
        proposalId,
        bundleJson.toJniVoteCommitmentResult(fallbackBundleIndex = bundleIndex),
        vcTreePosition
    )

    override suspend fun getCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommitmentBundleRecord? =
        runExpectedMissingRowLookup {
            db(dbHandle)
                .getCommitmentBundle(roundId, bundleIndex, proposalId)
                ?.toAppModel()
        }

    override suspend fun clearRecoveryState(
        dbHandle: Long,
        roundId: String
    ) =
        db(dbHandle).clearRecoveryState(roundId)

    override suspend fun recordShareDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        sentToUrls: List<String>,
        nullifier: ByteArray,
        submitAt: Long
    ) = db(dbHandle).recordShareDelegation(
        roundId,
        bundleIndex,
        proposalId,
        shareIndex,
        sentToUrls,
        nullifier,
        submitAt
    )

    override suspend fun getShareDelegations(
        dbHandle: Long,
        roundId: String
    ): List<VotingShareDelegationRecord> =
        db(dbHandle).getShareDelegations(roundId).map(JniShareDelegationRecord::toAppModel)

    override suspend fun markShareConfirmed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int
    ) = db(dbHandle).markShareConfirmed(roundId, bundleIndex, proposalId, shareIndex)

    override suspend fun addSentServers(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        newUrls: List<String>
    ) = db(dbHandle).addSentServers(roundId, bundleIndex, proposalId, shareIndex, newUrls)

    override suspend fun computeShareNullifier(
        voteCommitment: ByteArray,
        shareIndex: Int,
        blind: ByteArray
    ): ByteArray = rustBackend().computeShareNullifier(voteCommitment, shareIndex, blind)

    override suspend fun syncVoteTree(
        dbHandle: Long,
        roundId: String,
        nodeUrl: String
    ): Long =
        db(dbHandle).syncVoteTree(roundId, nodeUrl)

    override suspend fun storeVanPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        position: Int
    ) = db(dbHandle).storeVanPosition(roundId, bundleIndex, position.toLong())

    override suspend fun generateVanWitnessJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        anchorHeight: Int
    ): String =
        db(dbHandle).generateVanWitness(roundId, bundleIndex, anchorHeight.toLong()).toJson()

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
        networkId: Int,
        accountIndex: Int,
        singleShare: Boolean,
        proofProgress: ((Double) -> Unit)?
    ): VotingVoteCommitment =
        db(dbHandle)
            .buildVoteCommitment(
                roundId,
                bundleIndex,
                hotkeySeed,
                proposalId,
                choice,
                numOptions,
                witnessJson.toJniVanWitness(vanPosition, anchorHeight),
                networkId,
                accountIndex,
                singleShare,
                proofProgress?.asVotingProgressCallback()
            ).toAppModel()

    override suspend fun buildSharePayloadsJson(
        encSharesJson: String,
        commitmentJson: String,
        voteDecision: Int,
        numOptions: Int,
        vcTreePosition: Long,
        singleShareMode: Boolean
    ): String =
        rustBackend()
            .buildSharePayloads(
                commitmentJson.toJniVoteCommitmentResult(encSharesJson.toJniEncryptedShares()),
                voteDecision,
                numOptions,
                vcTreePosition,
                singleShareMode
            ).asList()
            .toSharePayloadsJson()

    override suspend fun signCastVote(
        hotkeySeed: ByteArray,
        networkId: Int,
        accountIndex: Int,
        commitmentJson: String
    ): ByteArray =
        rustBackend().signCastVote(
            hotkeySeed,
            networkId,
            accountIndex,
            commitmentJson.toJniVoteCommitmentResult()
        )

    override suspend fun warmProvingCaches() = rustBackend().warmProvingCaches()

    override suspend fun ballotDivisorZatoshi(): Long = BALLOT_DIVISOR_ZATOSHI

    override suspend fun decomposeWeight(weight: Long): List<Long> =
        rustBackend().decomposeWeight(weight).toList()

    override suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray = rustBackend().extractOrchardFvkFromUfvk(ufvk, networkId)
}

private fun JniBundleSetupResult.toAppModel() =
    VotingBundleSetupResult(
        bundleCount = bundleCount,
        eligibleWeight = eligibleWeight,
        bundleWeights = bundleWeights
    )

private fun JniVotingHotkey.toAppModel() =
    VotingHotkey(
        publicKey = publicKey.value.copyOf(),
        address = address
    )

private fun JniRoundState.toAppModel() =
    RoundStateInfo(
        roundId = roundId,
        phase = roundPhase.toAppModel(),
        snapshotHeight = snapshotHeight,
        hotkeyAddress = hotkeyAddress,
        delegatedWeight = delegatedWeight,
        proofGenerated = proofGenerated
    )

private fun JniRoundPhase.toAppModel() =
    when (this) {
        JniRoundPhase.INITIALIZED -> RoundPhase.INITIALIZED
        JniRoundPhase.HOTKEY_GENERATED -> RoundPhase.HOTKEY
        JniRoundPhase.DELEGATION_CONSTRUCTED -> RoundPhase.DELEGATION
        JniRoundPhase.DELEGATION_PROVED -> RoundPhase.PROVED
        JniRoundPhase.VOTE_READY -> RoundPhase.VOTE_READY
    }

private const val BALLOT_DIVISOR_ZATOSHI = 12_500_000L
private const val HEX_BYTE_CHARS = 2
private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff

private fun JniGovernancePczt.toAppModel() =
    VotingGovernancePczt(
        pcztBytes = pcztBytes.copyOf(),
        rk = rk.copyOf(),
        sighash = sighash.copyOf(),
        actionIndex = actionIndex
    )

private fun JniDelegationProofResult.toAppModel() =
    VotingDelegationProof(
        proof = proof.copyOf(),
        publicInputs = publicInputs.map(ByteArray::copyOf),
        nfSigned = nfSigned.copyOf(),
        cmxNew = cmxNew.copyOf(),
        govNullifiers = govNullifiers.map(ByteArray::copyOf),
        vanComm = vanComm.copyOf(),
        rk = rk.copyOf()
    )

private fun JniDelegationPirPrecomputeResult.toAppModel() =
    VotingDelegationPirPrecomputeResult(
        cachedCount = cachedCount,
        fetchedCount = fetchedCount
    )

private fun JniDelegationSubmissionResult.toAppModel() =
    VotingDelegationSubmission(
        proof = proof.copyOf(),
        rk = rk.copyOf(),
        spendAuthSig = spendAuthSig.copyOf(),
        sighash = sighash.copyOf(),
        nfSigned = nfSigned.copyOf(),
        cmxNew = cmxNew.copyOf(),
        govComm = govComm.copyOf(),
        govNullifiers = govNullifiers.map(ByteArray::copyOf),
        alpha = ByteArray(0),
        voteRoundId = voteRoundId
    )

private fun JniVoteCommitmentResult.toAppModel() =
    VotingVoteCommitment(
        vanNullifier = vanNullifier.copyOf(),
        voteAuthorityNoteNew = voteAuthorityNoteNew.copyOf(),
        voteCommitment = voteCommitment.copyOf(),
        rVpk = rVpk.copyOf(),
        alphaV = alphaV.copyOf(),
        anchorHeight = anchorHeight.toInt(),
        encSharesJson = encShares.toEncryptedSharesJson(),
        rawBundleJson = toStorageJson()
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

private fun String?.toVotingTxHashLookup(): VotingTxHashLookup =
    if (this == null) {
        VotingTxHashLookup.NotFound
    } else {
        VotingTxHashLookup.Present(this)
    }

private fun JniCommitmentBundleRecord.toAppModel() =
    VotingCommitmentBundleRecord(
        bundleJson = commitment.toStorageJson(),
        bundle = commitment.toStorageJson().toVoteCommitmentBundle(),
        vcTreePosition = vcTreePosition
    )

private fun JniVoteRecord.toAppModel() =
    VotingVoteRecord(
        proposalId = proposalId,
        bundleIndex = bundleIndex,
        choice = choice,
        submitted = submitted
    )

private fun JniShareDelegationRecord.toAppModel() =
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

private fun String.toJniNoteInfos(): List<JniNoteInfo> {
    val notes = JSONArray(this)
    return buildList {
        for (index in 0 until notes.length()) {
            val note = notes.getJSONObject(index)
            add(
                JniNoteInfo(
                    commitment = note.getString("commitment").hexStringToBytes(),
                    nullifier = note.getString("nullifier").hexStringToBytes(),
                    value = note.getLong("value"),
                    position = note.getLong("position"),
                    diversifier = note.getString("diversifier").hexStringToBytes(),
                    rho = note.getString("rho").hexStringToBytes(),
                    rseed = note.getString("rseed").hexStringToBytes(),
                    scope = note.getInt("scope"),
                    ufvk = note.getString("ufvk")
                )
            )
        }
    }
}

private fun List<JniNoteInfo>.toNotesJson(): String =
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
                .put("scope", note.scope)
                .put("ufvk", note.ufvk)
        }
    ).toString()

private fun String.toJniWitnesses(): List<JniWitnessData> {
    val witnesses = JSONArray(this)
    return buildList {
        for (index in 0 until witnesses.length()) {
            val witness = witnesses.getJSONObject(index)
            add(
                JniWitnessData(
                    noteCommitment = witness.getString("note_commitment").hexStringToBytes(),
                    position = witness.getLong("position"),
                    root = witness.getString("root").hexStringToBytes(),
                    authPath = witness.getJSONArray("auth_path").toByteArrays()
                )
            )
        }
    }
}

private fun List<JniWitnessData>.toWitnessesJson(): String =
    JSONArray(
        map { witness ->
            JSONObject()
                .put("note_commitment", witness.noteCommitment.toHexString())
                .put("position", witness.position)
                .put("root", witness.root.toHexString())
                .put("auth_path", witness.authPath.toHexJsonArray())
        }
    ).toString()

private fun JniVanWitness.toJson(): String =
    JSONObject()
        .put("auth_path", authPath.toHexJsonArray())
        .put("position", position)
        .put("anchor_height", anchorHeight)
        .toString()

private fun String.toJniVanWitness(
    position: Int,
    anchorHeight: Int
): JniVanWitness {
    val json = JSONObject(this)
    return JniVanWitness(
        authPath = json.getJSONArray("auth_path").toByteArrays(),
        position = json.optLong("position", position.toLong()),
        anchorHeight = json.optLong("anchor_height", anchorHeight.toLong())
    )
}

private fun String.toJniVoteCommitmentResult(
    encSharesOverride: List<JniWireEncryptedShare>? = null,
    fallbackBundleIndex: Int = 0
): JniVoteCommitmentResult {
    val json = JSONObject(this)
    return JniVoteCommitmentResult(
        vanNullifier = json.getString("van_nullifier").hexStringToBytes(),
        voteAuthorityNoteNew = json.getString("vote_authority_note_new").hexStringToBytes(),
        voteCommitment = json.getString("vote_commitment").hexStringToBytes(),
        proposalId = json.getInt("proposal_id"),
        bundleIndex = json.optInt("bundle_index", fallbackBundleIndex),
        proof = json.getString("proof").hexStringToBytes(),
        encShares = encSharesOverride ?: json.optJSONArray("enc_shares").toJniEncryptedShares(),
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

private fun JniVoteCommitmentResult.toStorageJson(): String =
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

private fun String.toJniEncryptedShares(): List<JniWireEncryptedShare> =
    JSONArray(this).toJniEncryptedShares()

private fun JSONArray?.toJniEncryptedShares(): List<JniWireEncryptedShare> {
    if (this == null) return emptyList()

    return buildList {
        for (index in 0 until length()) {
            val share = getJSONObject(index)
            add(
                JniWireEncryptedShare(
                    c1 = share.getString("c1").hexStringToBytes(),
                    c2 = share.getString("c2").hexStringToBytes(),
                    shareIndex = share.getInt("share_index")
                )
            )
        }
    }
}

private fun List<JniWireEncryptedShare>.toEncryptedSharesJson(): String =
    toEncryptedSharesJsonArray().toString()

private fun List<JniWireEncryptedShare>.toEncryptedSharesJsonArray(): JSONArray =
    JSONArray(map(JniWireEncryptedShare::toJson))

private fun JniWireEncryptedShare.toJson(): JSONObject =
    JSONObject()
        .put("c1", c1.toHexString())
        .put("c2", c2.toHexString())
        .put("share_index", shareIndex)

private fun List<JniSharePayload>.toSharePayloadsJson(): String =
    JSONArray(
        map { payload ->
            JSONObject()
                .put("shares_hash", payload.sharesHash.toHexString())
                .put("proposal_id", payload.proposalId)
                .put("vote_decision", payload.voteDecision)
                .put("enc_share", payload.encShare.toJson())
                .put("tree_position", payload.treePosition)
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

private fun ((Double) -> Unit).asVotingProgressCallback() =
    VotingProofProgressCallback { progress -> invoke(progress) }

private fun String.hexStringToBytes(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    return chunked(HEX_BYTE_CHARS)
        .map { chunk -> chunk.toInt(HEX_RADIX).toByte() }
        .toByteArray()
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
