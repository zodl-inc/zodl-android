package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.internal.DelegationProofResult
import cash.z.ecc.android.sdk.internal.DelegationPirPrecomputeResult
import cash.z.ecc.android.sdk.internal.DelegationSubmissionResult
import cash.z.ecc.android.sdk.internal.GovernancePcztResult
import cash.z.ecc.android.sdk.internal.CommitmentBundleRecord
import cash.z.ecc.android.sdk.internal.ShareDelegationRecord
import cash.z.ecc.android.sdk.internal.TypesafeVotingBackend
import cash.z.ecc.android.sdk.internal.VoteCommitmentResult
import cash.z.ecc.android.sdk.internal.VoteRecord
import cash.z.ecc.android.sdk.internal.VotingTxHashLookup as SdkVotingTxHashLookup
import cash.z.ecc.android.sdk.internal.model.voting.FfiBundleSetupResult
import cash.z.ecc.android.sdk.internal.model.voting.FfiRoundPhase
import cash.z.ecc.android.sdk.internal.model.voting.FfiRoundState
import cash.z.ecc.android.sdk.internal.model.voting.FfiVotingHotkey
import co.electriccoin.zcash.ui.common.model.voting.RoundPhase
import co.electriccoin.zcash.ui.common.model.voting.RoundStateInfo
import co.electriccoin.zcash.ui.common.model.voting.VotingCommitmentBundleRecord
import co.electriccoin.zcash.ui.common.model.voting.VotingBundleSetupResult
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

    suspend fun generateNoteWitnessesJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        notesJson: String
    ): String

    suspend fun storeWitnesses(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        witnessesJson: String
    )

    suspend fun buildGovernancePczt(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        ufvk: String,
        networkId: Int,
        accountIndex: Int,
        notesJson: String,
        hotkeyRawSeed: ByteArray,
        seedFingerprint: ByteArray,
        roundName: String,
        addressIndex: Int = 0
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
        hotkeyRawSeed: ByteArray,
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
        roundId: String,
        rVpk: ByteArray,
        vanNullifier: ByteArray,
        vanNew: ByteArray,
        voteCommitment: ByteArray,
        proposalId: Int,
        anchorHeight: Int,
        alphaV: ByteArray
    ): ByteArray

    suspend fun warmProvingCaches()

    suspend fun ballotDivisorZatoshi(): Long

    suspend fun decomposeWeight(weight: Long): List<Long>

    suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray
}

