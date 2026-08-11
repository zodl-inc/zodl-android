package co.electriccoin.zcash.ui.screen.migration.keystonesign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPczts
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.addressbook.ADDRESS_MAX_LENGTH
import co.electriccoin.zcash.ui.screen.migration.keystonescan.MigrationKeystoneScanArgs
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.ZashiAccountInfoListItemState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID
import co.electriccoin.zcash.ui.design.R as DesignR

class MigrationKeystoneSignVM(
    private val args: MigrationKeystoneSignArgs,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val pendingSchedule: PendingMigrationScheduleRepository,
    private val pendingKeystonePczts: PendingKeystoneMigrationPcztsRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val qrParts = MutableStateFlow<List<String>?>(null)
    private val qrFrameIndex = MutableStateFlow(0)

    // (0-based round index, total round count) for the batch currently being signed — null until
    // the first buildBatch() succeeds. totalRounds is always >= 1 once non-null.
    private val roundInfo = MutableStateFlow<Pair<Int, Int>?>(null)

    val failureSheet = MutableStateFlow<MigrationTransferFailureState?>(null)

    init {
        buildBatch()
    }

    // Builds (or, when resuming round > 0 of a multi-round batch, reuses) the unsigned split (if
    // needed) + schedule PCZTs, then builds the animated QR for *this round's* slice only — see
    // KeystoneBatchChunking.kt and MigrationSdk.kt's buildKeystoneSignBatchQrParts doc. A batch
    // too large for one round's KeystoneSigningRoundBudget (see sdk.keystoneSigningRoundBudget()/
    // KeystoneBatchChunking.kt) is split across multiple sign/scan round trips;
    // MigrationKeystoneScanVM advances pendingKeystonePczts.roundIndex and replace()s back into a
    // fresh instance of this screen for each subsequent round. Retains the unsigned originals (via
    // pendingKeystonePczts) so the scan screen can match the device's signatures back to them once
    // scanned, and so a resumed round never rebuilds (and thereby diverges from) PCZTs an earlier
    // round already got signatures for.
    private fun buildBatch() {
        viewModelScope.launch {
            runCatching {
                val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
                val sched = pendingSchedule.get(accountKeyId) ?: return@runCatching null
                val sdk = getOrchardMigrationSdk()
                val existing = pendingKeystonePczts.get(accountKeyId)
                val splitUnsignedPczt: ByteArray?
                val prepUnsignedPczts: List<Pair<Long, ByteArray>>
                val transferUnsignedPczts: List<Pair<Long, ByteArray>>
                val roundIndex: Int
                val accumulatedSplitSigned: ByteArray?
                val accumulatedPrepSigned: List<Pair<Long, ByteArray>>
                val accumulatedTransferSigned: List<Pair<Long, ByteArray>>
                if (existing != null) {
                    splitUnsignedPczt = existing.splitUnsignedPczt
                    prepUnsignedPczts = existing.prepUnsignedPczts
                    transferUnsignedPczts = existing.transferUnsignedPczts
                    roundIndex = existing.roundIndex
                    accumulatedSplitSigned = existing.accumulatedSplitSigned
                    accumulatedPrepSigned = existing.accumulatedPrepSigned
                    accumulatedTransferSigned = existing.accumulatedTransferSigned
                } else {
                    // Opaque-handle contract (SDK 2.6.5+): createUnsignedNoteSplitPczt(proposal)
                    // and createUnsignedTransferPczts(schedule) must refer to the SAME cached plan
                    // (same proposalHandle) or the split call throws "plan superseded". When a
                    // split is needed we therefore prepare it and re-derive the schedule FROM it —
                    // proposeMigrationTransfersFromSplit() keeps the same handle — mirroring
                    // MigrationReviewVM's Zashi path, which re-derives for the same reason (a
                    // pre-split proposeMigrationTransfers() schedule can name denominations the
                    // split never actually mints). The re-derived schedule is written back to
                    // pendingSchedule so the scan screen finalizes THIS plan, not the pre-split one.
                    if (sdk.isNoteSplitNeeded()) {
                        val splitProposal = sdk.prepareNoteSplit()
                        val scheduleFromSplit = sdk.proposeMigrationTransfersFromSplit(splitProposal)
                        splitUnsignedPczt = sdk.createUnsignedNoteSplitPczt(splitProposal).toByteArray()
                        // The WHOLE note-split tree is built (and pre-signable) at commit — every
                        // preparation beyond the first layer-0 split joins the batch here; only
                        // that first split keeps the immediate-broadcast storeSignedNoteSplitPczt
                        // path (2026-07-30 finding: these used to be silently dropped and stayed
                        // AwaitingSignature forever).
                        prepUnsignedPczts =
                            sdk
                                .createUnsignedPreparationPczts(scheduleFromSplit)
                                .filterNot { it.layer == 0 && it.index == 0 }
                                .map { it.id to it.pczt.toByteArray() }
                        transferUnsignedPczts =
                            sdk
                                .createUnsignedTransferPczts(scheduleFromSplit)
                                .map { it.first to it.second.toByteArray() }
                        pendingSchedule.set(accountKeyId, scheduleFromSplit)
                    } else {
                        splitUnsignedPczt = null
                        prepUnsignedPczts =
                            sdk.createUnsignedPreparationPczts(sched).map { it.id to it.pczt.toByteArray() }
                        transferUnsignedPczts =
                            sdk.createUnsignedTransferPczts(sched).map { it.first to it.second.toByteArray() }
                    }
                    roundIndex = 0
                    accumulatedSplitSigned = null
                    accumulatedPrepSigned = emptyList()
                    accumulatedTransferSigned = emptyList()
                }

                val roundBudget = sdk.keystoneSigningRoundBudget()
                val slice =
                    keystoneBatchRoundSlice(
                        roundIndex = roundIndex,
                        hasSplit = splitUnsignedPczt != null,
                        prepCount = prepUnsignedPczts.size,
                        transferCount = transferUnsignedPczts.size,
                        budget = roundBudget,
                    )
                val splitForRound = if (slice.includeSplit) splitUnsignedPczt else null
                val prepsForRound = prepUnsignedPczts.slice(slice.prepRange)
                val transfersForRound = transferUnsignedPczts.slice(slice.transferRange)
                val requestId = randomRequestId()
                val parts =
                    sdk.buildKeystoneSignBatchQrParts(
                        requestId = requestId,
                        splitUnsignedPczt = splitForRound?.let(::Pczt),
                        // Extra preparations ride ahead of the transfers — the device signs PCZTs
                        // kind-agnostically; ScanVM splits the response back by the same counts.
                        transferUnsignedPczts = (prepsForRound + transfersForRound).map { Pczt(it.second) },
                        maxFragmentLen = MAX_FRAGMENT_LEN,
                    )
                pendingKeystonePczts.set(
                    accountKeyId,
                    PendingKeystoneMigrationPczts(
                        requestId = requestId,
                        splitUnsignedPczt = splitUnsignedPczt,
                        prepUnsignedPczts = prepUnsignedPczts,
                        transferUnsignedPczts = transferUnsignedPczts,
                        roundIndex = roundIndex,
                        accumulatedSplitSigned = accumulatedSplitSigned,
                        accumulatedPrepSigned = accumulatedPrepSigned,
                        accumulatedTransferSigned = accumulatedTransferSigned,
                    )
                )
                val totalRounds =
                    keystoneBatchTotalRounds(
                        hasSplit = splitUnsignedPczt != null,
                        prepCount = prepUnsignedPczts.size,
                        transferCount = transferUnsignedPczts.size,
                        budget = roundBudget,
                    )
                Triple(parts, roundIndex, totalRounds)
            }.onSuccess { result ->
                if (result != null) {
                    val (parts, roundIndex, totalRounds) = result
                    qrFrameIndex.value = 0
                    qrParts.value = parts
                    roundInfo.value = roundIndex to totalRounds
                }
            }.onFailure {
                failureSheet.update {
                    MigrationTransferFailureState(
                        message = stringRes(DesignR.string.migrationKeystoneSign_prepareErrorMessage),
                        onRetry = {
                            failureSheet.value = null
                            buildBatch()
                        },
                        onDismiss = { failureSheet.value = null },
                    )
                }
            }
        }
    }

    private val combinedState: Flow<SignKeystoneTransactionState?> =
        combine(
            getSelectedWalletAccount.observe(),
            qrParts,
            qrFrameIndex,
            roundInfo
        ) { account, parts, frameIndex, round ->
            val accountKeyId = account?.sdkAccount?.accountUuid?.toStorageKeyId()
            if (account == null || accountKeyId == null || pendingSchedule.peek(accountKeyId) == null) {
                // Edge case only (e.g. process death mid-flow) — the schedule is proposed
                // fresh every time Confirm Transfer Plan is entered, so just bounce back there.
                // NOTE: peek() is used here (not get()) because this is a reactive combine block
                // that may be re-evaluated on every selected-account emission; get() clears the
                // stored schedule on account mismatch which would strand the QR screen if a
                // transient wrong-account emission arrived before the correct one.
                navigationRouter.back()
                return@combine null
            }
            // Only surfaced for a migration too large for one Keystone QR round trip — invisible
            // (empty suffix) in the common single-round case.
            val roundSuffix =
                round
                    ?.let { (index, total) ->
                        if (total > 1) {
                            stringRes(DesignR.string.migrationKeystoneSign_roundSuffix, index + 1, total)
                        } else {
                            stringRes("")
                        }
                    }
                    ?: stringRes("")
            SignKeystoneTransactionState(
                barTitle = stringRes(DesignR.string.migrationKeystoneSign_barTitle),
                title = stringRes(DesignR.string.migrationKeystoneSign_scanWithKeystone, roundSuffix),
                subtitle = stringRes(DesignR.string.migrationKeystoneSign_subtitle),
                accountInfo =
                    ZashiAccountInfoListItemState(
                        icon = account.icon,
                        title = account.name,
                        subtitle = stringRes("${account.unified.address.address.take(ADDRESS_MAX_LENGTH)}..."),
                    ),
                badgeText = stringRes(DesignR.string.migrationKeystoneSign_badgeHardware),
                generateNextQrCode = {
                    val size = parts?.size ?: 1
                    qrFrameIndex.value = (frameIndex + 1) % size
                },
                qrData = parts?.getOrNull(frameIndex),
                secondaryButton = null,
                positiveButton =
                    ButtonState(
                        text = stringRes(DesignR.string.migrationKeystoneSign_getSignature),
                        onClick = ::onGetSignature
                    ),
                negativeButton =
                    ButtonState(text = stringRes(DesignR.string.migrationKeystoneSign_reject), onClick = ::onReject),
                onBack = ::onReject,
            )
        }

    val state: StateFlow<SignKeystoneTransactionState?> =
        combinedState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            initialValue = null,
        )

    private fun onGetSignature() = navigationRouter.forward(MigrationKeystoneScanArgs(mode = args.mode))

    private fun onReject() {
        pendingSchedule.clear()
        pendingKeystonePczts.clear()
        navigationRouter.back()
    }

    companion object {
        // Conservative default fragment length for the animated multi-part QR — matches the
        // `keystone-sdk-android` AAR's own default (unused here directly, but a reasonable
        // reference point since Keystone devices are the same physical scan target either way).
        private const val MAX_FRAGMENT_LEN = 150

        // A UUID is two 64-bit longs.
        private const val UUID_BYTE_LENGTH = 16

        private fun randomRequestId(): ByteArray {
            val uuid = UUID.randomUUID()
            return ByteBuffer
                .allocate(UUID_BYTE_LENGTH)
                .putLong(uuid.mostSignificantBits)
                .putLong(uuid.leastSignificantBits)
                .array()
        }
    }
}
