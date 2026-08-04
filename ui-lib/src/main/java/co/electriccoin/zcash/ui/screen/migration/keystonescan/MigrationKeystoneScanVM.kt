package co.electriccoin.zcash.ui.screen.migration.keystonescan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.TransferResult
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneFirmwarePolicy
import co.electriccoin.zcash.ui.common.model.KeystoneFirmwareVersion
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState
import co.electriccoin.zcash.ui.common.model.migration.migrationFailureMessage
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toKeystoneFwVersion
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.PendingMigrationScheduleRepository
import co.electriccoin.zcash.ui.common.usecase.FinalizeMigrationScheduleUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardMigrationSdkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.migration.keystonesign.KEYSTONE_BATCH_MAX_ITEMS
import co.electriccoin.zcash.ui.screen.migration.keystonesign.MigrationKeystoneSignArgs
import co.electriccoin.zcash.ui.screen.migration.keystonesign.keystoneBatchRoundSlice
import co.electriccoin.zcash.ui.screen.migration.keystonesign.keystoneBatchTotalRounds
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.scankeystone.model.ScanKeystoneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MigrationKeystoneScanVM(
    private val args: MigrationKeystoneScanArgs,
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val pendingSchedule: PendingMigrationScheduleRepository,
    private val pendingKeystonePczts: PendingKeystoneMigrationPcztsRepository,
    private val finalizeMigrationSchedule: FinalizeMigrationScheduleUseCase,
    private val isMigrationTorEnabledStorageProvider: IsMigrationTorEnabledStorageProvider,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {

    val validationState = MutableStateFlow(ScanValidationState.NONE)

    val state = MutableStateFlow(
        ScanKeystoneState(
            progress = null,
            message = stringRes("Scan the QR code shown on your Keystone device after signing."),
        )
    )

    val failureSheet = MutableStateFlow<MigrationTransferFailureState?>(null)

    // Covers only the LAST round's finish (apply signatures, submit the split, store the
    // schedule, finalize) — the real network/JNI work the QR camera gives no feedback on
    // otherwise. Deliberately NOT wrapped around per-frame decodeKeystoneSignBatchPart() calls
    // below (that would flicker loading on every scanned QR chunk) or the fast, local
    // multi-round carry-forward (no network I/O, matches prior instant-navigate behavior).
    private val finalizingLce = mutableLce<Unit>()
    val isFinalizing: StateFlow<Boolean> = finalizingLce.loading.stateIn(this, initialValue = false)

    private var isProcessing = false
    private var hasResetDecoder = false

    // "cypherpunk" 3.0.2 is the first Keystone firmware that supports migration batch signing at
    // all — older firmware either can't sign the batch correctly or won't report a version, and
    // both cases must block broadcast, not silently proceed.
    private val requiredFirmware = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)

    fun onScanned(result: String) {
        if (isProcessing || finalizingLce.state.value.loading) return
        isProcessing = true
        viewModelScope.launch {
            val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
            val sched = pendingSchedule.get(accountKeyId)
            val pending = pendingKeystonePczts.get(accountKeyId)
            if (sched == null || pending == null) {
                // Edge case only (e.g. process death mid-flow) — bounce back to Confirm Transfer
                // Plan, which will propose a fresh schedule.
                navigationRouter.back()
                return@launch
            }
            val sdk = getOrchardMigrationSdk() ?: error("MigrationKeystoneScanVM: no wallet available to sign")
            if (!hasResetDecoder) {
                sdk.resetKeystoneSignBatchDecoder()
                hasResetDecoder = true
            }
            val decoded = runCatching { sdk.decodeKeystoneSignBatchPart(result, pending.requestId) }
                .getOrElse {
                    isProcessing = false
                    return@launch
                }
            state.update { it.copy(progress = decoded.progress) }
            val data = decoded.data
            if (!decoded.complete || data == null) {
                isProcessing = false
                return@launch
            }

            // Firmware can't change mid-batch (same physical device every round), so checking on
            // round 0 only is sufficient and avoids making the user scan through every remaining
            // round only to be blocked at the very end.
            if (pending.roundIndex == 0) {
                val detected = decoded.firmwareVersion?.toKeystoneFwVersion()
                val outcome = KeystoneFirmwarePolicy.evaluate(detected, requiredFirmware)
                Twig.debug {
                    "MigrationKeystoneScanVM: detected Keystone firmware " +
                        "${detected ?: "none"} (required $requiredFirmware) -> $outcome"
                }
                if (outcome != KeystoneFirmwarePolicy.Outcome.OK) {
                    isProcessing = false
                    failureSheet.update {
                        MigrationTransferFailureState(
                            message = "Your Keystone firmware doesn't support migration yet. " +
                                "Update your Keystone device, then come back to retry.",
                            // Nothing to retry without a physical firmware update — both actions
                            // just dismiss and back out, unlike the network-failure sheet below.
                            onRetry = { failureSheet.value = null; navigationRouter.back() },
                            onDismiss = { failureSheet.value = null; navigationRouter.back() },
                        )
                    }
                    return@launch
                }
            }

            // This round's slice only — the scanned response covers exactly what buildBatch()
            // built for pending.roundIndex, not the whole (possibly multi-round) batch.
            val slice = keystoneBatchRoundSlice(
                roundIndex = pending.roundIndex,
                hasSplit = pending.splitUnsignedPczt != null,
                transferCount = pending.transferUnsignedPczts.size,
                maxItems = KEYSTONE_BATCH_MAX_ITEMS,
            )
            val transfersForRound = pending.transferUnsignedPczts.slice(slice.transferRange)
            val splitForRound = if (slice.includeSplit) pending.splitUnsignedPczt else null

            val signed = sdk.applyKeystoneBatchSignatures(
                splitUnsignedPczt = splitForRound,
                transferUnsignedPczts = transfersForRound.map { it.second },
                batchSignResponse = data,
            )

            val accumulatedSplitSigned = signed.splitSignedPczt ?: pending.accumulatedSplitSigned
            val accumulatedTransferSigned = pending.accumulatedTransferSigned +
                transfersForRound.map { it.first }.zip(signed.transferSignedPczts)

            val totalRounds = keystoneBatchTotalRounds(
                hasSplit = pending.splitUnsignedPczt != null,
                transferCount = pending.transferUnsignedPczts.size,
                maxItems = KEYSTONE_BATCH_MAX_ITEMS,
            )
            if (pending.roundIndex + 1 < totalRounds) {
                // More rounds remain — carry the accumulated signatures forward and hand off to a
                // fresh sign-screen instance for the next round. replace() keeps the back stack at
                // a constant depth regardless of how many rounds a large migration needs.
                pendingKeystonePczts.set(
                    accountKeyId,
                    pending.copy(
                        roundIndex = pending.roundIndex + 1,
                        accumulatedSplitSigned = accumulatedSplitSigned,
                        accumulatedTransferSigned = accumulatedTransferSigned,
                    )
                )
                isProcessing = false
                navigationRouter.replace(MigrationKeystoneSignArgs(args.mode))
                return@launch
            }

            // Last (or only) round — finish using the FULL accumulated signed set, not just this
            // round's slice. This is the real network/JNI work (Tor submit, schedule storage,
            // finalize) with no other feedback on the QR screen, so it's tracked via
            // finalizingLce/isFinalizing for the loading overlay.
            isProcessing = false
            finalizingLce.execute {
                val splitSignedPczt = accumulatedSplitSigned
                if (splitSignedPczt != null) {
                    val useTor = isMigrationTorEnabledStorageProvider.get()
                    val splitResult = sdk.storeSignedNoteSplitPczt(
                        splitSignedPczt,
                        NetworkPrivacyOptions(useTor = useTor),
                    )
                    if (splitResult !is TransferResult.Success) {
                        failureSheet.update {
                            MigrationTransferFailureState(
                                message = migrationFailureMessage(splitResult),
                                onRetry = { failureSheet.value = null; onScanned(result) },
                                onDismiss = { failureSheet.value = null },
                            )
                        }
                        return@execute
                    }
                }
                sdk.storeSignedSchedulePczts(accumulatedTransferSigned)
                finalizeMigrationSchedule(sched, args.mode)
                pendingSchedule.clear()
                pendingKeystonePczts.clear()
            }
        }
    }

    fun onBack() = navigationRouter.back()
}