class VotingCryptoClientImpl(
    private val backend: TypesafeVotingBackend
) : VotingCryptoClient {
    override suspend fun openVotingDb(dbPath: String): Long = backend.openVotingDb(dbPath)

    override suspend fun closeVotingDb(dbHandle: Long) = backend.closeVotingDb(dbHandle)

    override suspend fun setWalletId(
        dbHandle: Long,
        walletId: String
    ) = backend.setWalletId(dbHandle, walletId)

    override suspend fun initializeRound(
        dbHandle: Long,
        roundId: String,
        snapshotHeight: Long,
        eaPK: ByteArray,
        ncRoot: ByteArray,
        nullifierIMTRoot: ByteArray,
        sessionJson: String?
    ) = backend.initRound(
        dbHandle = dbHandle,
        roundId = roundId,
        snapshotHeight = snapshotHeight,
        eaPK = eaPK,
        ncRoot = ncRoot,
        nullifierIMTRoot = nullifierIMTRoot,
        sessionJson = sessionJson
    )

    override suspend fun getRoundState(
        dbHandle: Long,
        roundId: String
    ): RoundStateInfo? = backend.getRoundState(dbHandle, roundId)?.toAppModel()

    override suspend fun listRoundsJson(dbHandle: Long): String = backend.listRoundsJson(dbHandle)

    override suspend fun getBundleCount(
        dbHandle: Long,
        roundId: String
    ): Int = backend.getBundleCount(dbHandle, roundId)

    override suspend fun getVotes(
        dbHandle: Long,
        roundId: String
    ): List<VotingVoteRecord> = backend.getVotes(dbHandle, roundId)
        .map(VoteRecord::toAppModel)

    override suspend fun clearRound(
        dbHandle: Long,
        roundId: String
    ) = backend.clearRound(dbHandle, roundId)

    override suspend fun deleteSkippedBundles(
        dbHandle: Long,
        roundId: String,
        keepCount: Int
    ): Long = backend.deleteSkippedBundles(dbHandle, roundId, keepCount)

    override suspend fun setupBundles(
        dbHandle: Long,
        roundId: String,
        notesJson: String
    ): VotingBundleSetupResult = backend.setupBundles(dbHandle, roundId, notesJson).toAppModel()

    override suspend fun computeBundleSetup(notesJson: String): VotingBundleSetupResult =
        backend.computeBundleSetup(notesJson).toAppModel()

    override suspend fun generateHotkey(
        dbHandle: Long,
        roundId: String,
        seed: ByteArray
    ): VotingHotkey = backend.generateHotkey(dbHandle, roundId, seed).toAppModel()

    override suspend fun storeTreeState(
        dbHandle: Long,
        roundId: String,
        treeStateBytes: ByteArray
    ) = backend.storeTreeState(dbHandle, roundId, treeStateBytes)

    override suspend fun getWalletNotesJson(
        walletDbPath: String,
        snapshotHeight: Long,
        networkId: Int,
        accountUuidBytes: ByteArray
    ): String = backend.getWalletNotes(walletDbPath, snapshotHeight, networkId, accountUuidBytes)

    override suspend fun generateNoteWitnessesJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        walletDbPath: String,
        notesJson: String
    ): String = backend.generateNoteWitnesses(dbHandle, roundId, bundleIndex, walletDbPath, notesJson)

    override suspend fun storeWitnesses(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        witnessesJson: String
    ) = backend.storeWitnesses(dbHandle, roundId, bundleIndex, witnessesJson)

    override suspend fun buildGovernancePczt(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        ufvk: String,
        networkId: Int,
        accountIndex: Int,
        notesJson: String,
        hotkeyRawSeed: ByteArray,
        seedFingerprint: ByteArray,
        roundName: String,
        addressIndex: Int
    ): VotingGovernancePczt = backend.buildGovernancePczt(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        ufvk = ufvk,
        networkId = networkId,
        accountIndex = accountIndex,
        notesJson = notesJson,
        hotkeyRawSeed = hotkeyRawSeed,
        seedFingerprint = seedFingerprint,
        roundName = roundName,
        addressIndex = addressIndex
    ).toAppModel()

    override suspend fun extractPcztSighash(pcztBytes: ByteArray): ByteArray =
        backend.extractPcztSighash(pcztBytes)

    override suspend fun extractSpendAuthSignatureFromSignedPczt(
        signedPcztBytes: ByteArray,
        actionIndex: Int
    ): ByteArray = backend.extractSpendAuthSig(signedPcztBytes, actionIndex)

    override suspend fun precomputeDelegationPir(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        networkId: Int,
        notesJson: String
    ): VotingDelegationPirPrecomputeResult = backend.precomputeDelegationPir(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        pirServerUrl = pirServerUrl,
        networkId = networkId,
        notesJson = notesJson
    ).toAppModel()

    override suspend fun buildAndProveDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        pirServerUrl: String,
        networkId: Int,
        notesJson: String,
        hotkeyRawSeed: ByteArray,
        proofProgress: ((Double) -> Unit)?
    ): VotingDelegationProof = backend.buildAndProveDelegation(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        pirServerUrl = pirServerUrl,
        networkId = networkId,
        notesJson = notesJson,
        hotkeyRawSeed = hotkeyRawSeed,
        proofProgress = proofProgress
    ).toAppModel()

    override suspend fun getDelegationSubmission(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        senderSeed: ByteArray,
        networkId: Int,
        accountIndex: Int
    ): VotingDelegationSubmission = backend.getDelegationSubmission(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        senderSeed = senderSeed,
        networkId = networkId,
        accountIndex = accountIndex
    ).toAppModel()

    override suspend fun getDelegationSubmissionWithKeystoneSignature(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        keystoneSig: ByteArray,
        keystoneSighash: ByteArray
    ): VotingDelegationSubmission = backend.getDelegationSubmissionWithKeystoneSig(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        keystoneSig = keystoneSig,
        keystoneSighash = keystoneSighash
    ).toAppModel()

    override suspend fun storeDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        txHash: String
    ) = backend.storeDelegationTxHash(dbHandle, roundId, bundleIndex, txHash)

    override suspend fun getDelegationTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): VotingTxHashLookup = backend.getDelegationTxHash(dbHandle, roundId, bundleIndex).toAppModel()

    override suspend fun storeVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        txHash: String
    ) = backend.storeVoteTxHash(dbHandle, roundId, bundleIndex, proposalId, txHash)

    override suspend fun getVoteTxHash(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingTxHashLookup = backend.getVoteTxHash(dbHandle, roundId, bundleIndex, proposalId).toAppModel()

    override suspend fun markVoteSubmitted(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ) = backend.markVoteSubmitted(dbHandle, roundId, bundleIndex, proposalId)

    override suspend fun storeCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        bundleJson: String,
        vcTreePosition: Long
    ) = backend.storeCommitmentBundle(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        proposalId = proposalId,
        bundleJson = bundleJson,
        vcTreePosition = vcTreePosition
    )

    override suspend fun getCommitmentBundle(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): VotingCommitmentBundleRecord? = backend.getCommitmentBundle(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        proposalId = proposalId
    )?.toAppModel()

    override suspend fun clearRecoveryState(
        dbHandle: Long,
        roundId: String
    ) = backend.clearRecoveryState(dbHandle, roundId)

    override suspend fun recordShareDelegation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        sentToUrls: List<String>,
        nullifier: ByteArray,
        submitAt: Long
    ) = backend.recordShareDelegation(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        proposalId = proposalId,
        shareIndex = shareIndex,
        sentToUrls = sentToUrls,
        nullifier = nullifier,
        submitAt = submitAt
    )

    override suspend fun getShareDelegations(
        dbHandle: Long,
        roundId: String
    ): List<VotingShareDelegationRecord> = backend.getShareDelegations(dbHandle, roundId)
        .map(ShareDelegationRecord::toAppModel)

    override suspend fun markShareConfirmed(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int
    ) = backend.markShareConfirmed(dbHandle, roundId, bundleIndex, proposalId, shareIndex)

    override suspend fun addSentServers(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        shareIndex: Int,
        newUrls: List<String>
    ) = backend.addSentServers(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        proposalId = proposalId,
        shareIndex = shareIndex,
        newUrls = newUrls
    )

    override suspend fun computeShareNullifier(
        voteCommitment: ByteArray,
        shareIndex: Int,
        blind: ByteArray
    ): ByteArray = backend.computeShareNullifier(voteCommitment, shareIndex, blind)

    override suspend fun syncVoteTree(
        dbHandle: Long,
        roundId: String,
        nodeUrl: String
    ): Long = backend.syncVoteTree(dbHandle, roundId, nodeUrl)

    override suspend fun storeVanPosition(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        position: Int
    ) = backend.storeVanPosition(dbHandle, roundId, bundleIndex, position)

    override suspend fun generateVanWitnessJson(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        anchorHeight: Int
    ): String = backend.generateVanWitnessJson(dbHandle, roundId, bundleIndex, anchorHeight)

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
        singleShare: Boolean,
        proofProgress: ((Double) -> Unit)?
    ): VotingVoteCommitment = backend.buildVoteCommitment(
        dbHandle = dbHandle,
        roundId = roundId,
        bundleIndex = bundleIndex,
        hotkeySeed = hotkeySeed,
        proposalId = proposalId,
        choice = choice,
        numOptions = numOptions,
        witnessJson = witnessJson,
        vanPosition = vanPosition,
        anchorHeight = anchorHeight,
        networkId = networkId,
        singleShare = singleShare,
        proofProgress = proofProgress
    ).toAppModel()

    override suspend fun buildSharePayloadsJson(
        encSharesJson: String,
        commitmentJson: String,
        voteDecision: Int,
        numOptions: Int,
        vcTreePosition: Long,
        singleShareMode: Boolean
    ): String = backend.buildSharePayloadsJson(
        encSharesJson = encSharesJson,
        commitmentJson = commitmentJson,
        voteDecision = voteDecision,
        numOptions = numOptions,
        vcTreePosition = vcTreePosition,
        singleShareMode = singleShareMode
    )

    override suspend fun signCastVote(
        hotkeySeed: ByteArray,
        networkId: Int,
        roundId: String,
        rVpk: ByteArray,
        vanNullifier: ByteArray,
        vanNew: ByteArray,
        voteCommitment: ByteArray,
        proposalId: Int,
        anchorHeight: Int,
        alphaV: ByteArray
    ): ByteArray = backend.signCastVote(
        hotkeySeed = hotkeySeed,
        networkId = networkId,
        roundId = roundId,
        rVpk = rVpk,
        vanNullifier = vanNullifier,
        vanNew = vanNew,
        voteCommitment = voteCommitment,
        proposalId = proposalId,
        anchorHeight = anchorHeight,
        alphaV = alphaV
    )

    override suspend fun warmProvingCaches() = backend.warmProvingCaches()

    override suspend fun ballotDivisorZatoshi(): Long = backend.ballotDivisorZatoshi()

    override suspend fun decomposeWeight(weight: Long): List<Long> = backend.decomposeWeight(weight)

    override suspend fun extractOrchardFvkFromUfvk(
        ufvk: String,
        networkId: Int
    ): ByteArray = backend.extractOrchardFvkFromUfvk(ufvk, networkId)
}

