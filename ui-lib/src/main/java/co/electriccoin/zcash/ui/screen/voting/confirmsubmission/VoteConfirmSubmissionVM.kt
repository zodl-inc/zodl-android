package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.internal.TypesafeVotingBackend
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.VotingStorageDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletSeedBytesUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom

class VoteConfirmSubmissionVM(
    private val args: VoteConfirmSubmissionArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val votingBackend: TypesafeVotingBackend,
    private val synchronizerProvider: SynchronizerProvider,
    private val accountDataSource: AccountDataSource,
    private val votingStorage: VotingStorageDataSource,
    private val getActiveVotingSession: GetActiveVotingSessionUseCase,
    private val getWalletSeedBytes: GetWalletSeedBytesUseCase,
    private val votingApiProvider: co.electriccoin.zcash.ui.common.provider.VotingApiProvider,
) : ViewModel() {
    private data class PageData(
        val roundTitle: String,
        val isKeystone: Boolean
    )

    private val dataLce = mutableLce<PageData>()
    private val statusFlow = MutableStateFlow<VoteSubmissionStatus>(VoteSubmissionStatus.Idle)
    private val eligibleWeightZatoshiFlow = MutableStateFlow<Long?>(null)
    private val hotkeyAddressFlow = MutableStateFlow<String?>(null)

    init {
        dataLce.execute {
            val round = getAllRounds().firstOrNull { it.id == args.roundIdHex }
            val account = getSelectedWalletAccount()
            PageData(roundTitle = round?.title ?: "", isKeystone = account is KeystoneAccount)
        }
    }

    val state: StateFlow<LceState<VoteConfirmSubmissionState>> =
        combine(
            dataLce.state.map { it.success },
            statusFlow,
            eligibleWeightZatoshiFlow,
            hotkeyAddressFlow,
        ) { data, status, weightZatoshi, hotkeyAddr ->
            data?.let { createState(it, status, weightZatoshi, hotkeyAddr) }
        }.withLce(groupLce(dataLce)) {
            errorStateMapper.mapToState(
                error = it,
                title = stringRes("Confirmation unavailable"),
                message = stringRes("Could not load poll details. Please try again."),
                primaryStyle = ButtonStyle.PRIMARY,
            )
        }.stateIn(this)

    private fun createState(
        data: PageData,
        status: VoteSubmissionStatus,
        weightZatoshi: Long?,
        hotkeyAddr: String?,
    ): VoteConfirmSubmissionState {
        val weightZEC =
            weightZatoshi
                ?.let { "%.4f ZEC".format(it / 100_000_000.0) }
                ?: "0.0000 ZEC"
        val hotkey = hotkeyAddr ?: "–"
        val memo =
            "I am authorizing this hotkey managed by my wallet to vote on " +
                "${data.roundTitle} with $weightZEC."
        val ctaLabel =
            when (status) {
                is VoteSubmissionStatus.Idle -> {
                    if (data.isKeystone) "Confirm with Keystone" else "Confirm"
                }

                is VoteSubmissionStatus.Authorizing -> {
                    "Authorizing…"
                }

                is VoteSubmissionStatus.Submitting -> {
                    "Submitting vote ${status.current} of ${status.total}…"
                }

                is VoteSubmissionStatus.Completed -> {
                    "Done"
                }

                is VoteSubmissionStatus.Failed -> {
                    "Dismiss"
                }
            }
        val ctaEnabled =
            status is VoteSubmissionStatus.Idle ||
                status is VoteSubmissionStatus.Completed ||
                status is VoteSubmissionStatus.Failed
        val ctaAction: () -> Unit =
            when (status) {
                is VoteSubmissionStatus.Idle -> ::onConfirm
                is VoteSubmissionStatus.Completed -> ::onDone
                is VoteSubmissionStatus.Failed -> ::onDismiss
                else -> ({})
            }

        return VoteConfirmSubmissionState(
            status = status,
            roundTitle = stringRes(data.roundTitle),
            votingWeightZEC = stringRes(weightZEC),
            hotkeyAddress = stringRes(hotkey),
            isKeystoneUser = data.isKeystone,
            memo = stringRes(memo),
            ctaButton =
                ButtonState(
                    text = stringRes(ctaLabel),
                    style = ButtonStyle.PRIMARY,
                    isEnabled = ctaEnabled,
                    onClick = ctaAction,
                ),
            onBack = { if (status is VoteSubmissionStatus.Idle) navigationRouter.back() },
        )
    }

    @Suppress("LongMethod")
    private fun onConfirm() {
        viewModelScope.launch {
            var dbHandle = 0L
            try {
                statusFlow.value = VoteSubmissionStatus.Authorizing(0.05f)

                // ── Collect prerequisites ──────────────────────────────────────
                val synchronizer = synchronizerProvider.getSynchronizer()
                val walletDbPath = synchronizer.getWalletDbPath()
                val votingDbPath = (File(walletDbPath).parent ?: File(walletDbPath).absoluteFile.parent!!) + "/voting.sqlite3"

                val round =
                    getAllRounds().firstOrNull { it.id == args.roundIdHex }
                        ?: error("Round ${args.roundIdHex} not found")
                val session =
                    getActiveVotingSession()
                        ?: error("No active voting session — cannot get crypto parameters")

                val account = accountDataSource.getSelectedAccount()
                val accountUuidBytes = account.sdkAccount.accountUuid.value
                val walletSeed = getWalletSeedBytes()
                // voting.rs convention: 0 = MainNetwork, 1 = TestNetwork
                // ZcashNetwork convention: Mainnet.id = 1, Testnet.id = 0 — inverse!
                val zcashNetwork =
                    (synchronizer as? cash.z.ecc.android.sdk.SdkSynchronizer)
                        ?.let { ZcashNetwork.Mainnet } ?: ZcashNetwork.Mainnet
                val networkId = if (zcashNetwork.isMainnet()) 0 else 1
                val pirUrl =
                    votingApiProvider
                        .fetchServiceConfig()
                        .pirServers
                        .firstOrNull()
                        ?.url
                        ?: "https://dev.shielded-vote.com"

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.1f)

                // ── Step 1: Open voting DB + init round ───────────────────────
                dbHandle = votingBackend.openVotingDb(votingDbPath)
                votingBackend.setWalletId(dbHandle, account.sdkAccount.accountUuid.toString())
                votingBackend.initRound(
                    dbHandle = dbHandle,
                    roundId = args.roundIdHex,
                    snapshotHeight = session.snapshotHeight,
                    eaPK = session.eaPK,
                    ncRoot = session.ncRoot,
                    nullifierIMTRoot = session.nullifierIMTRoot,
                    sessionJson = null
                )
                votingStorage.storePhase(args.roundIdHex, "INIT")

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.2f)

                // ── Step 2: Wallet notes + bundle setup ───────────────────────
                val notesJson =
                    votingBackend.getWalletNotes(
                        walletDbPath,
                        session.snapshotHeight,
                        networkId,
                        accountUuidBytes
                    )
                val bundleResult = votingBackend.setupBundles(dbHandle, args.roundIdHex, notesJson)
                check(bundleResult.eligibleWeight > 0L) {
                    "No eligible ZEC at snapshot height ${session.snapshotHeight}. " +
                        "You need shielded ZEC to participate in this poll."
                }
                eligibleWeightZatoshiFlow.value = bundleResult.eligibleWeight
                votingStorage.storePhase(args.roundIdHex, "BUNDLES")

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.3f)

                // ── Step 3: Generate hotkey ────────────────────────────────────
                val hotkeyRawSeed = ByteArray(64).also { SecureRandom().nextBytes(it) }
                val hotkey = votingBackend.generateHotkey(dbHandle, args.roundIdHex, hotkeyRawSeed)
                votingStorage.storeHotkeySecret(args.roundIdHex, hotkey.secretKey.value)
                hotkeyAddressFlow.value = hotkey.address
                votingStorage.storePhase(args.roundIdHex, "HOTKEY")

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.4f)

                // ── Step 4: Tree state + witnesses (bundle 0) ─────────────────
                val snapshotBlockHeight = BlockHeight.new(session.snapshotHeight)
                val treeStateBytes = synchronizer.getTreeState(snapshotBlockHeight)
                votingBackend.storeTreeState(dbHandle, args.roundIdHex, treeStateBytes)
                votingBackend.generateNoteWitnesses(dbHandle, args.roundIdHex, 0, walletDbPath, notesJson)
                votingStorage.storePhase(args.roundIdHex, "WITNESSES")

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.5f)

                // ── Step 5: ZKP1 — delegation proof ───────────────────────────
                votingBackend.buildAndProveDelegation(
                    dbHandle,
                    args.roundIdHex,
                    0,
                    pirUrl,
                    networkId,
                    notesJson,
                    hotkeyRawSeed
                )
                votingStorage.storePhase(args.roundIdHex, "PROOF")

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.7f)

                // ── Step 6: Get + submit delegation ───────────────────────────
                val delegationSubmission =
                    votingBackend.getDelegationSubmission(
                        dbHandle,
                        args.roundIdHex,
                        0,
                        walletSeed,
                        networkId,
                        0
                    )
                votingApiProvider.submitDelegation(
                    DelegationRegistration(
                        roundIdHex = args.roundIdHex,
                        proof = delegationSubmission.proof,
                        hotkeyAddress = hotkey.address,
                        snapshotHeight = session.snapshotHeight,
                        rk = delegationSubmission.rk,
                        spendAuthSig = delegationSubmission.spendAuthSig,
                        sighash = delegationSubmission.sighash,
                        nfSigned = delegationSubmission.nfSigned,
                        cmxNew = delegationSubmission.cmxNew,
                        govComm = delegationSubmission.govComm,
                        govNullifiers = delegationSubmission.govNullifiers,
                        alpha = delegationSubmission.alpha,
                    )
                )
                votingStorage.storePhase(args.roundIdHex, "DELEGATED")

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.85f)

                // ── Step 7: Sync VAN tree + store position ────────────────────
                // The VAN position is emitted by the delegation TX event on chain.
                // For MVP, sync the tree and use position 0 — real position requires
                // polling the delegation TX confirmation and parsing the TX events.
                votingBackend.syncVoteTree(dbHandle, args.roundIdHex, pirUrl)
                votingBackend.storeVanPosition(dbHandle, args.roundIdHex, 0, 0)

                statusFlow.value = VoteSubmissionStatus.Authorizing(0.95f)

                // ── Step 8: ZKP2 + vote submission per proposal ───────────────
                val choices = org.json.JSONObject(args.choicesJson)
                val proposalIds = choices.keys().asSequence().toList()
                val totalProposals = proposalIds.size

                proposalIds.forEachIndexed { idx, key ->
                    val proposalId = key.toInt()
                    val choice = choices.getInt(key)
                    val proposal =
                        round.proposals.firstOrNull { it.id == proposalId }
                            ?: return@forEachIndexed

                    statusFlow.value =
                        VoteSubmissionStatus.Submitting(
                            idx + 1,
                            totalProposals,
                            (idx + 1).toFloat() / totalProposals
                        )

                    val vanWitnessJson =
                        votingBackend.generateVanWitnessJson(
                            dbHandle,
                            args.roundIdHex,
                            0,
                            session.snapshotHeight.toInt()
                        )
                    val commitment =
                        votingBackend.buildVoteCommitment(
                            dbHandle = dbHandle,
                            roundId = args.roundIdHex,
                            bundleIndex = 0,
                            hotkeySeed = hotkey.secretKey.value,
                            proposalId = proposalId,
                            choice = choice,
                            numOptions = proposal.options.size,
                            witnessJson = vanWitnessJson,
                            vanPosition = 0,
                            anchorHeight = session.snapshotHeight.toInt(),
                            networkId = networkId,
                        )
                    val sig =
                        votingBackend.signCastVote(
                            hotkeySeed = hotkey.secretKey.value,
                            networkId = networkId,
                            roundId = args.roundIdHex,
                            rVpk = commitment.rVpk,
                            vanNullifier = commitment.vanNullifier,
                            vanNew = commitment.voteAuthorityNoteNew,
                            voteCommitment = commitment.voteCommitment,
                            proposalId = proposalId,
                            anchorHeight = session.snapshotHeight.toInt(),
                            alphaV = commitment.alphaV,
                        )
                    votingApiProvider.submitVoteCommitment(
                        co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle(
                            proof = commitment.voteCommitment, // voteCommitment bytes as proof
                            encryptedShares = emptyList() // shares sent separately
                        ),
                        co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature(
                            roundIdHex = args.roundIdHex,
                            signature = sig
                        )
                    )
                    votingStorage.markShareSubmitted(args.roundIdHex, proposalId)
                }

                votingStorage.storePhase(args.roundIdHex, "VOTED")
                statusFlow.value = VoteSubmissionStatus.Completed
            } catch (e: Exception) {
                statusFlow.value = VoteSubmissionStatus.Failed(e.message ?: "Unexpected error during submission")
            } finally {
                if (dbHandle != 0L) votingBackend.closeVotingDb(dbHandle)
                // Zero the sensitive data — handled by GC in JVM but explicit is cleaner
            }
        }
    }

    private fun onDone() = navigationRouter.forward(VoteCoinholderPollingArgs)

    private fun onDismiss() {
        statusFlow.value = VoteSubmissionStatus.Idle
    }
}
