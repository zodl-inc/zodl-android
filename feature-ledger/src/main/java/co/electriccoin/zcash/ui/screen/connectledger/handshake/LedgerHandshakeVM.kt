package co.electriccoin.zcash.ui.screen.connectledger.handshake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerBondingFailedException
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerIssueSheetBuilder
import co.electriccoin.zcash.ui.screen.connectledger.common.ledgerAlreadyAddedSheet
import co.electriccoin.zcash.ui.screen.connectledger.common.toEnrollmentIssue
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import co.electriccoin.zcash.ui.design.R as DesignR

/**
 * Pairs the Ledger picked on the scan screen as soon as the screen opens: connects to it, opens
 * the Zcash app on it and asks it to export the account, which the user approves on the device.
 *
 * The device comes from [LedgerSelectedDeviceRepository]; when process death has emptied it, the
 * flow falls back to the wallet root. The transport each attempt opens is closed by the data
 * source before [PairLedgerDeviceUseCase] returns, and a running attempt is cancelled on Cancel,
 * on back, on a permission denial and in [onCleared].
 */
@Suppress("TooManyFunctions")
class LedgerHandshakeVM(
    application: Application,
    private val pairLedgerDevice: PairLedgerDeviceUseCase,
    private val selectWalletAccount: SelectWalletAccountUseCase,
    private val ledgerSelectedDeviceRepository: LedgerSelectedDeviceRepository,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val internalState = MutableStateFlow(LedgerHandshakeInternalState())

    private var pairJob: Job? = null

    val state: StateFlow<LedgerHandshakeState> =
        internalState
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(internalState.value)
            )

    init {
        startPairing()
    }

    override fun onCleared() {
        cancelPairing()
        super.onCleared()
    }

    private fun createState(internal: LedgerHandshakeInternalState): LedgerHandshakeState {
        val sheets = issueSheets(internal)
        return LedgerHandshakeState(
            isWaiting = internal.phase == LedgerHandshakePhase.WAITING,
            cancelButton =
                ButtonState(
                    text = stringRes(DesignR.string.general_cancel),
                    style = ButtonStyle.DESTRUCTIVE1,
                    onClick = ::onBack,
                ),
            retryButton = createRetryButton(internal, sheets),
            errorSheet = if (internal.isSheetShown) createErrorSheet(internal, sheets) else null,
            permissionRequestNonce = internal.permissionRequestNonce,
            enableBluetoothRequestNonce = internal.enableBluetoothRequestNonce,
            onBack = ::onBack,
        )
    }

    /**
     * Repeats the sheet's own action, which for most issues runs the pairing again. An issue that
     * trying again cannot fix, and an account already in the wallet, leave only Cancel.
     */
    private fun createRetryButton(
        internal: LedgerHandshakeInternalState,
        sheets: LedgerIssueSheetBuilder,
    ): ButtonState? {
        val issue = internal.issue
        return if (issue != null && issue.retry != LedgerIssueRetry.NONE) {
            ButtonState(
                text = stringRes(R.string.ledger_handshake_retry),
                style = ButtonStyle.PRIMARY,
                onClick = sheets.action(issue),
            )
        } else {
            null
        }
    }

    private fun createErrorSheet(
        internal: LedgerHandshakeInternalState,
        sheets: LedgerIssueSheetBuilder,
    ): LedgerErrorSheetState? {
        val alreadyAdded = internal.alreadyAdded
        val issue = internal.issue
        return when {
            alreadyAdded != null -> {
                ledgerAlreadyAddedSheet(
                    onGoToAccount = { onGoToAccountClick(alreadyAdded) },
                    onDismiss = ::onSheetDismissed,
                )
            }

            issue != null -> {
                sheets.sheet(issue)
            }

            else -> {
                null
            }
        }
    }

    private fun issueSheets(internal: LedgerHandshakeInternalState) =
        LedgerIssueSheetBuilder(
            canRequestPermissionsAgain = internal.canRequestPermissionsAgain,
            onTryAgain = ::startPairing,
            onRequestPermissionsAgain = ::onRequestPermissionsAgainClick,
            onOpenSettings = ::onOpenSettingsClick,
            onEnableBluetooth = ::onEnableBluetoothClick,
            onClose = ::onBack,
            onDismiss = ::onSheetDismissed,
        )

    /**
     * Also restarts after a permissions issue: permissions revoked since the scan make the pairing
     * fail while the system dialog is still up, and granting them should not need another tap.
     */
    fun onPermissionsGranted() {
        val internal = internalState.value
        if (internal.phase == LedgerHandshakePhase.PERMISSION || internal.issue?.kind == LedgerIssueKind.PERMISSIONS) {
            startPairing()
        }
    }

    fun onPermissionsDenied(canRequestAgain: Boolean) {
        cancelPairing()
        internalState.update { it.copy(canRequestPermissionsAgain = canRequestAgain) }
        showIssue(LedgerIssue.permissions)
    }

    /**
     * The system dialog turned Bluetooth on, so the pairing can be tried again.
     */
    fun onBluetoothEnabled() = startPairing()

    /**
     * The user declined the system dialog, so the sheet comes back over the page.
     */
    fun onBluetoothEnableDeclined() {
        internalState.update { it.copy(isSheetShown = it.issue != null) }
    }

    /**
     * Each attempt opens a fresh transport to the selected device. A running attempt is left alone
     * before the device is looked up, so a restart can never unwind the flow under it.
     */
    private fun startPairing() {
        if (pairJob?.isActive == true) return
        val device = ledgerSelectedDeviceRepository.get()
        if (device == null) {
            navigationRouter.backToRoot()
            return
        }
        internalState.update {
            it.copy(
                phase = LedgerHandshakePhase.WAITING,
                issue = null,
                alreadyAdded = null,
                isSheetShown = false,
            )
        }
        pairJob = viewModelScope.launch { pair(device) }
    }

    /**
     * On success this screen is replaced, so backing out of the birthday screens lands on the scan
     * screen rather than on a finished pairing. An account that was only missing its binding needs
     * no birthday: it is connected again at once.
     *
     * A failure while the phone was still connecting reads as a failed pairing; one after the link
     * was up, including the pairing running out of time, as a device that disconnected during setup.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun pair(device: LedgerBluetoothDevice) {
        try {
            when (val result = pairLedgerDevice(device)) {
                is PairLedgerDeviceResult.Paired -> {
                    navigationRouter.replace(HWNewOrActiveArgs(HWWalletEnrollment.Ledger))
                }

                is PairLedgerDeviceResult.Rebound -> {
                    navigationRouter.replace(LedgerConnectedArgs)
                }

                is PairLedgerDeviceResult.AlreadyAdded -> {
                    showAlreadyAdded(result.account)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerBondingFailedException) {
            showIssue(e.ledgerException.toEnrollmentIssue(LedgerIssueContext.ENROLLMENT_PAIRING))
        } catch (e: LedgerPairingTimedOutException) {
            Twig.warn { "Ledger enrollment failed: ${e.javaClass.simpleName}" }
            showIssue(LedgerIssue.disconnectedDuringSetup)
        } catch (e: LedgerException) {
            showIssue(e.toEnrollmentIssue(LedgerIssueContext.ENROLLMENT))
        } catch (e: Exception) {
            internalState.update {
                it.copy(
                    phase = LedgerHandshakePhase.IDLE,
                    issue = LedgerIssue.unknown,
                    isSheetShown = false,
                )
            }
            navigateToError(ErrorArgs.General(e))
        }
    }

    private fun onGoToAccountClick(account: WalletAccount) =
        viewModelScope.launch {
            selectWalletAccount(account, navigateBack = false)
            navigationRouter.backToRoot()
        }

    /**
     * Re-launches the runtime request the screen owns.
     */
    private fun onRequestPermissionsAgainClick() {
        internalState.update {
            it.copy(
                isSheetShown = false,
                phase = LedgerHandshakePhase.PERMISSION,
                permissionRequestNonce = it.permissionRequestNonce + 1,
            )
        }
    }

    /**
     * Back to [LedgerHandshakePhase.PERMISSION]: returning from Settings with the permission
     * granted restarts the pairing through the screen's ON_RESUME hook.
     */
    private fun onOpenSettingsClick() {
        internalState.update { it.copy(isSheetShown = false, phase = LedgerHandshakePhase.PERMISSION) }
        getApplication<Application>().startActivity(
            SettingsUtil.newSettingsIntent(getApplication<Application>().packageName)
        )
    }

    private fun onEnableBluetoothClick() {
        internalState.update {
            it.copy(
                isSheetShown = false,
                enableBluetoothRequestNonce = it.enableBluetoothRequestNonce + 1,
            )
        }
    }

    private fun onSheetDismissed() {
        internalState.update { it.copy(isSheetShown = false) }
    }

    /**
     * Cancel, the back arrow and system back all land here. A pairing still waiting on the device
     * is dropped at once rather than when the screen is disposed, so the device is not left holding
     * an export nobody will read.
     */
    private fun onBack() {
        cancelPairing()
        navigationRouter.back()
    }

    private fun cancelPairing() {
        pairJob?.cancel()
        pairJob = null
    }

    private fun showIssue(issue: LedgerIssue) {
        internalState.update {
            it.copy(
                phase = LedgerHandshakePhase.IDLE,
                issue = issue,
                alreadyAdded = null,
                isSheetShown = true,
            )
        }
    }

    private fun showAlreadyAdded(account: WalletAccount) {
        internalState.update {
            it.copy(
                phase = LedgerHandshakePhase.IDLE,
                issue = null,
                alreadyAdded = account,
                isSheetShown = true,
            )
        }
    }
}

private data class LedgerHandshakeInternalState(
    val phase: LedgerHandshakePhase = LedgerHandshakePhase.WAITING,
    val issue: LedgerIssue? = null,
    val alreadyAdded: WalletAccount? = null,
    val isSheetShown: Boolean = false,
    val canRequestPermissionsAgain: Boolean = false,
    val permissionRequestNonce: Int = 0,
    val enableBluetoothRequestNonce: Int = 0,
)

private enum class LedgerHandshakePhase {
    PERMISSION,
    WAITING,
    IDLE,
}
