package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.isLastMoment
import co.electriccoin.zcash.ui.common.model.voting.isSyntheticAbstainChoice
import co.electriccoin.zcash.ui.common.model.voting.selectVotingBundleNotesJson
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
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingDelegationPirPrecomputeKey
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
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

class VotingAuthorizationException(cause: Exception) : Exception(
    cause.message ?: "Voting authorization failed",
    cause
)

class SubmitVotesUseCase(
    private val votingConfigRepository: VotingConfigRepository,
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

            val selectedAccount = requireNotNull(getSelectedWalletAccount()) {
                "No selected wallet account is available"
            }
            val isKeystone = selectedAccount is KeystoneAccount
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            when (val preparation = prepareVotingRound(roundId)) {
                is VotingRoundPreparationResult.Ready -> Unit
                is VotingRoundPreparationResult.Ineligible ->
                    error("Wallet is not eligible for this vote")
                is VotingRoundPreparationResult.WalletSyncing ->
                    error(
                        "Wallet sync is below the voting snapshot height " +
                            "(${preparation.scannedHeight ?: 0}/${preparation.snapshotHeight})"
                    )
            }

            val currentConfig = requireNotNull(
                votingConfigRepository.currentConfig.value ?: votingConfigRepository.get()
            ) {
                "No active voting session is loaded"
            }
            val session = currentConfig.session
            val sessionRoundId = session.voteRoundId.toHex()
            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val serviceConfig = currentConfig.serviceConfig
            val voteServerUrls = serviceConfig.voteServers
                .map { endpoint -> endpoint.url.trimEnd('/') }
                .distinct()
            val voteServerUrl = voteServerUrls
                .firstOrNull()
                ?: error("Voting server URL is not configured")
            val pirServerUrl = pirSnapshotResolver.resolve(
                endpoints = serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                expectedSnapshotHeight = session.snapshotHeight
            )

            val recovery = requireNotNull(votingRecoveryRepository.get(accountUuidString, roundId)) {
                "Voting round $roundId has not been prepared"
            }
            votingRecoveryRepository.storeVoteServerUrls(accountUuidString, roundId, voteServerUrls)
            votingRecoveryRepository.storeVoteEndEpochSeconds(accountUuidString, roundId, session.voteEndTime.epochSecond)
            val recoveryBundleCount = recovery.bundleCount
            val hotkeySeed = getHotkeySeed(accountUuidString, roundId, recovery)

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizer.getWalletDbPath()
            val votingDbPath = File(walletDbPath)
                .parentFile
                ?.resolve("voting.sqlite3")
                ?.absolutePath
                ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val senderSeed = if (isKeystone) null else getWalletSeedBytes()
            val accountIndex = selectedAccount.hdAccountIndex.index.toInt()
            val accountUfvk = selectedAccount.sdkAccount.ufvk
            val seedFingerprint = selectedAccount.sdkAccount.seedFingerprint
            val allNotesJson = votingCryptoClient.getWalletNotesJson(
                walletDbPath = walletDbPath,
                snapshotHeight = session.snapshotHeight,
                networkId = networkId,
                accountUuidBytes = selectedAccount.sdkAccount.accountUuid.value
            )

            val singleShare = session.isLastMoment()
            val submitAtDeadline = session.shareSubmissionDeadlineEpochSeconds(singleShare)
            val sortedChoices = choices.toSortedMap()
            val totalChoices = sortedChoices.size

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            var isVotingAuthorizationPhase = false
            try {
                votingCryptoClient.setWalletId(dbHandle, selectedAccount.sdkAccount.accountUuid.toString())
                val bundleCount = recoveryBundleCount
                    ?: votingCryptoClient.getBundleCount(dbHandle, roundId)
                        .takeIf { count -> count >= 0 }
                    ?: error("Voting round $roundId has no prepared bundle count")
                val submittedBundleIndicesByProposal = votingCryptoClient.getVotes(
                    dbHandle = dbHandle,
                    roundId = roundId
                ).filter { vote ->
                    vote.submitted
                }.groupBy { vote ->
                    vote.proposalId
                }.mapValuesTo(mutableMapOf()) { (_, votes) ->
                    votes.mapTo(mutableSetOf()) { vote -> vote.bundleIndex }
                }
                val delegatedShareIndicesByTarget = votingCryptoClient.getShareDelegations(
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
                    isVotingAuthorizationPhase = true
                    repeat(bundleCount) { bundleIndex ->
                        onProgress(
                            VotingSubmissionProgress.Authorizing(
                                progress = bundleIndex.toFloat() / bundleCount.coerceAtLeast(1)
                            )
                        )

                        val cachedDelegationTxHash = votingCryptoClient.getDelegationTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex
                        )
                        if (cachedDelegationTxHash is VotingTxHashLookup.Present) {
                            val confirmation = awaitTxConfirmation(cachedDelegationTxHash.txHash)
                            require(confirmation.code == 0) {
                                confirmation.log.ifEmpty { "Delegation transaction failed" }
                            }

                            val vanPosition = confirmation.event("delegate_vote")
                                ?.attribute("leaf_index")
                                ?.toIntOrNull()
                                ?: error("Missing delegate_vote leaf_index for bundle $bundleIndex")
                            votingCryptoClient.storeVanPosition(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                position = vanPosition
                            )
                            return@repeat
                        }

                        val witnessesJson = votingCryptoClient.generateNoteWitnessesJson(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            walletDbPath = walletDbPath,
                            notesJson = allNotesJson
                        )
                        votingCryptoClient.storeWitnesses(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            witnessesJson = witnessesJson
                        )

                        val bundleNotesJson = allNotesJson.selectVotingBundleNotesJson(witnessesJson)
                        val precomputeResult = votingProofPrecomputeRepository.awaitDelegationPirPrecompute(
                            VotingDelegationPirPrecomputeKey(
                                accountUuid = accountUuidString,
                                roundId = roundId,
                                bundleIndex = bundleIndex
                            )
                        )
                        precomputeResult?.onFailure { throwable ->
                            Log.w(TAG, "Voting PIR precompute failed for round $roundId bundle $bundleIndex", throwable)
                        }
                        if (!isKeystone && precomputeResult == null) {
                            votingCryptoClient.buildGovernancePczt(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                ufvk = requireNotNull(accountUfvk) {
                                    "Software wallet account is missing UFVK for voting bundle $bundleIndex"
                                },
                                networkId = networkId,
                                accountIndex = accountIndex,
                                notesJson = bundleNotesJson,
                                hotkeyRawSeed = hotkeySeed,
                                seedFingerprint = requireNotNull(seedFingerprint) {
                                    "Software wallet account is missing seed fingerprint for voting bundle $bundleIndex"
                                },
                                roundName = session.title
                            )
                        }
                        votingCryptoClient.buildAndProveDelegation(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            pirServerUrl = pirServerUrl,
                            networkId = networkId,
                            notesJson = bundleNotesJson,
                            hotkeyRawSeed = hotkeySeed,
                            proofProgress = { progress ->
                                onProgress(
                                    VotingSubmissionProgress.Authorizing(
                                        progress = ((bundleIndex + progress.coerceIn(0.0, 1.0)) /
                                            bundleCount.coerceAtLeast(1)).toFloat()
                                    )
                                )
                            }
                        )
                        votingRecoveryRepository.setPhase(
                            accountUuid = accountUuidString,
                            roundId = roundId,
                            phase = VotingRecoveryPhase.DELEGATION_PROVED
                        )

                        val submission = if (isKeystone) {
                            val keystoneSignature = recovery.keystoneBundleSignatures[bundleIndex]
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
                            val keystoneSignature = recovery.keystoneBundleSignatures[bundleIndex]
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
                        val txResult = votingApiProvider.submitDelegation(submission.toDelegationRegistration())
                        require(txResult.code == 0) {
                            txResult.log.ifEmpty { "Delegation transaction was rejected" }
                        }
                        votingCryptoClient.storeDelegationTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            txHash = txResult.txHash
                        )

                        val confirmation = awaitTxConfirmation(txResult.txHash)
                        require(confirmation.code == 0) {
                            confirmation.log.ifEmpty { "Delegation transaction failed" }
                        }

                        val vanPosition = confirmation.event("delegate_vote")
                            ?.attribute("leaf_index")
                            ?.toIntOrNull()
                            ?: error("Missing delegate_vote leaf_index for bundle $bundleIndex")
                        votingCryptoClient.storeVanPosition(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            position = vanPosition
                        )
                    }

                    votingRecoveryRepository.setPhase(
                        accountUuid = accountUuidString,
                        roundId = roundId,
                        phase = VotingRecoveryPhase.DELEGATION_SUBMITTED
                    )
                    isVotingAuthorizationPhase = false
                }

                val proposalSelections = sortedChoices.mapNotNull { (proposalId, choiceId) ->
                    val proposal = session.proposals.firstOrNull { it.id == proposalId }
                        ?: error("Unknown proposal id $proposalId for round $roundId")
                    if (proposal.options.none { option -> option.id == choiceId }) {
                        null
                    } else {
                        proposalId to VotingProposalSelection(
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

                sortedChoices.entries.forEachIndexed { proposalIndex, (proposalId, choiceId) ->
                    val proposal = session.proposals.firstOrNull { it.id == proposalId }
                        ?: error("Unknown proposal id $proposalId for round $roundId")
                    val progressBase = proposalIndex + 1

                    val submittedBundles = submittedBundleIndicesByProposal
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
                        return@forEachIndexed
                    }
                    require(hasOnWireOption) {
                        "Unknown vote option $choiceId for proposal $proposalId"
                    }

                    repeat(bundleCount) { bundleIndex ->
                        if (bundleIndex in submittedBundles) {
                            return@repeat
                        }

                        val completedBundles = proposalIndex * bundleCount + bundleIndex + 1
                        val bundleTotal = totalChoices * bundleCount.coerceAtLeast(1)
                        onProgress(
                            VotingSubmissionProgress.Submitting(
                                current = progressBase,
                                total = totalChoices,
                                progress = completedBundles.toFloat() / bundleTotal
                            )
                        )

                        val cachedVoteTxHash = votingCryptoClient.getVoteTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        )

                        if (cachedVoteTxHash is VotingTxHashLookup.Present) {
                            val confirmation = awaitTxConfirmation(cachedVoteTxHash.txHash)
                            require(confirmation.code == 0) {
                                confirmation.log.ifEmpty { "Vote commitment transaction failed" }
                            }

                            val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
                            votingCryptoClient.storeVanPosition(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                position = confirmedVanPosition
                            )

                            val storedCommitment = requireNotNull(
                                votingCryptoClient.getCommitmentBundle(
                                    dbHandle = dbHandle,
                                    roundId = roundId,
                                    bundleIndex = bundleIndex,
                                    proposalId = proposalId
                                )
                            ) {
                                "Missing stored vote commitment bundle for round $roundId bundle $bundleIndex proposal $proposalId"
                            }
                            votingCryptoClient.storeCommitmentBundle(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId,
                                bundleJson = storedCommitment.bundleJson,
                                vcTreePosition = vcTreePosition
                            )
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
                            votingCryptoClient.markVoteSubmitted(
                                dbHandle = dbHandle,
                                roundId = roundId,
                                bundleIndex = bundleIndex,
                                proposalId = proposalId
                            )
                            submittedBundles += bundleIndex
                            return@repeat
                        }

                        val syncedHeight = votingCryptoClient.syncVoteTree(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            nodeUrl = voteServerUrl
                        )
                        check(syncedHeight >= 0) {
                            "Failed to synchronize vote tree for round $roundId"
                        }

                        val vanWitnessJson = votingCryptoClient.generateVanWitnessJson(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            anchorHeight = syncedHeight.toInt()
                        )
                        val vanWitness = vanWitnessJson.toVanWitnessSummary()
                        val commitment = votingCryptoClient.buildVoteCommitment(
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
                            singleShare = singleShare,
                            proofProgress = { proofProgress ->
                                onProgress(
                                    VotingSubmissionProgress.Submitting(
                                        current = progressBase,
                                        total = totalChoices,
                                        progress = ((proposalIndex * bundleCount + bundleIndex +
                                            proofProgress.coerceIn(0.0, 1.0)) / bundleTotal).toFloat()
                                    )
                                )
                            }
                        )
                        votingCryptoClient.storeCommitmentBundle(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            bundleJson = commitment.rawBundleJson,
                            vcTreePosition = 0L
                        )
                        val signature = CastVoteSignature(
                            voteAuthSig = votingCryptoClient.signCastVote(
                                hotkeySeed = hotkeySeed,
                                networkId = networkId,
                                roundId = roundId,
                                rVpk = commitment.rVpk,
                                vanNullifier = commitment.vanNullifier,
                                vanNew = commitment.voteAuthorityNoteNew,
                                voteCommitment = commitment.voteCommitment,
                                proposalId = proposalId,
                                anchorHeight = commitment.anchorHeight,
                                alphaV = commitment.alphaV
                            )
                        )
                        val txResult = votingApiProvider.submitVoteCommitment(
                            bundle = commitment.toVoteCommitmentBundle(),
                            signature = signature
                        )
                        require(txResult.code == 0) {
                            txResult.log.ifEmpty { "Vote commitment transaction was rejected" }
                        }
                        votingCryptoClient.storeVoteTxHash(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            txHash = txResult.txHash
                        )

                        val confirmation = awaitTxConfirmation(txResult.txHash)
                        require(confirmation.code == 0) {
                            confirmation.log.ifEmpty { "Vote commitment transaction failed" }
                        }

                        val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
                        votingCryptoClient.storeVanPosition(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            position = confirmedVanPosition
                        )
                        votingCryptoClient.storeCommitmentBundle(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId,
                            bundleJson = commitment.rawBundleJson,
                            vcTreePosition = vcTreePosition
                        )
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
                        votingCryptoClient.markVoteSubmitted(
                            dbHandle = dbHandle,
                            roundId = roundId,
                            bundleIndex = bundleIndex,
                            proposalId = proposalId
                        )
                        submittedBundles += bundleIndex
                    }

                    markProposalSubmissionComplete(accountUuidString, roundId, proposalId)
                }

                val completedProposalCount = votingRecoveryRepository
                    .get(accountUuidString, roundId)
                    ?.submittedProposalIds
                    ?.size
                    ?: totalChoices

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
                votingSessionStore.clearDraftVotes(
                    accountUuid = accountUuidString,
                    roundId = roundId
                )
                votingShareTrackingScheduler.schedule(roundId)

                VotingSubmissionResult(submittedProposalCount = completedProposalCount)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (isVotingAuthorizationPhase) {
                    throw VotingAuthorizationException(exception)
                }
                throw exception
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
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
            ?: error("Voting round $roundId has no stored hotkey seed")
    }

    private suspend fun awaitTxConfirmation(txHash: String): TxConfirmation {
        repeat(TX_CONFIRMATION_RETRIES) {
            votingApiProvider.fetchTxConfirmation(txHash)?.let { confirmation ->
                return confirmation
            }
            delay(TX_CONFIRMATION_POLL_MS)
        }

        error("Transaction $txHash was not confirmed in time")
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
        val payloads = votingCryptoClient.buildSharePayloadsJson(
            encSharesJson = commitmentBundle.encShares.toEncryptedSharesJson(),
            commitmentJson = commitmentJson,
            voteDecision = choiceId,
            numOptions = numOptions,
            vcTreePosition = vcTreePosition,
            singleShareMode = singleShare
        ).toSharePayloads().map { payload ->
            payload.withSubmitAt(randomSubmitAt(submitAtDeadline))
        }
        val pendingPayloads = payloads.filterNot { payload ->
            payload.encShare.shareIndex in existingShareIndices
        }

        if (pendingPayloads.isEmpty()) {
            return
        }

        val delegationResults = votingApiProvider.delegateShares(pendingPayloads, roundId)
        delegationResults.forEach { info ->
            val payload = pendingPayloads.firstOrNull { candidate ->
                candidate.encShare.shareIndex == info.shareIndex &&
                    candidate.proposalId == info.proposalId
            } ?: return@forEach
            val shareBlind = commitmentBundle.shareBlindFactors.getOrNull(info.shareIndex)
                ?: error(
                    "Missing share blind for proposal $proposalId share ${info.shareIndex}"
                )
            val nullifier = votingCryptoClient.computeShareNullifier(
                voteCommitment = commitmentBundle.voteCommitment,
                shareIndex = info.shareIndex,
                blind = shareBlind
            )
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
            existingShareIndices += info.shareIndex
        }
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

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 1 else 0

    private fun TxConfirmation.castVoteLeafPositions(): Pair<Int, Long> {
        val rawLeafIndex = event("cast_vote")
            ?.attribute("leaf_index")
            ?: error("Missing cast_vote leaf_index")
        val leafParts = rawLeafIndex.split(',')
        require(leafParts.size == 2) {
            "Malformed cast_vote leaf_index: $rawLeafIndex"
        }

        val vanPosition = leafParts[0].trim().toIntOrNull()
            ?: error("Malformed VAN leaf position: ${leafParts[0]}")
        val voteCommitmentPosition = leafParts[1].trim().toLongOrNull()
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
    }
}