private fun FfiBundleSetupResult.toAppModel() =
    VotingBundleSetupResult(
        bundleCount = bundleCount,
        eligibleWeight = eligibleWeight,
        bundleWeights = bundleWeights
    )

private fun FfiVotingHotkey.toAppModel() =
    VotingHotkey(
        secretKey = secretKey.value.copyOf(),
        publicKey = publicKey.value.copyOf(),
        address = address
    )

private fun FfiRoundState.toAppModel() =
    RoundStateInfo(
        roundId = roundId,
        phase = roundPhase.toAppModel(),
        snapshotHeight = snapshotHeight,
        hotkeyAddress = hotkeyAddress,
        delegatedWeight = delegatedWeight,
        proofGenerated = proofGenerated
    )

private fun FfiRoundPhase.toAppModel() =
    when (this) {
        FfiRoundPhase.INITIALIZED -> RoundPhase.INITIALIZED
        FfiRoundPhase.HOTKEY_GENERATED -> RoundPhase.HOTKEY
        FfiRoundPhase.DELEGATION_CONSTRUCTED -> RoundPhase.DELEGATION
        FfiRoundPhase.DELEGATION_PROVED -> RoundPhase.PROVED
        FfiRoundPhase.VOTE_READY -> RoundPhase.VOTE_READY
    }

