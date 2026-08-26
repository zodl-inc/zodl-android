package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLatest
import co.electriccoin.zcash.ui.common.model.voting.CommitmentTreeLeafPage
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmationProbeResult
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.isDelegationSetupOverwrite
import co.electriccoin.zcash.ui.common.model.voting.isLastMoment
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import co.electriccoin.zcash.ui.common.model.voting.toDelegationRegistration
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
import co.electriccoin.zcash.ui.common.repository.toCanonicalUuidString
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Base64

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
    private class VotingSubmitContext(
        val roundId: String,
        val accountUuidString: String,
        val accountUuidCanonical: String,
        val recovery: VotingRecoverySnapshot,
        val session: VotingSession,
        val voteServerUrl: String,
        val walletDbPath: String,
        val votingDbPath: String,
        val networkId: Int,
        val senderSeed: ByteArray?,
        val accountIndex: Int,
        val accountUfvk: String?,
        val seedFingerprint: ByteArray?,
        val allNotesJson: String,
        val hotkeySeed: ByteArray,
        val isKeystone: Boolean,
        val pirServerUrl: String,
        val pirLayout: VotingPirLayout,
        val singleShare: Boolean,
        val sortedChoices: Map<Int, Int>,
        val totalChoices: Int
    )

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
            val accountUuidCanonical = selectedAccount.sdkAccount.accountUuid.toCanonicalUuidString()

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
            votingRecoveryRepository.storeVoteEndEpochSeconds(
                accountUuidString,
                roundId,
                session.voteEndTime.epochSecond
            )
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

            val singleShare = recovery.singleShareMode ?: session.isLastMoment()
            val sortedChoices = choices.toSortedMap()
            val totalChoices = sortedChoices.size

            val context =
                VotingSubmitContext(
                    roundId = roundId,
                    accountUuidString = accountUuidString,
                    accountUuidCanonical = accountUuidCanonical,
                    recovery = recovery,
                    session = session,
                    voteServerUrl = voteServerUrl,
                    walletDbPath = walletDbPath,
                    votingDbPath = votingDbPath,
                    networkId = networkId,
                    senderSeed = senderSeed,
                    accountIndex = accountIndex,
                    accountUfvk = accountUfvk,
                    seedFingerprint = seedFingerprint,
                    allNotesJson = allNotesJson,
                    hotkeySeed = hotkeySeed,
                    isKeystone = isKeystone,
                    pirServerUrl = pirServerUrl,
                    pirLayout = serviceConfig.pirLayout.requireKnownPolyLen(),
                    singleShare = singleShare,
                    sortedChoices = sortedChoices,
                    totalChoices = totalChoices
                )

            val dbHandle = votingCryptoClient.openVotingDb(context.votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at ${context.votingDbPath}" }

            try {
                votingCryptoClient.setWalletId(
                    dbHandle,
                    context.accountUuidString,
                    context.networkId
                )
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

                if (context.recovery.needsDelegationSubmission()) {
                    submitDelegationBundles(
                        context = context,
                        dbHandle = dbHandle,
                        bundleCount = bundleCount,
                        onProgress = onProgress
                    )
                }

                val processedProposalCount =
                    submitVoteCommitmentsAndShares(
                        context = context,
                        dbHandle = dbHandle,
                        bundleCount = bundleCount,
                        submittedBundleIndicesByProposal = submittedBundleIndicesByProposal,
                        delegatedShareIndicesByTarget = delegatedShareIndicesByTarget,
                        onProgress = onProgress
                    )

                val completedProposalCount =
                    votingRecoveryRepository
                        .get(context.accountUuidString, context.roundId)
                        ?.submittedProposalIds
                        ?.size
                        ?: context.totalChoices

                // Phase transitions track recovery state-machine progress and must run
                // whenever the loop completes, so that share-tracking can resume. The
                // user-facing "voted on this round" marker (`submittedAt` /
                // `markRoundSubmitted`) is gated separately on every expected proposal
                // having been accounted for, mirroring iOS `failCount == 0` semantics.
                votingRecoveryRepository.setPhase(
                    accountUuid = context.accountUuidString,
                    roundId = context.roundId,
                    phase = VotingRecoveryPhase.VOTES_SUBMITTED
                )
                votingRecoveryRepository.setPhase(
                    accountUuid = context.accountUuidString,
                    roundId = context.roundId,
                    phase = VotingRecoveryPhase.SHARES_SUBMITTED
                )
                if (processedProposalCount == context.totalChoices) {
                    votingRecoveryRepository.storeSubmittedAt(
                        accountUuid = context.accountUuidString,
                        roundId = context.roundId,
                        submittedAtEpochSeconds = Instant.now().epochSecond
                    )
                    votingSessionStore.markRoundSubmitted(
                        accountUuid = context.accountUuidString,
                        roundId = context.roundId,
                        proposalCount = completedProposalCount
                    )
                }
                votingSessionStore.clearDraftVotes(
                    accountUuid = context.accountUuidString,
                    roundId = context.roundId
                )
                votingShareTrackingScheduler.schedule(context.roundId)

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

    private fun VotingRecoverySnapshot.needsDelegationSubmission(): Boolean =
        phase != VotingRecoveryPhase.DELEGATION_SUBMITTED &&
            phase != VotingRecoveryPhase.VOTES_SUBMITTED &&
            phase != VotingRecoveryPhase.SHARES_SUBMITTED

    private suspend fun submitDelegationBundles(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleCount: Int,
        onProgress: (VotingSubmissionProgress) -> Unit
    ) {
        repeat(bundleCount) { bundleIndex ->
            submitSingleDelegationBundle(context, dbHandle, bundleIndex, bundleCount, onProgress)
        }

        votingRecoveryRepository.setPhase(
            accountUuid = context.accountUuidString,
            roundId = context.roundId,
            phase = VotingRecoveryPhase.DELEGATION_SUBMITTED
        )
    }

    private suspend fun currentDelegationPhase(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): DelegationPhase? =
        votingCryptoClient
            .delegationPhases(dbHandle, roundId)
            .firstOrNull { bundle -> bundle.bundleIndex == bundleIndex }
            ?.phase

    @Suppress("LongMethod")
    private suspend fun buildDelegationProofIfNeeded(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        bundleCount: Int,
        onProgress: (VotingSubmissionProgress) -> Unit
    ) {
        val roundId = context.roundId
        if (currentDelegationPhase(dbHandle, roundId, bundleIndex).let {
                it == DelegationPhase.SUBMITTED || it == DelegationPhase.CONFIRMED
            }
        ) {
            return
        }
        val witnessesJson =
            votingCryptoClient.generateNoteWitnessesJson(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                walletDbPath = context.walletDbPath,
                networkId = context.networkId,
                notesJson = context.allNotesJson
            )
        votingCryptoClient.storeWitnesses(
            dbHandle = dbHandle,
            roundId = roundId,
            bundleIndex = bundleIndex,
            notesJson = context.allNotesJson,
            witnessesJson = witnessesJson
        )

        val precomputeResult =
            votingProofPrecomputeRepository.awaitDelegationPirPrecompute(
                VotingDelegationPirPrecomputeKey(
                    accountUuid = context.accountUuidString,
                    roundId = roundId,
                    bundleIndex = bundleIndex
                )
            )
        precomputeResult?.onFailure { throwable ->
            Log.w(TAG, "Voting PIR precompute failed for round $roundId bundle $bundleIndex", throwable)
        }
        // Whether THIS call just wrote fresh PCZT/alpha for this bundle (a construct that
        // succeeds, as opposed to the crate refusing to overwrite already-intact data). This
        // matters because a stale `proofs` row can survive a setup reset (resetVotingSessionState
        // clears `bundles` columns but not the `proofs` table) — if construct just wrote fresh
        // alpha, any existing proof is for the OLD alpha and must be disregarded regardless of
        // what the phase read below says. Precompute's outcome is intentionally never consulted
        // here: it's a best-effort cache warm, not a signal for whether setup is done (a prior
        // version treated a swallowed background-race "success" as "setup already built", which
        // is exactly what left bundle 1's alpha NULL and crashed build_and_prove_delegation).
        var setupJustBuilt = false
        if (!context.isKeystone) {
            runCatching {
                votingCryptoClient.buildGovernancePcztFromSeed(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    ufvk =
                        requireNotNull(context.accountUfvk) {
                            "Software wallet account is missing UFVK for voting bundle $bundleIndex"
                        },
                    networkId = context.networkId,
                    accountIndex = context.accountIndex,
                    notesJson = context.allNotesJson,
                    walletSeed =
                        requireNotNull(context.senderSeed) {
                            "Software wallet seed is missing for voting bundle $bundleIndex"
                        },
                    hotkeySeed = context.hotkeySeed,
                    seedFingerprint =
                        requireNotNull(context.seedFingerprint) {
                            "Software wallet account is missing seed fingerprint for voting bundle $bundleIndex"
                        },
                    roundName = context.session.title
                )
            }.onSuccess {
                setupJustBuilt = true
            }.recoverCatching { throwable ->
                // The crate refuses to silently overwrite already-persisted PCZT fields with
                // different data — this bundle's setup is present and intact, nothing to do.
                if (!throwable.isDelegationSetupOverwrite()) throw throwable
            }.getOrThrow()
        }

        val alreadyProved =
            !setupJustBuilt &&
                bundleIndex !in context.recovery.rebuiltSinceProofBundles &&
                currentDelegationPhase(dbHandle, roundId, bundleIndex).let {
                    it == DelegationPhase.PROVED || it == DelegationPhase.SUBMITTED || it == DelegationPhase.CONFIRMED
                }
        if (!alreadyProved) {
            val fvkBytes =
                votingCryptoClient.extractOrchardFvkFromUfvk(
                    ufvk =
                        requireNotNull(context.accountUfvk) {
                            "Account is missing UFVK for voting bundle $bundleIndex"
                        },
                    networkId = context.networkId
                )
            runVotingAuthorizationStep(context.isKeystone) {
                votingCryptoClient.buildAndProveDelegation(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    pirServerUrl = context.pirServerUrl,
                    pirLayout = context.pirLayout,
                    notesJson = context.allNotesJson,
                    fvkBytes = fvkBytes,
                    hotkeySeed = context.hotkeySeed,
                    seedFingerprint =
                        requireNotNull(context.seedFingerprint) {
                            "Account is missing seed fingerprint for voting bundle $bundleIndex"
                        },
                    accountIndex = context.accountIndex,
                    roundName = context.session.title,
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
            // A fresh proof now matches the current alpha, so any earlier rebuild-since-proof
            // flag for this bundle (see VotingKeystoneRepository.createPcztEncoder) is stale.
            votingRecoveryRepository.clearBundleRebuiltSinceProof(
                accountUuid = context.accountUuidString,
                roundId = roundId,
                bundleIndex = bundleIndex
            )
        }
    }

    @Suppress("LongMethod")
    private suspend fun submitSingleDelegationBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        bundleCount: Int,
        onProgress: (VotingSubmissionProgress) -> Unit
    ) {
        val roundId = context.roundId
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
                return
            }
            // No usable cached confirmation (not yet propagated, failed on-chain,
            // or missing leaf_index) — fall through and re-run delegation from
            // scratch for this bundle.
        }

        // A resumed round may already have this specific bundle CONFIRMED — nothing left to do.
        // Per-bundle phase, not round-level: a multi-bundle round can have bundle 0 confirmed
        // while bundle 1 is still pending, and the round-level phase alone can't tell those apart.
        //
        // SUBMITTED is deliberately NOT short-circuited here (unlike the check above, which mirrors
        // this one for a different purpose): SUBMITTED means delegation_tx_hash is set but
        // van_leaf_position isn't yet — the normal in-flight state while awaiting confirmation, not
        // an edge case. Falling through is safe: buildDelegationProofIfNeeded no-ops for an
        // already-SUBMITTED bundle (see its own phase check). A software-wallet retry may have a
        // different RedPallas signature and transaction hash even though the persisted VAN
        // commitment is unchanged, so the submit path below reconciles a spent-nullifier response
        // against that commitment. It then stores van_leaf_position instead of leaving it unset
        // (which previously wedged the round downstream in submitFreshVoteBundle → syncVoteTree →
        // generateVanWitnessJson).
        if (currentDelegationPhase(dbHandle, roundId, bundleIndex) == DelegationPhase.CONFIRMED) {
            return
        }

        buildDelegationProofIfNeeded(context, dbHandle, bundleIndex, bundleCount, onProgress)
        votingRecoveryRepository.setPhase(
            accountUuid = context.accountUuidString,
            roundId = roundId,
            phase = VotingRecoveryPhase.DELEGATION_PROVED
        )

        val submissionResolution =
            runVotingAuthorizationStep(context.isKeystone) {
                val submission =
                    if (context.isKeystone) {
                        val keystoneSignature =
                            context.recovery.keystoneBundleSignatures[bundleIndex]
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
                            walletDbPath = context.walletDbPath,
                            accountUuid = context.accountUuidCanonical,
                            hotkeySeed = context.hotkeySeed,
                            roundName = context.session.title,
                            senderSeed = requireNotNull(context.senderSeed)
                        )
                    }
                if (context.isKeystone) {
                    val keystoneSignature =
                        context.recovery.keystoneBundleSignatures[bundleIndex]
                            ?: error("Keystone signature is missing for voting bundle $bundleIndex")
                    val expectedSpendAuthSig = keystoneSignature.decodeSpendAuthSig()
                    require(submission.spendAuthSig.contentEquals(expectedSpendAuthSig)) {
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
                val registration = submission.toDelegationRegistration()
                val result =
                    try {
                        votingApiProvider.submitDelegation(registration)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        val recoveredPosition =
                            findPersistedVanPosition(
                                context = context,
                                expectedVanCmx = registration.vanCmx
                            )
                        if (recoveredPosition != null) {
                            return@runVotingAuthorizationStep DelegationSubmissionResolution.ConfirmedVan(
                                recoveredPosition
                            )
                        }
                        throw exception
                    }

                reconcileDelegationTransactionResult(
                    result = result,
                    bundleIndex = bundleIndex,
                    rejectionMessage = "Delegation transaction was rejected",
                    fetchTxConfirmation = votingApiProvider::fetchTxConfirmation,
                    findVanPosition = {
                        findPersistedVanPosition(
                            context = context,
                            expectedVanCmx = registration.vanCmx
                        )
                    }
                )
            }
        if (submissionResolution is DelegationSubmissionResolution.ConfirmedVan) {
            submissionResolution.txHash?.let { txHash ->
                votingCryptoClient.storeDelegationTxHash(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    txHash = txHash
                )
            }
            votingCryptoClient.storeVanPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                position = submissionResolution.position
            )
            return
        }
        val acceptedTransaction =
            (submissionResolution as DelegationSubmissionResolution.AcceptedTransaction).transaction
        votingCryptoClient.storeDelegationTxHash(
            dbHandle = dbHandle,
            roundId = roundId,
            bundleIndex = bundleIndex,
            txHash = acceptedTransaction.txHash
        )

        val confirmation =
            acceptedTransaction.confirmation
                ?: runVotingAuthorizationStep(context.isKeystone) {
                    awaitTxConfirmation(acceptedTransaction.txHash)
                        ?: throw VotingSubmissionRecoverableException(
                            VotingErrors.TxConfirmationTimedOut(acceptedTransaction.txHash)
                        )
                }
        runVotingAuthorizationStep(context.isKeystone) {
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

    private suspend fun findPersistedVanPosition(
        context: VotingSubmitContext,
        expectedVanCmx: ByteArray
    ): Int? {
        val voteChainStartHeight = context.session.createdAtHeight.coerceAtLeast(0)
        return try {
            findVanCommitmentPosition(
                roundId = context.roundId,
                startHeight = voteChainStartHeight,
                expectedVanCmx = expectedVanCmx,
                fetchLatest = votingApiProvider::fetchCommitmentTreeLatest,
                fetchLeafPage = votingApiProvider::fetchCommitmentTreeLeafPage
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to reconcile persisted VAN commitment for round ${context.roundId}", exception)
            null
        }
    }

    private suspend fun submitVoteCommitmentsAndShares(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleCount: Int,
        submittedBundleIndicesByProposal: MutableMap<Int, MutableSet<Int>>,
        delegatedShareIndicesByTarget: MutableMap<ShareDelegationTarget, MutableSet<Int>>,
        onProgress: (VotingSubmissionProgress) -> Unit
    ): Int {
        val roundId = context.roundId
        val proposalSelections =
            context.sortedChoices
                .mapNotNull { (proposalId, choiceId) ->
                    val proposal =
                        context.session.proposals.firstOrNull { it.id == proposalId }
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
        val latestRecovery =
            votingRecoveryRepository.get(context.accountUuidString, roundId)
                ?: context.recovery
        val conflictingProposalId =
            latestRecovery.proposalSelections.entries
                .firstOrNull { (proposalId, lockedSelection) ->
                    val requestedSelection = proposalSelections[proposalId]
                    if (proposalId in latestRecovery.submittedProposalIds) {
                        requestedSelection != null && requestedSelection != lockedSelection
                    } else {
                        requestedSelection != lockedSelection
                    }
                }?.key
        if (conflictingProposalId != null) {
            throw VotingSubmissionRecoverableException(
                VotingErrors.ConflictingProposalSelection(
                    roundId = roundId,
                    proposalId = conflictingProposalId
                )
            )
        }
        if (proposalSelections.isNotEmpty()) {
            votingRecoveryRepository.storeProposalSelections(
                accountUuid = context.accountUuidString,
                roundId = roundId,
                proposalSelections = proposalSelections
            )
        }
        votingRecoveryRepository.storeSingleShareMode(
            accountUuid = context.accountUuidString,
            roundId = roundId,
            singleShareMode = context.singleShare
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
        context.sortedChoices.entries.forEachIndexed { proposalIndex, (proposalId, choiceId) ->
            val proposal =
                context.session.proposals.firstOrNull { it.id == proposalId }
                    ?: error("Unknown proposal id $proposalId for round $roundId")
            val progressBase = proposalIndex + 1

            fun emitSubmittingProgress(
                bundleIndex: Int,
                bundleProgress: Double
            ) {
                onProgress(
                    VotingSubmissionProgress.Submitting(
                        current = progressBase,
                        total = context.totalChoices,
                        progress =
                            calculateSubmittingBundleProgress(
                                proposalIndex = proposalIndex,
                                bundleIndex = bundleIndex,
                                bundleCount = bundleCount,
                                totalChoices = context.totalChoices,
                                bundleProgress = bundleProgress
                            )
                    )
                )
            }

            val submittedBundles =
                submittedBundleIndicesByProposal
                    .getOrPut(proposalId) { mutableSetOf() }

            if (proposalId in context.recovery.submittedProposalIds && submittedBundles.size >= bundleCount) {
                onProgress(
                    VotingSubmissionProgress.Submitting(
                        current = progressBase,
                        total = context.totalChoices,
                        progress = progressBase.toFloat() / context.totalChoices.coerceAtLeast(1)
                    )
                )
                markProposalSubmissionComplete(context.accountUuidString, roundId, proposalId)
                processedProposalCount++
                return@forEachIndexed
            }

            require(proposal.options.any { option -> option.id == choiceId }) {
                "Unknown vote option $choiceId for proposal $proposalId"
            }

            repeat(bundleCount) { bundleIndex ->
                if (bundleIndex in submittedBundles) {
                    return@repeat
                }

                emitSubmittingProgress(bundleIndex, 0.0)

                if (
                    submitCachedVoteIfReusable(
                        context = context,
                        dbHandle = dbHandle,
                        bundleIndex = bundleIndex,
                        proposalId = proposalId,
                        delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
                    )
                ) {
                    submittedBundles += bundleIndex
                    emitSubmittingProgress(bundleIndex, 1.0)
                    return@repeat
                }

                submitFreshVoteBundle(
                    context = context,
                    dbHandle = dbHandle,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId,
                    choiceId = choiceId,
                    numOptions = proposal.options.size,
                    delegatedShareIndicesByTarget = delegatedShareIndicesByTarget,
                    emitSubmittingProgress = { progressBundleIndex, bundleProgress ->
                        emitSubmittingProgress(progressBundleIndex, bundleProgress)
                    }
                )
                submittedBundles += bundleIndex
                emitSubmittingProgress(bundleIndex, 1.0)
            }

            markProposalSubmissionComplete(context.accountUuidString, roundId, proposalId)
            processedProposalCount++
        }
        return processedProposalCount
    }

    private suspend fun resolveReusableCachedVoteConfirmation(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): TxConfirmationProbeResult.Confirmed? {
        val cachedVoteTxHash =
            votingCryptoClient.getVoteTxHash(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) as? VotingTxHashLookup.Present ?: return null
        return probeCachedTx(cachedVoteTxHash.txHash)
            .also { confirmation ->
                if (confirmation !is TxConfirmationProbeResult.Confirmed) {
                    Log.i(
                        TAG,
                        "Cached vote tx ${cachedVoteTxHash.txHash} for round $roundId " +
                            "is not reusable ($confirmation); rebuilding commitment"
                    )
                }
            }.takeIf { it is TxConfirmationProbeResult.Confirmed } as? TxConfirmationProbeResult.Confirmed
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun submitCachedVoteIfReusable(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        proposalId: Int,
        delegatedShareIndicesByTarget: MutableMap<ShareDelegationTarget, MutableSet<Int>>
    ): Boolean {
        val roundId = context.roundId
        val cachedConfirmation =
            resolveReusableCachedVoteConfirmation(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) ?: return false

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

        traceVotingStep(
            roundId = roundId,
            step = "recordCachedVcPosition",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            try {
                votingCryptoClient.recordVcPosition(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId,
                    vcTreePosition = vcTreePosition
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw VotingSubmissionRecoverableException(
                    VotingErrors.MissingCachedCommitment(
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        proposalId = proposalId
                    ),
                    e
                )
            }
        }
        submitMissingShares(
            context = context,
            dbHandle = dbHandle,
            bundleIndex = bundleIndex,
            proposalId = proposalId,
            delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
        )
        return true
    }

    private suspend fun submitFreshVoteBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        proposalId: Int,
        choiceId: Int,
        numOptions: Int,
        delegatedShareIndicesByTarget: MutableMap<ShareDelegationTarget, MutableSet<Int>>,
        emitSubmittingProgress: (Int, Double) -> Unit
    ) {
        val roundId = context.roundId
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
                    nodeUrl = context.voteServerUrl
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
                    hotkeySeed = context.hotkeySeed,
                    proposalId = proposalId,
                    choice = choiceId,
                    numOptions = numOptions,
                    witnessJson = vanWitnessJson,
                    vanPosition = vanWitness.position,
                    anchorHeight = vanWitness.anchorHeight,
                    singleShare = context.singleShare,
                    proofProgress = { proofProgress ->
                        emitSubmittingProgress(bundleIndex, proofProgress)
                    }
                )
            }
        val signature = CastVoteSignature(voteAuthSig = commitment.voteAuthSig)
        val acceptedTransaction =
            reconcileVotingTransactionResult(
                result =
                    votingApiProvider.submitVoteCommitment(
                        bundle = commitment.toVoteCommitmentBundle(),
                        signature = signature
                    ),
                rejectionMessage = "Vote commitment transaction was rejected",
                fetchTxConfirmation = votingApiProvider::fetchTxConfirmation
            )
        acceptedTransaction.confirmation?.let { recoveredConfirmation ->
            requireRecoveredCastVoteMatchesCommitment(
                context = context,
                bundleIndex = bundleIndex,
                proposalId = proposalId,
                expectedVanCmx = commitment.voteAuthorityNoteNew,
                expectedVoteCommitment = commitment.voteCommitment,
                confirmation = recoveredConfirmation
            )
        }
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
                txHash = acceptedTransaction.txHash
            )
        }

        val confirmation =
            acceptedTransaction.confirmation
                ?: awaitTxConfirmation(acceptedTransaction.txHash)
                ?: throw VotingSubmissionRecoverableException(
                    VotingErrors.TxConfirmationTimedOut(acceptedTransaction.txHash)
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
            step = "recordVcPosition",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            votingCryptoClient.recordVcPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId,
                vcTreePosition = vcTreePosition
            )
        }
        submitMissingShares(
            context = context,
            dbHandle = dbHandle,
            bundleIndex = bundleIndex,
            proposalId = proposalId,
            delegatedShareIndicesByTarget = delegatedShareIndicesByTarget
        )
    }

    private suspend fun requireRecoveredCastVoteMatchesCommitment(
        context: VotingSubmitContext,
        bundleIndex: Int,
        proposalId: Int,
        expectedVanCmx: ByteArray,
        expectedVoteCommitment: ByteArray,
        confirmation: TxConfirmation
    ) {
        val (confirmedVanPosition, confirmedVoteCommitmentPosition) = confirmation.castVoteLeafPositions()
        val startHeight = context.session.createdAtHeight.coerceAtLeast(0)
        val actualVanPosition =
            findVanCommitmentPosition(
                roundId = context.roundId,
                startHeight = startHeight,
                expectedVanCmx = expectedVanCmx,
                fetchLatest = votingApiProvider::fetchCommitmentTreeLatest,
                fetchLeafPage = votingApiProvider::fetchCommitmentTreeLeafPage
            )
        val actualVoteCommitmentPosition =
            findVanCommitmentPosition(
                roundId = context.roundId,
                startHeight = startHeight,
                expectedVanCmx = expectedVoteCommitment,
                fetchLatest = votingApiProvider::fetchCommitmentTreeLatest,
                fetchLeafPage = votingApiProvider::fetchCommitmentTreeLeafPage
            )?.toLong()
        if (
            actualVanPosition != confirmedVanPosition ||
            actualVoteCommitmentPosition != confirmedVoteCommitmentPosition
        ) {
            throw VotingSubmissionRecoverableException(
                VotingErrors.RecoveredVoteCommitmentMismatch(
                    roundId = context.roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId
                )
            )
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
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        proposalId: Int,
        delegatedShareIndicesByTarget: MutableMap<ShareDelegationTarget, MutableSet<Int>>
    ) {
        val roundId = context.roundId
        val target = ShareDelegationTarget(bundleIndex = bundleIndex, proposalId = proposalId)
        val existingShareIndices = delegatedShareIndicesByTarget.getOrPut(target) { mutableSetOf() }
        val committedVote =
            traceVotingStep(
                roundId = roundId,
                step = "recoverCommittedVote",
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) {
                votingCryptoClient.recoverCommittedVote(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId
                )
            }
        val payloads =
            committedVote.sharePayloadsJson.toSharePayloads().map { payload ->
                payload.withSubmitAt(
                    votingCryptoClient.scheduledShareSubmitAt(
                        nowSeconds = Instant.now().epochSecond,
                        ceremonyStartSeconds = context.session.ceremonyStart.epochSecond,
                        voteEndTimeSeconds = context.session.voteEndTime.epochSecond,
                        singleShare = context.singleShare
                    )
                )
            }
        val pendingPayloads =
            payloads.filterNot { payload ->
                payload.encShare.shareIndex in existingShareIndices
            }

        if (pendingPayloads.isEmpty()) {
            return
        }

        val delegationResults = delegateSharesWithRetry(pendingPayloads)
        delegationResults.forEach { info ->
            val payload =
                pendingPayloads.firstOrNull { candidate ->
                    candidate.encShare.shareIndex == info.shareIndex &&
                        candidate.proposalId == info.proposalId
                } ?: return@forEach
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
                    nullifier = ByteArray(0),
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

    private suspend fun delegateSharesWithRetry(payloads: List<SharePayload>): List<DelegatedShareInfo> {
        var lastRetryableError: Exception? = null
        repeat(SHARE_DELEGATION_ATTEMPTS) { attempt ->
            try {
                return votingApiProvider.delegateShares(payloads)
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

    private fun Throwable.isTransientVotingInfrastructureFailure(): Boolean =
        generateSequence(this) { throwable -> throwable.cause }
            .map { throwable -> throwable.message.orEmpty().lowercase() }
            .any { lower ->
                lower.contains("http 5") ||
                    lower.contains("timeout") ||
                    lower.contains("timed out") ||
                    lower.contains("connect") ||
                    lower.contains("connection") ||
                    lower.contains("transport became inactive") ||
                    lower.contains("grpcstatus") ||
                    lower.contains("network")
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

    private fun TxConfirmation.requireAccepted(fallbackMessage: String) {
        if (code != 0) {
            throw IllegalStateException(log.ifEmpty { fallbackMessage })
        }
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

internal data class AcceptedVotingTransaction(
    val txHash: String,
    val confirmation: TxConfirmation?
)

internal sealed interface DelegationSubmissionResolution {
    data class AcceptedTransaction(
        val transaction: AcceptedVotingTransaction
    ) : DelegationSubmissionResolution

    data class ConfirmedVan(
        val position: Int,
        val txHash: String? = null
    ) : DelegationSubmissionResolution
}

/**
 * Treats a spent-nullifier rejection as an ambiguous retry only when the rejected response's hash
 * resolves to a successful transaction. This mirrors Vizor's bounded recovery behavior without
 * persisting an unconfirmed rejection hash.
 */
internal suspend fun reconcileVotingTransactionResult(
    result: TxResult,
    rejectionMessage: String,
    maxRecoveryAttempts: Int = SPENT_NULLIFIER_RECOVERY_ATTEMPTS,
    recoveryDelayMillis: Long = SPENT_NULLIFIER_RECOVERY_POLL_MS,
    fetchTxConfirmation: suspend (String) -> TxConfirmation?
): AcceptedVotingTransaction {
    require(maxRecoveryAttempts >= 1) { "maxRecoveryAttempts must be >= 1, was $maxRecoveryAttempts" }
    require(recoveryDelayMillis >= 0) { "recoveryDelayMillis must be non-negative" }

    if (result.code == 0) {
        check(result.txHash.isNotBlank()) { "Accepted voting transaction did not include tx_hash" }
        return AcceptedVotingTransaction(result.txHash, confirmation = null)
    }

    if (result.txHash.isNotBlank() && result.log.isSpentNullifierRejection()) {
        repeat(maxRecoveryAttempts) { attempt ->
            val confirmation = fetchTxConfirmation(result.txHash)
            if (confirmation?.code == 0) {
                return AcceptedVotingTransaction(result.txHash, confirmation)
            }
            if (confirmation != null) {
                throw IllegalStateException(
                    confirmation.log.ifEmpty { result.log.ifEmpty { rejectionMessage } }
                )
            }
            if (attempt + 1 < maxRecoveryAttempts) {
                delay(recoveryDelayMillis)
            }
        }
    }

    throw IllegalStateException(result.log.ifEmpty { rejectionMessage })
}

internal suspend fun reconcileDelegationTransactionResult(
    result: TxResult,
    bundleIndex: Int,
    rejectionMessage: String,
    fetchTxConfirmation: suspend (String) -> TxConfirmation?,
    findVanPosition: suspend () -> Int?
): DelegationSubmissionResolution {
    if (!result.log.isSpentNullifierRejection()) {
        return DelegationSubmissionResolution.AcceptedTransaction(
            reconcileVotingTransactionResult(result, rejectionMessage, fetchTxConfirmation = fetchTxConfirmation)
        )
    }

    var hashFailure: Exception? = null
    val acceptedTransaction =
        try {
            reconcileVotingTransactionResult(
                result = result,
                rejectionMessage = rejectionMessage,
                fetchTxConfirmation = fetchTxConfirmation
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            hashFailure = exception
            null
        }

    acceptedTransaction?.let { accepted ->
        val confirmation =
            checkNotNull(accepted.confirmation) {
                "Spent-nullifier hash recovery must include a confirmation"
            }
        try {
            confirmation.delegateVoteVanPosition(bundleIndex)
            return DelegationSubmissionResolution.AcceptedTransaction(accepted)
        } catch (exception: CancellationException) {
            throw exception
        } catch (leafException: Exception) {
            val vanPosition = findVanPosition() ?: throw leafException
            return DelegationSubmissionResolution.ConfirmedVan(
                position = vanPosition,
                txHash = accepted.txHash
            )
        }
    }

    val vanPosition = findVanPosition() ?: throw hashFailure ?: IllegalStateException(rejectionMessage)
    return DelegationSubmissionResolution.ConfirmedVan(vanPosition)
}

internal suspend fun findVanCommitmentPosition(
    roundId: String,
    startHeight: Long = 0,
    expectedVanCmx: ByteArray,
    maxRecoveryAttempts: Int = SPENT_NULLIFIER_RECOVERY_ATTEMPTS,
    recoveryDelayMillis: Long = SPENT_NULLIFIER_RECOVERY_POLL_MS,
    maxPagesPerAttempt: Int = COMMITMENT_TREE_MAX_PAGES,
    fetchLatest: suspend (String) -> CommitmentTreeLatest,
    fetchLeafPage: suspend (String, Long, Long) -> CommitmentTreeLeafPage
): Int? {
    require(roundId.isNotBlank()) { "roundId must not be blank" }
    require(startHeight >= 0) { "startHeight must be non-negative" }
    require(expectedVanCmx.size == COMMITMENT_BYTES) {
        "expectedVanCmx must be $COMMITMENT_BYTES bytes"
    }
    require(maxRecoveryAttempts >= 1) { "maxRecoveryAttempts must be >= 1" }
    require(recoveryDelayMillis >= 0) { "recoveryDelayMillis must be non-negative" }
    require(maxPagesPerAttempt >= 1) { "maxPagesPerAttempt must be >= 1" }

    repeat(maxRecoveryAttempts) { attempt ->
        val latest = fetchLatest(roundId)
        require(latest.height >= 0) { "Commitment tree height must be non-negative" }
        require(latest.nextIndex >= 0) { "Commitment tree next_index must be non-negative" }
        val scanStartHeight = startHeight.takeIf { it <= latest.height } ?: 0L
        val matches = mutableSetOf<Long>()
        var previousNextIndex: Long? = null
        var previousBlockHeight: Long? = null
        var pageStart = scanStartHeight
        var pageCount = 0
        do {
            check(pageCount < maxPagesPerAttempt) {
                "Commitment tree pagination exceeded $maxPagesPerAttempt pages"
            }
            pageCount += 1
            val page = fetchLeafPage(roundId, pageStart, latest.height)
            page.blocks.forEach { block ->
                require(block.height in pageStart..latest.height) {
                    "Commitment leaf block ${block.height} is outside requested range $pageStart..${latest.height}"
                }
                require(previousBlockHeight == null || block.height > previousBlockHeight) {
                    "Commitment leaf block heights must be strictly increasing"
                }
                require(block.startIndex >= 0) { "Commitment leaf start_index must be non-negative" }
                if (previousNextIndex == null) {
                    require(block.startIndex == 0L) {
                        "First commitment leaf block must start at index 0"
                    }
                }
                previousNextIndex?.let { expectedStartIndex ->
                    require(block.startIndex == expectedStartIndex) {
                        "Commitment leaf start_index ${block.startIndex} does not continue at $expectedStartIndex"
                    }
                }
                block.leavesBase64.forEachIndexed { leafOffset, encodedLeaf ->
                    val leaf =
                        runCatching { Base64.getDecoder().decode(encodedLeaf) }
                            .getOrElse { throw IllegalArgumentException("Malformed commitment leaf", it) }
                    require(leaf.size == COMMITMENT_BYTES) {
                        "Commitment leaf must be $COMMITMENT_BYTES bytes"
                    }
                    if (leaf.contentEquals(expectedVanCmx)) {
                        matches += Math.addExact(block.startIndex, leafOffset.toLong())
                    }
                }
                previousNextIndex = Math.addExact(block.startIndex, block.leavesBase64.size.toLong())
                previousBlockHeight = block.height
            }
            val nextFromHeight = page.nextFromHeight
            require(nextFromHeight == 0L || nextFromHeight > pageStart) {
                "Commitment tree next_from_height must advance or be zero"
            }
            require(nextFromHeight == 0L || nextFromHeight <= latest.height) {
                "Commitment tree next_from_height exceeds latest height"
            }
            if (nextFromHeight != 0L && page.blocks.isNotEmpty()) {
                require(nextFromHeight > page.blocks.last().height) {
                    "Commitment tree next_from_height must follow the last returned block"
                }
            }
            pageStart = nextFromHeight
        } while (pageStart != 0L)

        val scannedNextIndex = previousNextIndex ?: 0L
        require(scannedNextIndex == latest.nextIndex) {
            "Commitment tree scan ended at index $scannedNextIndex, expected ${latest.nextIndex}"
        }

        require(matches.size <= 1) { "Persisted VAN commitment appears more than once in the round tree" }
        matches.singleOrNull()?.let { position ->
            require(position <= Int.MAX_VALUE) { "VAN position exceeds supported range: $position" }
            return position.toInt()
        }
        if (attempt + 1 < maxRecoveryAttempts) {
            delay(recoveryDelayMillis)
        }
    }
    return null
}

private fun String.isSpentNullifierRejection(): Boolean =
    contains("nullifier", ignoreCase = true) &&
        contains("spent", ignoreCase = true)

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

internal fun TxConfirmation.castVoteLeafPositions(): Pair<Int, Long> {
    val rawLeafIndex =
        event("cast_vote")
            ?.attribute("leaf_index")
            ?: throw unexpectedSdkResponse("Missing cast_vote leaf_index")
    val leafParts = rawLeafIndex.split(',')
    if (leafParts.size != 2) {
        throw unexpectedSdkResponse("Malformed cast_vote leaf_index: $rawLeafIndex")
    }

    val vanPosition =
        leafParts[0].trim().toIntOrNull()
            ?: throw unexpectedSdkResponse("Malformed VAN leaf position: ${leafParts[0]}")
    val voteCommitmentPosition =
        leafParts[1].trim().toLongOrNull()
            ?: throw unexpectedSdkResponse("Malformed vote commitment leaf position: ${leafParts[1]}")

    return vanPosition to voteCommitmentPosition
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

private const val SPENT_NULLIFIER_RECOVERY_ATTEMPTS = 3
private const val SPENT_NULLIFIER_RECOVERY_POLL_MS = 1_000L
private const val COMMITMENT_BYTES = 32
private const val COMMITMENT_TREE_MAX_PAGES = 128
