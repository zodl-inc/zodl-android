package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmationProbeResult
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.hasVoteReady
import co.electriccoin.zcash.ui.common.model.voting.isLastMoment
import co.electriccoin.zcash.ui.common.model.voting.isRoundPhaseRegression
import co.electriccoin.zcash.ui.common.model.voting.isSyntheticAbstainChoice
import co.electriccoin.zcash.ui.common.model.voting.shareSubmissionDeadlineEpochSeconds
import co.electriccoin.zcash.ui.common.model.voting.toDelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.toEncryptedSharesJson
import co.electriccoin.zcash.ui.common.model.voting.toSharePayloads
import co.electriccoin.zcash.ui.common.model.voting.toVoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingDelegationPirPrecomputeKey
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import kotlin.random.Random

class VotingAuthorizationException(
    cause: Exception
) : Exception(
        cause.message ?: "Voting authorization failed",
        cause
    )

class SubmitVotesUseCase(
    private val resolveVotingRoundSession: ResolveVotingRoundSessionUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
    private val votingApiProvider: VotingApiProvider,
    private val pirSnapshotResolver: PirSnapshotResolver,
    private val votingHotkeySeedProvider: VotingHotkeySeedProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getWalletSeedBytes: GetWalletSeedBytesUseCase,
    private val prepareVotingRound: PrepareVotingRoundUseCase,
    private val votingShareTrackingScheduler: VotingShareTrackingScheduler,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        roundId: String,
        choices: Map<Int, Int>,
        onProgress: (VotingSubmissionProgress) -> Unit = {}
    ): VotingSubmissionResult =
        withContext(Dispatchers.IO) {
            if (choices.isEmpty()) {
                return@withContext VotingSubmissionResult(submittedProposalCount = 0)
            }

            val selectedAccount = getSelectedWalletAccount()
            val isKeystone = selectedAccount is KeystoneAccount
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            when (val preparation = prepareVotingRound(roundId)) {
                is VotingRoundPreparationResult.Ready -> {
                    Unit
                }

                is VotingRoundPreparationResult.Ineligible -> {
                    throw VotingSubmissionRecoverableException(VotingErrors.Ineligible)
                }

                is VotingRoundPreparationResult.WalletSyncing -> {
                    throw VotingSubmissionRecoverableException(
                        VotingErrors.WalletSyncing(
                            scannedHeight = preparation.scannedHeight,
                            snapshotHeight = preparation.snapshotHeight
                        )
                    )
                }
            }

            val sessionContext = resolveVotingRoundSession(roundId)
            val session = sessionContext.session
            val sessionRoundId = session.voteRoundId.toHex()
            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val serviceConfig = sessionContext.serviceConfig
            val voteServerUrls =
                serviceConfig.voteServers
                    .map { endpoint -> endpoint.url.trimEnd('/') }
                    .distinct()
            val voteServerUrl =
                voteServerUrls
                    .firstOrNull()
                    ?: throw VotingSubmissionRecoverableException(VotingErrors.MissingVotingServerUrl)
            val pirServerUrl =
                pirSnapshotResolver.resolve(
                    endpoints = serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                    expectedSnapshotHeight = session.snapshotHeight
                )

            val recovery =
                votingRecoveryRepository.get(accountUuidString, roundId)
                    ?: throw VotingSubmissionRecoverableException(
                        VotingErrors.MissingPreparedRecovery(roundId)
                    )
            votingRecoveryRepository.storeVoteServerUrls(accountUuidString, roundId, voteServerUrls)
            votingRecoveryRepository.storeVoteEndEpochSeconds(accountUuidString, roundId, session.voteEndTime.epochSecond)
            val recoveryBundleCount = recovery.bundleCount
            val hotkeySeed = getHotkeySeed(accountUuidString, roundId, recovery)

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val votingDbPath =
                File(walletDbPath)
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val senderSeed = if (isKeystone) null else getWalletSeedBytes()
            val accountIndex = selectedAccount.hdAccountIndex.index.toInt()
            val accountUfvk = selectedAccount.sdkAccount.ufvk
            val seedFingerprint = selectedAccount.sdkAccount.seedFingerprint
            val allNotesJson =
                votingCryptoClient.getWalletNotesJson(
                    walletDbPath = walletDbPath,
                    snapshotHeight = session.snapshotHeight,
                    networkId = networkId,
                    accountUuidBytes = selectedAccount.sdkAccount.accountUuid.value
                )
            val hotkeyRawAddress =
                votingCryptoClient.deriveHotkeyRawAddress(
                    hotkeySeed = hotkeySeed,
                    networkId = networkId
                )

            val singleShare = session.isLastMoment()
            val submitAtDeadline = session.shareSubmissionDeadlineEpochSeconds(singleShare)
            val sortedChoices = choices.toSortedMap()
            val totalChoices = sortedChoices.size

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, selectedAccount.sdkAccount.accountUuid.toString())
                val bundleCount =
                    recoveryBundleCount
                        ?: votingCryptoClient
                            .getBundleCount(dbHandle, roundId)
                            .takeIf { count -> count >= 0 }
                        ?: throw VotingSubmissionRecoverableException(
                            VotingErrors.MissingBundleCount(roundId)
                        )
                val submittedBundleIndicesByProposal =
                    votingCryptoClient
                        .getVotes(
                            dbHandle = dbHandle,
                            roundId = roundId
                        ).filter { vote ->
                            vote.submitted
                        }.groupBy { vote ->
                            vote.proposalId
                        }.mapValuesTo(mutableMapOf()) { (_, votes) ->
                            votes.mapTo(mutableSetOf()) { vote -> vote.bundleIndex }
                        }
                val delegatedShareIndicesByTarget =
                    votingCryptoClient
                        .getShareDelegations(
                            dbHandle = dbHandle,
                            roundId = roundId
                        ).groupBy { record ->
                            ShareDelegationTarget(
                                bundleIndex = record.bundleIndex,
                                proposalId = record.proposalId
                            )
                        }.mapValuesTo(mutableMapOf()) { (_, records) ->
                            records.mapTo(mutableSetOf()) { it.shareIndex }
                        }

                if (recovery.phase != VotingRecoveryPhase.DELEGATION_SUBMITTED &&
                    recovery.phase != VotingRecoveryPhase.VOTES_SUBMITTED &&
                    recovery.phase != VotingRecoveryPhase.SHARES_SUBMITTED
                ) {
                    repeat(bundleCount) { bundleIndex ->
                        onProgress(
                            VotingSubmissionProgress.Authorizing(
                                progress = bundleIndex.toFloat() / bundleCount.coerceAtLeast(1)
                            )
                        )

                        val cachedDelegationTxHash =
                            votingCryptoClient.getDelegationTxHash(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex
                            )
                        if (cachedDelegationTxHash is VotingTxHashLookup.Present) {
                            // Fast-path probe: mirrors iOS `recoverDelegationVanPosition` with
                            // `confirmationTimeout: 0`. A cached hash that hasn't propagated yet
                            // (or that landed on-chain with a non-zero code) must NOT block this
                            // flow for 90s — return null and fall through to fresh delegation.
                            val confirmation =
                                awaitTxConfirmation(
                                    txHash = cachedDelegationTxHash.txHash,
                                    maxAttempts = 1
                                )
                            val vanPosition =
                                confirmation
                                    ?.takeIf { it.code == 0 }
                                    ?.event("delegate_vote")
                                    ?.attribute("leaf_index")
                                    ?.toIntOrNull()
                            if (vanPosition != null) {
                                votingCryptoClient.storeVanPosition(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    position = vanPosition
                                )
                                return@repeat
                            }
                            // No usable cached confirmation (not yet propagated, failed on-chain,
                            // or missing leaf_index) — fall through and re-run delegation from
                            // scratch for this bundle.
                        }

                        // A resumed round may already be past delegation/proving in Rust.
                        // Skip stale rebuild work instead of trying to regress the phase.
                        var rustRoundState = votingCryptoClient.getRoundState(dbHandle, roundId)
                        if (rustRoundState?.phase.hasVoteReady()) {
                            return@repeat
                        }

                        if (rustRoundState?.proofGenerated != true) {
                            val witnessesJson =
                                votingCryptoClient.generateNoteWitnessesJson(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    walletDbPath = walletDbPath,
                                    networkId = networkId,
                                    notesJson = allNotesJson
                                )
                            votingCryptoClient.storeWitnesses(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                notesJson = allNotesJson,
                                witnessesJson = witnessesJson
                            )

                            val precomputeResult =
                                votingProofPrecomputeRepository.awaitDelegationPirPrecompute(
                                    VotingDelegationPirPrecomputeKey(
                                        accountUuid = accountUuidString,
                                        roundId = roundId,
                                        bundleIndex = bundleIndex
                                    )
                                )
                            precomputeResult?.onFailure { throwable ->
                                Log.w(TAG, "Voting PIR precompute failed for round $roundId bundle $bundleIndex", throwable)
                            }
                            if (!isKeystone && precomputeResult?.isSuccess != true) {
                                val governancePcztResult =
                                    runCatching {
                                        votingCryptoClient.buildGovernancePcztFromSeed(
                                            dbHandle = dbHandle,
                                            roundId = roundId,
                                            bundleIndex = bundleIndex,
                                            ufvk =
                                                requireNotNull(accountUfvk) {
                                                    "Software wallet account is missing UFVK for voting bundle $bundleIndex"
                                                },
                                            networkId = networkId,
                                            accountIndex = accountIndex,
                                            notesJson = allNotesJson,
                                            walletSeed =
                                                requireNotNull(senderSeed) {
                                                    "Software wallet seed is missing for voting bundle $bundleIndex"
                                                },
                                            hotkeySeed = hotkeySeed,
                                            seedFingerprint =
                                                requireNotNull(seedFingerprint) {
                                                    "Software wallet account is missing seed fingerprint for voting bundle $bundleIndex"
                                                },
                                            roundName = session.title
                                        )
                                    }
                                // Foreground or resumed submit may find that Rust already
                                // advanced past PCZT building; ignore only that stale phase race.
                                governancePcztResult
                                    .exceptionOrNull()
                                    ?.takeUnless { throwable -> throwable.isRoundPhaseRegression() }
                                    ?.let { throw it }
                                if (governancePcztResult.exceptionOrNull()?.isRoundPhaseRegression() == true) {
                                    Log.i(
                                        TAG,
                                        "Skipping governance PCZT rebuild for round $roundId bundle $bundleIndex; " +
                                            "Rust round phase already advanced"
                                    )
                                }
                            }

                            rustRoundState = votingCryptoClient.getRoundState(dbHandle, roundId)
                            if (rustRoundState?.proofGenerated != true) {
                                runVotingAuthorizationStep(isKeystone) {
                                    votingCryptoClient.buildAndProveDelegation(
                                        dbHandle = dbHandle,
                                        roundId = roundId,
                                        bundleIndex = bundleIndex,
                                        pirServerUrl = pirServerUrl,
                                        networkId = networkId,
                                        notesJson = allNotesJson,
                                        hotkeyRawAddress = hotkeyRawAddress,
                                        proofProgress = { progress ->
                                            onProgress(
                                                VotingSubmissionProgress.Authorizing(
                                                    progress =
                                                        (
                                                            (bundleIndex + progress.coerceIn(0.0, 1.0)) /
                                                                bundleCount.coerceAtLeast(1)
                                                        ).toFloat()
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        votingRecoveryRepository.setPhase(
                            accountUuid = accountUuidString,
                            roundId = roundId,
                            phase = VotingRecoveryPhase.DELEGATION_PROVED
                        )

                        val txResult =
                            runVotingAuthorizationStep(isKeystone) {
                                val submission =
                                    if (isKeystone) {
                                        val keystoneSignature =
                                            recovery.keystoneBundleSignatures[bundleIndex]
                                                ?: error("Keystone signature is missing for voting bundle $bundleIndex")
                                        votingCryptoClient.getDelegationSubmissionWithKeystoneSignature(
                                            dbHandle = dbHandle,
                                            roundId = roundId,
                                            bundleIndex = bundleIndex,
                                            keystoneSig = keystoneSignature.decodeSpendAuthSig(),
                                            keystoneSighash = keystoneSignature.decodeSighash()
                                        )
                                    } else {
                                        votingCryptoClient.getDelegationSubmission(
                                            dbHandle = dbHandle,
                                            roundId = roundId,
                                            bundleIndex = bundleIndex,
                                            senderSeed = requireNotNull(senderSeed),
                                            networkId = networkId,
                                            accountIndex = accountIndex
                                        )
                                    }
                                if (isKeystone) {
                                    val keystoneSignature =
                                        recovery.keystoneBundleSignatures[bundleIndex]
                                            ?: error("Keystone signature is missing for voting bundle $bundleIndex")
                                    require(submission.spendAuthSig.contentEquals(keystoneSignature.decodeSpendAuthSig())) {
                                        "Delegation signature mismatch for Keystone voting bundle $bundleIndex"
                                    }
                                    require(submission.sighash.contentEquals(keystoneSignature.decodeSighash())) {
                                        "Delegation sighash mismatch for Keystone voting bundle $bundleIndex"
                                    }
                                    keystoneSignature.decodeRk()?.let { expectedRk ->
                                        require(submission.rk.contentEquals(expectedRk)) {
                                            "Delegation rk mismatch for Keystone voting bundle $bundleIndex"
                                        }
                                    }
                                }
                                votingApiProvider
                                    .submitDelegation(submission.toDelegationRegistration())
                                    .requireAccepted("Delegation transaction was rejected")
                            }
                        votingCryptoClient.storeDelegationTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            txHash = txResult.txHash
                        )

                        val confirmation =
                            runVotingAuthorizationStep(isKeystone) {
                                awaitTxConfirmation(txResult.txHash)
                                    ?: throw VotingSubmissionRecoverableException(
                                        VotingErrors.TxConfirmationTimedOut(txResult.txHash)
                                    )
                            }
                        runVotingAuthorizationStep(isKeystone) {
                            confirmation.requireAccepted("Delegation transaction failed")
                        }

                        val vanPosition = confirmation.delegateVoteVanPosition(bundleIndex)
                        traceVotingStep(
                            roundId = roundId,
                            step = "storeDelegationVanPosition",
                            bundleIndex = bundleIndex
                        ) {
                            votingCryptoClient.storeVanPosition(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                position = vanPosition
                            )
                        }
                    }

                    votingRecoveryRepository.setPhase(
                        accountUuid = accountUuidString,
                        roundId = roundId,
                        phase = VotingRecoveryPhase.DELEGATION_SUBMITTED
                    )
                }

                val proposalSelections =
                    sortedChoices
                        .mapNotNull { (proposalId, choiceId) ->
                            val proposal =
                                session.proposals.firstOrNull { it.id == proposalId }
                                    ?: error("Unknown proposal id $proposalId for round $roundId")
                            if (proposal.options.none { option -> option.id == choiceId }) {
                                null
                            } else {
                                proposalId to
                                    VotingProposalSelection(
                                        choiceId = choiceId,
                                        numOptions = proposal.options.size
                                    )
                            }
                        }.toMap()
                if (proposalSelections.isNotEmpty()) {
                    votingRecoveryRepository.storeProposalSelections(
                        accountUuid = accountUuidString,
                        roundId = roundId,
                        proposalSelections = proposalSelections
                    )
                }
                votingRecoveryRepository.storeSingleShareMode(
                    accountUuid = accountUuidString,
                    roundId = roundId,
                    singleShareMode = singleShare
                )

                // Track per-proposal completion to mirror iOS `failCount == 0` gating
                // (`VotingStore+Submission.swift` ~line 411-440). Failures throw out of
                // this try block today, but counting explicitly keeps `submittedAt` honest
                // if a future skip-path is added that does not throw, and makes the
                // "every expected proposal accounted for" invariant local to this scope.
                // A proposal already on-chain from a prior run (the idempotent recovery
                // path below) counts as submitted — the user's previous attempt already
                // succeeded for that proposal.
                var processedProposalCount = 0
                sortedChoices.entries.forEachIndexed { proposalIndex, (proposalId, choiceId) ->
                    val proposal =
                        session.proposals.firstOrNull { it.id == proposalId }
                            ?: error("Unknown proposal id $proposalId for round $roundId")
                    val progressBase = proposalIndex + 1

                    fun emitSubmittingProgress(
                        bundleIndex: Int,
                        bundleProgress: Double
                    ) {
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress =
                                    calculateSubmittingBundleProgress(
                                        proposalIndex = proposalIndex,
                                        bundleIndex = bundleIndex,
                                        bundleCount = bundleCount,
                                        totalChoices = totalChoices,
                                        bundleProgress = bundleProgress
                                    )
                            )
                        )
                    }

                    val submittedBundles =
                        submittedBundleIndicesByProposal
                            .getOrPut(proposalId) { mutableSetOf() }

                    if (proposalId in recovery.submittedProposalIds && submittedBundles.size >= bundleCount) {
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress = progressBase.toFloat() / totalChoices.coerceAtLeast(1)
                            )
                        )
                        markProposalSubmissionComplete(accountUuidString, roundId, proposalId)
                        processedProposalCount++
                        return@forEachIndexed
                    }

                    val hasOnWireOption = proposal.options.any { option -> option.id == choiceId }
                    if (!hasOnWireOption && proposal.isSyntheticAbstainChoice(choiceId)) {
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress = progressBase.toFloat() / totalChoices.coerceAtLeast(1)
                            )
                        )
                        markProposalSubmissionComplete(accountUuidString, roundId, proposalId)
                        processedProposalCount++
                        return@forEachIndexed
                    }
                    require(hasOnWireOption) {
                        "Unknown vote option $choiceId for proposal $proposalId"
                    }

                    repeat(bundleCount) { bundleIndex ->
                        if (bundleIndex in submittedBundles) {
                            return@repeat
                        }

                        emitSubmittingProgress(bundleIndex, bundleProgress = 0.0)

                        val cachedVoteTxHash =
                            votingCryptoClient.getVoteTxHash(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId
                            )

                        if (cachedVoteTxHash is VotingTxHashLookup.Present) {
                            val cachedConfirmation = probeCachedTx(cachedVoteTxHash.txHash)
                            if (cachedConfirmation is TxConfirmationProbeResult.Confirmed) {
                                val confirmation = cachedConfirmation.confirmation
                                val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
                                traceVotingStep(
                                    roundId = roundId,
                                    step = "storeCachedVoteVanPosition",
                                    bundleIndex = bundleIndex,
                                    proposalId = proposalId
                                ) {
                                    votingCryptoClient.storeVanPosition(
                                        dbHandle = dbHandle,
                                        roundId = roundId,
                                        bundleIndex = bundleIndex,
                                        position = confirmedVanPosition
                                    )
                                }

                                val storedCommitment =
                                    traceVotingStep(
                                        roundId = roundId,
                                        step = "getCachedCommitmentBundle",
                                        bundleIndex = bundleIndex,
                                        proposalId = proposalId
                                    ) {
                                        votingCryptoClient.getCommitmentBundle(
                                            dbHandle = dbHandle,
                                            roundId = roundId,
                                            bundleIndex = bundleIndex,
                                            proposalId = proposalId
                                        )
                                    } ?: throw VotingSubmissionRecoverableException(
                                        VotingErrors.MissingCachedCommitment(
                                            roundId = roundId,
                                            bundleIndex = bundleIndex,
                                            proposalId = proposalId
                                        )
                                    )
                                traceVotingStep(
                                    roundId = roundId,
                                    step = "storeCachedCommitmentBundle",
                                    bundleIndex = bundleIndex,
                                    proposalId = proposalId
                                ) {
                                    votingCryptoClient.storeCommitmentBundle(
                                        dbHandle = dbHandle,
                                        roundId = roundId,
                                        bundleIndex = bundleIndex,
                                        proposalId = proposalId,
                                        bundleJson = storedCommitment.bundleJson,
                                        vcTreePosition = vcTreePosition
                                    )
                                }
                                submitMissingShares(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    proposalId = proposalId,
                                    choiceId = choiceId,
                                    numOptions = proposal.options.size,
                                    singleShare = singleShare,
                                    submitAtDeadline = submitAtDeadline,
                                    commitmentJson = storedCommitment.bundleJson,
                                    commitmentBundle = storedCommitment.bundle,
                                    vcTreePosition = vcTreePosition,
                                    delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
                                )
                                traceVotingStep(
                                    roundId = roundId,
                                    step = "markCachedVoteSubmitted",
                                    bundleIndex = bundleIndex,
                                    proposalId = proposalId
                                ) {
                                    votingCryptoClient.markVoteSubmitted(
                                        dbHandle = dbHandle,
                                        roundId = roundId,
                                        bundleIndex = bundleIndex,
                                        proposalId = proposalId
                                    )
                                }
                                submittedBundles += bundleIndex
                                emitSubmittingProgress(bundleIndex, bundleProgress = 1.0)
                                return@repeat
                            }
                            Log.i(
                                TAG,
                                "Cached vote tx ${cachedVoteTxHash.txHash} for round $roundId " +
                                    "is not reusable ($cachedConfirmation); rebuilding commitment"
                            )
                        }

                        val syncedHeight =
                            traceVotingStep(
                                roundId = roundId,
                                step = "syncVoteTree",
                                bundleIndex = bundleIndex,
                                proposalId = proposalId
                            ) {
                                votingCryptoClient.syncVoteTree(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    nodeUrl = voteServerUrl
                                )
                            }
                        if (syncedHeight < 0) {
                            throw VotingSubmissionRecoverableException(
                                VotingErrors.VoteTreeSyncFailed(roundId)
                            )
                        }

                        val vanWitnessJson =
                            traceVotingStep(
                                roundId = roundId,
                                step = "generateVanWitnessJson",
                                bundleIndex = bundleIndex,
                                proposalId = proposalId
                            ) {
                                votingCryptoClient.generateVanWitnessJson(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    anchorHeight = syncedHeight.toInt()
                                )
                            }
                        val vanWitness = vanWitnessJson.toVanWitnessSummary()
                        val commitment =
                            traceVotingStep(
                                roundId = roundId,
                                step = "buildVoteCommitment",
                                bundleIndex = bundleIndex,
                                proposalId = proposalId
                            ) {
                                votingCryptoClient.buildVoteCommitment(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    hotkeySeed = hotkeySeed,
                                    proposalId = proposalId,
                                    choice = choiceId,
                                    numOptions = proposal.options.size,
                                    witnessJson = vanWitnessJson,
                                    vanPosition = vanWitness.position,
                                    anchorHeight = vanWitness.anchorHeight,
                                    networkId = networkId,
                                    accountIndex = accountIndex,
                                    singleShare = singleShare,
                                    proofProgress = { proofProgress ->
                                        emitSubmittingProgress(
                                            bundleIndex = bundleIndex,
                                            bundleProgress = proofProgress
                                        )
                                    }
                                )
                            }
                        traceVotingStep(
                            roundId = roundId,
                            step = "storeNewCommitmentBundle",
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        ) {
                            votingCryptoClient.storeCommitmentBundle(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId,
                                bundleJson = commitment.rawBundleJson,
                                vcTreePosition = 0L
                            )
                        }
                        val signature =
                            CastVoteSignature(
                                voteAuthSig =
                                    traceVotingStep(
                                        roundId = roundId,
                                        step = "signCastVote",
                                        bundleIndex = bundleIndex,
                                        proposalId = proposalId
                                    ) {
                                        votingCryptoClient.signCastVote(
                                            hotkeySeed = hotkeySeed,
                                            networkId = networkId,
                                            accountIndex = accountIndex,
                                            commitmentJson = commitment.rawBundleJson
                                        )
                                    }
                            )
                        val txResult =
                            votingApiProvider
                                .submitVoteCommitment(
                                    bundle = commitment.toVoteCommitmentBundle(),
                                    signature = signature
                                ).requireAccepted("Vote commitment transaction was rejected")
                        traceVotingStep(
                            roundId = roundId,
                            step = "storeVoteTxHash",
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        ) {
                            votingCryptoClient.storeVoteTxHash(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId,
                                txHash = txResult.txHash
                            )
                        }

                        val confirmation =
                            awaitTxConfirmation(txResult.txHash)
                                ?: throw VotingSubmissionRecoverableException(
                                    VotingErrors.TxConfirmationTimedOut(txResult.txHash)
                                )
                        confirmation.requireAccepted("Vote commitment transaction failed")

                        val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
                        traceVotingStep(
                            roundId = roundId,
                            step = "storeConfirmedVoteVanPosition",
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        ) {
                            votingCryptoClient.storeVanPosition(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                position = confirmedVanPosition
                            )
                        }
                        traceVotingStep(
                            roundId = roundId,
                            step = "storeConfirmedCommitmentBundle",
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        ) {
                            votingCryptoClient.storeCommitmentBundle(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId,
                                bundleJson = commitment.rawBundleJson,
                                vcTreePosition = vcTreePosition
                            )
                        }
                        submitMissingShares(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            choiceId = choiceId,
                            numOptions = proposal.options.size,
                            singleShare = singleShare,
                            submitAtDeadline = submitAtDeadline,
                            commitmentJson = commitment.rawBundleJson,
                            commitmentBundle = commitment.toVoteCommitmentBundle(),
                            vcTreePosition = vcTreePosition,
                            delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
                        )
                        traceVotingStep(
                            roundId = roundId,
                            step = "markNewVoteSubmitted",
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        ) {
                            votingCryptoClient.markVoteSubmitted(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId
                            )
                        }
                        submittedBundles += bundleIndex
                        emitSubmittingProgress(bundleIndex, bundleProgress = 1.0)
                    }

                    markProposalSubmissionComplete(accountUuidString, roundId, proposalId)
                    processedProposalCount++
                }

                val completedProposalCount =
                    votingRecoveryRepository
                        .get(accountUuidString, roundId)
                        ?.submittedProposalIds
                        ?.size
                        ?: totalChoices

                // Phase transitions track recovery state-machine progress and must run
                // whenever the loop completes, so that share-tracking can resume. The
                // user-facing "voted on this round" marker (`submittedAt` /
                // `markRoundSubmitted`) is gated separately on every expected proposal
                // having been accounted for, mirroring iOS `failCount == 0` semantics.
                votingRecoveryRepository.setPhase(
                    accountUuid = accountUuidString,
                    roundId = roundId,
                    phase = VotingRecoveryPhase.VOTES_SUBMITTED
                )
                votingRecoveryRepository.setPhase(
                    accountUuid = accountUuidString,
                    roundId = roundId,
                    phase = VotingRecoveryPhase.SHARES_SUBMITTED
                )
                if (processedProposalCount == totalChoices) {
                    votingRecoveryRepository.storeSubmittedAt(
                        accountUuid = accountUuidString,
                        roundId = roundId,
                        submittedAtEpochSeconds = Instant.now().epochSecond
                    )
                    votingSessionStore.markRoundSubmitted(
                        accountUuid = accountUuidString,
                        roundId = roundId,
                        proposalCount = completedProposalCount
                    )
                }
                votingSessionStore.clearDraftVotes(
                    accountUuid = accountUuidString,
                    roundId = roundId
                )
                votingShareTrackingScheduler.schedule(roundId)

                VotingSubmissionResult(submittedProposalCount = completedProposalCount)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                throw exception
            } finally {
                traceVotingStep(
                    roundId = roundId,
                    step = "closeVotingDb"
                ) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }

    private suspend fun markProposalSubmissionComplete(
        accountUuid: String,
        roundId: String,
        proposalId: Int
    ) {
        // Recovery is durable and written first; if the process dies before the
        // in-memory session store is pruned, the next launch replays this state.
        votingRecoveryRepository.markProposalSubmitted(
            accountUuid = accountUuid,
            roundId = roundId,
            proposalId = proposalId
        )
        votingSessionStore.clearDraftVote(
            accountUuid = accountUuid,
            roundId = roundId,
            proposalId = proposalId
        )
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
            ?: throw VotingSubmissionRecoverableException(VotingErrors.MissingHotkeySeed(roundId))
    }

    /**
     * Polls `fetchTxConfirmation` until a confirmation is returned or the attempt budget is
     * exhausted. Mirrors iOS `delegationTxConfirmationStatus` semantics:
     *
     * - `maxAttempts = TX_CONFIRMATION_RETRIES` (default, 45 × 2s ≈ 90s) — fresh-submit waits.
     * - `maxAttempts = 1` — single fetch, no sleep, returns null if the TX hasn't propagated.
     *   Used for the cached-delegation-hash recovery probe so a transient lookup miss does
     *   not stall the submission flow (iOS: `confirmationTimeout: 0`).
     *
     * Returns null when the TX is not seen within the budget; callers decide whether that is
     * fatal (fresh-submit) or a fall-through signal (recovery).
     */
    private suspend fun awaitTxConfirmation(
        txHash: String,
        maxAttempts: Int = TX_CONFIRMATION_RETRIES
    ): TxConfirmation? {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        repeat(maxAttempts) { attempt ->
            votingApiProvider.fetchTxConfirmation(txHash)?.let { return it }
            if (attempt + 1 < maxAttempts) {
                delay(TX_CONFIRMATION_POLL_MS)
            }
        }
        return null
    }

    private suspend fun probeCachedTx(txHash: String): TxConfirmationProbeResult {
        val confirmation =
            awaitTxConfirmation(txHash, maxAttempts = 1)
                ?: return TxConfirmationProbeResult.NotFound
        return if (confirmation.code == 0) {
            TxConfirmationProbeResult.Confirmed(confirmation)
        } else {
            TxConfirmationProbeResult.Rejected(confirmation.log)
        }
    }

    private suspend fun submitMissingShares(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int,
        choiceId: Int,
        numOptions: Int,
        singleShare: Boolean,
        submitAtDeadline: Long?,
        commitmentJson: String,
        commitmentBundle: VoteCommitmentBundle,
        vcTreePosition: Long,
        delegatedShareIndicesByTarget: MutableMap<ShareDelegationTarget, MutableSet<Int>>
    ) {
        val target = ShareDelegationTarget(bundleIndex = bundleIndex, proposalId = proposalId)
        val existingShareIndices = delegatedShareIndicesByTarget.getOrPut(target) { mutableSetOf() }
        val payloads =
            traceVotingStep(
                roundId = roundId,
                step = "buildSharePayloadsJson",
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) {
                votingCryptoClient.buildSharePayloadsJson(
                    encSharesJson = commitmentBundle.encShares.toEncryptedSharesJson(),
                    commitmentJson = commitmentJson,
                    voteDecision = choiceId,
                    numOptions = numOptions,
                    vcTreePosition = vcTreePosition,
                    singleShareMode = singleShare
                )
            }.toSharePayloads().map { payload ->
                payload.withSubmitAt(randomSubmitAt(submitAtDeadline))
            }
        val pendingPayloads =
            payloads.filterNot { payload ->
                payload.encShare.shareIndex in existingShareIndices
            }

        if (pendingPayloads.isEmpty()) {
            return
        }

        val delegationResults = delegateSharesWithRetry(pendingPayloads, roundId)
        delegationResults.forEach { info ->
            val payload =
                pendingPayloads.firstOrNull { candidate ->
                    candidate.encShare.shareIndex == info.shareIndex &&
                        candidate.proposalId == info.proposalId
                } ?: return@forEach
            val shareBlind =
                commitmentBundle.shareBlindFactors.getOrNull(info.shareIndex)
                    ?: error(
                        "Missing share blind for proposal $proposalId share ${info.shareIndex}"
                    )
            val nullifier =
                traceVotingStep(
                    roundId = roundId,
                    step = "computeShareNullifier",
                    bundleIndex = bundleIndex,
                    proposalId = info.proposalId,
                    shareIndex = info.shareIndex
                ) {
                    votingCryptoClient.computeShareNullifier(
                        voteCommitment = commitmentBundle.voteCommitment,
                        shareIndex = info.shareIndex,
                        blind = shareBlind
                    )
                }
            traceVotingStep(
                roundId = roundId,
                step = "recordShareDelegation",
                bundleIndex = bundleIndex,
                proposalId = info.proposalId,
                shareIndex = info.shareIndex
            ) {
                votingCryptoClient.recordShareDelegation(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    proposalId = info.proposalId,
                    shareIndex = info.shareIndex,
                    sentToUrls = info.acceptedByServers,
                    nullifier = nullifier,
                    submitAt = payload.submitAt
                )
            }
            existingShareIndices += info.shareIndex
        }
    }

    private suspend fun <T> traceVotingStep(
        roundId: String,
        step: String,
        bundleIndex: Int? = null,
        proposalId: Int? = null,
        shareIndex: Int? = null,
        block: suspend () -> T
    ): T {
        val context =
            buildString {
                append("round=").append(roundId)
                if (bundleIndex != null) append(" bundle=").append(bundleIndex)
                if (proposalId != null) append(" proposal=").append(proposalId)
                if (shareIndex != null) append(" share=").append(shareIndex)
            }
        Log.i(TAG, "Voting trace begin $step $context")
        return try {
            block().also {
                Log.i(TAG, "Voting trace end $step $context")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Voting trace failed $step $context", exception)
            throw exception
        }
    }

    private suspend fun delegateSharesWithRetry(
        payloads: List<SharePayload>,
        roundId: String
    ): List<DelegatedShareInfo> {
        var lastRetryableError: Exception? = null
        repeat(SHARE_DELEGATION_ATTEMPTS) { attempt ->
            try {
                return votingApiProvider.delegateShares(payloads, roundId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!exception.isShareDelegationExhaustion() && !exception.isTransientVotingInfrastructureFailure()) {
                    throw exception
                }
                lastRetryableError = exception
                if (attempt + 1 < SHARE_DELEGATION_ATTEMPTS) {
                    delay(SHARE_DELEGATION_RETRY_MS)
                }
            }
        }

        throw lastRetryableError ?: IllegalStateException("No voting server accepted share")
    }

    private fun Throwable.isShareDelegationExhaustion(): Boolean {
        val lower = message.orEmpty().lowercase()
        return lower.contains("no voting server accepted share") ||
            lower.contains("no reachable vote servers") ||
            lower.contains("all configured vote servers failed")
    }

    private fun Throwable.isTransientVotingInfrastructureFailure(): Boolean {
        val lower = message.orEmpty().lowercase()
        return lower.contains("http 5") ||
            lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("connect") ||
            lower.contains("connection") ||
            lower.contains("transport became inactive") ||
            lower.contains("grpcstatus") ||
            lower.contains("network")
    }

    private fun randomSubmitAt(deadlineEpochSeconds: Long?): Long {
        if (deadlineEpochSeconds == null) {
            return 0
        }

        val nowEpochSeconds = System.currentTimeMillis() / 1_000
        if (deadlineEpochSeconds <= nowEpochSeconds) {
            return 0
        }

        val window = deadlineEpochSeconds - nowEpochSeconds
        return nowEpochSeconds + Random.nextLong(until = window)
    }

    private suspend fun <T> runVotingAuthorizationStep(
        isKeystone: Boolean,
        block: suspend () -> T
    ): T =
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw exception.asVotingAuthorizationExceptionIfNeeded(isKeystone)
        }

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 1 else 0

    private fun TxResult.requireAccepted(fallbackMessage: String): TxResult {
        if (code != 0) {
            throw IllegalStateException(log.ifEmpty { fallbackMessage })
        }
        return this
    }

    private fun TxConfirmation.requireAccepted(fallbackMessage: String) {
        if (code != 0) {
            throw IllegalStateException(log.ifEmpty { fallbackMessage })
        }
    }

    private fun TxConfirmation.castVoteLeafPositions(): Pair<Int, Long> {
        val rawLeafIndex =
            event("cast_vote")
                ?.attribute("leaf_index")
                ?: error("Missing cast_vote leaf_index")
        val leafParts = rawLeafIndex.split(',')
        require(leafParts.size == 2) {
            "Malformed cast_vote leaf_index: $rawLeafIndex"
        }

        val vanPosition =
            leafParts[0].trim().toIntOrNull()
                ?: error("Malformed VAN leaf position: ${leafParts[0]}")
        val voteCommitmentPosition =
            leafParts[1].trim().toLongOrNull()
                ?: error("Malformed vote commitment leaf position: ${leafParts[1]}")

        return vanPosition to voteCommitmentPosition
    }

    private fun String.toVanWitnessSummary(): VanWitnessSummary {
        val json = JSONObject(this)
        return VanWitnessSummary(
            position = json.getInt("position"),
            anchorHeight = json.getInt("anchor_height")
        )
    }

    private data class VanWitnessSummary(
        val position: Int,
        val anchorHeight: Int
    )

    private data class ShareDelegationTarget(
        val bundleIndex: Int,
        val proposalId: Int
    )

    private companion object {
        const val TAG = "SubmitVotesUseCase"
        const val TX_CONFIRMATION_RETRIES = 45
        const val TX_CONFIRMATION_POLL_MS = 2_000L
        const val SHARE_DELEGATION_ATTEMPTS = 3
        const val SHARE_DELEGATION_RETRY_MS = 2_000L
    }
}