private fun GovernancePcztResult.toAppModel() =
    VotingGovernancePczt(
        pcztBytes = pcztBytes.copyOf(),
        rk = rk.copyOf(),
        sighash = sighash.copyOf(),
        actionIndex = actionIndex
    )

private fun DelegationProofResult.toAppModel() =
    VotingDelegationProof(
        proof = proof.copyOf(),
        publicInputs = publicInputs.map(ByteArray::copyOf),
        nfSigned = nfSigned.copyOf(),
        cmxNew = cmxNew.copyOf(),
        govNullifiers = govNullifiers.map(ByteArray::copyOf),
        vanComm = vanComm.copyOf(),
        rk = rk.copyOf()
    )

private fun DelegationPirPrecomputeResult.toAppModel() =
    VotingDelegationPirPrecomputeResult(
        cachedCount = cachedCount,
        fetchedCount = fetchedCount
    )

private fun DelegationSubmissionResult.toAppModel() =
    VotingDelegationSubmission(
        proof = proof.copyOf(),
        rk = rk.copyOf(),
        spendAuthSig = spendAuthSig.copyOf(),
        sighash = sighash.copyOf(),
        nfSigned = nfSigned.copyOf(),
        cmxNew = cmxNew.copyOf(),
        govComm = govComm.copyOf(),
        govNullifiers = govNullifiers.map(ByteArray::copyOf),
        alpha = alpha.copyOf(),
        voteRoundId = voteRoundId
    )

private fun VoteCommitmentResult.toAppModel() =
    VotingVoteCommitment(
        vanNullifier = vanNullifier.copyOf(),
        voteAuthorityNoteNew = voteAuthorityNoteNew.copyOf(),
        voteCommitment = voteCommitment.copyOf(),
        rVpk = rVpk.copyOf(),
        alphaV = alphaV.copyOf(),
        anchorHeight = anchorHeight,
        encSharesJson = encSharesJson,
        rawBundleJson = rawBundleJson
    )

private fun SdkVotingTxHashLookup.toAppModel(): VotingTxHashLookup =
    when (this) {
        SdkVotingTxHashLookup.NotFound -> VotingTxHashLookup.NotFound
        is SdkVotingTxHashLookup.Present -> VotingTxHashLookup.Present(txHash)
    }

private fun CommitmentBundleRecord.toAppModel() =
    VotingCommitmentBundleRecord(
        bundleJson = bundleJson,
        bundle = bundleJson.toVoteCommitmentBundle(),
        vcTreePosition = vcTreePosition
    )

private fun VoteRecord.toAppModel() =
    VotingVoteRecord(
        proposalId = proposalId,
        bundleIndex = bundleIndex,
        choice = choice,
        submitted = submitted
    )

private fun ShareDelegationRecord.toAppModel() =
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