internal fun Exception.asVotingAuthorizationExceptionIfNeeded(isKeystone: Boolean): Exception =
    when {
        this is VotingSubmissionRecoverableException -> this
        this is VotingAuthorizationException -> this
        isKeystone -> VotingAuthorizationException(this)
        else -> this
    }

internal fun TxConfirmation.delegateVoteVanPosition(bundleIndex: Int): Int {
    val rawLeafIndex =
        event("delegate_vote")
            ?.attribute("leaf_index")
            ?: throw unexpectedSdkResponse("Missing delegate_vote leaf_index for bundle $bundleIndex")

    return rawLeafIndex.toIntOrNull()
        ?: throw unexpectedSdkResponse("Malformed delegate_vote leaf_index for bundle $bundleIndex: $rawLeafIndex")
}

private fun unexpectedSdkResponse(message: String) =
    VotingSubmissionRecoverableException(VotingErrors.UnexpectedSdkResponse(message))

internal fun calculateSubmittingBundleProgress(
    proposalIndex: Int,
    bundleIndex: Int,
    bundleCount: Int,
    totalChoices: Int,
    bundleProgress: Double
): Float {
    require(proposalIndex >= 0) { "proposalIndex must be non-negative" }
    require(bundleIndex >= 0) { "bundleIndex must be non-negative" }
    require(bundleCount > 0) { "bundleCount must be positive" }
    require(totalChoices > 0) { "totalChoices must be positive" }

    val completedBundles =
        proposalIndex * bundleCount +
            bundleIndex +
            bundleProgress.coerceIn(0.0, 1.0)
    val bundleTotal = totalChoices * bundleCount

    return (completedBundles / bundleTotal).toFloat().coerceIn(0f, 1f)
}
