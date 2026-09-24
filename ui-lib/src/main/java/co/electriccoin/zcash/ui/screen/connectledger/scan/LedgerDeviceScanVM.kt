package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toLedgerIssue
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.neworactive.HWNewOrActiveArgs
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorSheetState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the scan/select/pair phases of Ledger enrollment.
 *
 * Nothing Bluetooth-related outlives this screen: the scan job is cancelled in [onCleared], and
 * the transport a pairing opens is closed by the data source before [PairLedgerDeviceUseCase]
 * returns. Device identifiers are used only as list keys — never logged.
 */
@Suppress("TooManyFunctions")
class LedgerDeviceScanVM(
    application: Application,
    private val observeLedgerDevices: ObserveLedgerDevicesUseCase,
    private val pairLedgerDevice: PairLedgerDeviceUseCase,
    private val selectWalletAccount: SelectWalletAccountUseCase,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val internalState = MutableStateFlow(LedgerScanInternalState())

    private var scanJob: Job? = null

    private var pairJob: Job? = null

    private var scanTimeoutJob: Job? = null

    val state: StateFlow<LedgerDeviceScanState> =
        internalState
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(internalState.value)
            )

    /**
     * This screen sits under the birthday screens and is popped when the flow is abandoned or
     * finished, so its disposal is where an unused pairing stops being held.
     */
    override fun onCleared() {
        stopScan()
        pairJob?.cancel()
        pairJob = null
        ledgerPairingRepository.clear()
        super.onCleared()
    }

    private fun createState(internal: LedgerScanInternalState): LedgerDeviceScanState {
        val hasDevices = internal.devices.isNotEmpty()
        val isIdle = internal.phase == LedgerScanPhase.IDLE
        val pageIssue = internal.issue?.takeIf { !hasDevices }
        return LedgerDeviceScanState(
            title =
                when {
                    hasDevices -> stringRes(R.string.ledger_scan_select_title)
                    isIdle || pageIssue != null -> stringRes(R.string.ledger_scan_idle_title)
                    else -> stringRes(R.string.ledger_scan_searching_title)
                },
            subtitle =
                if (hasDevices) {
                    stringRes(R.string.ledger_scan_select_subtitle)
                } else {
                    stringRes(R.string.ledger_scan_searching_subtitle)
                },
            isScanning = internal.phase == LedgerScanPhase.SCANNING && !hasDevices,
            showDeviceSkeletons = !hasDevices && (!isIdle || pageIssue != null),
            devices =
                internal.devices.map { device ->
                    LedgerDeviceItemState(
                        name = stringRes(device.name ?: device.model.productName),
                        isSelected = device.identifier == internal.selectedIdentifier,
                        isEnabled = internal.phase != LedgerScanPhase.PAIRING,
                        onClick = { onDeviceClick(device.identifier) },
                    )
                },
            inlineIssue = pageIssue?.let { LedgerInlineIssueState(it.icon, it.title, it.message) },
            primaryButton = createPrimaryButton(internal, hasDevices, pageIssue),
            errorSheet = if (internal.isSheetShown) createErrorSheet(internal) else null,
            permissionRequestNonce = internal.permissionRequestNonce,
            enableBluetoothRequestNonce = internal.enableBluetoothRequestNonce,
            onBack = ::onBack,
        )
    }

    /**
     * Retained devices win over the idle retry: after an issue that keeps the link, the selected
     * row and an enabled Connect are how the user tries again.
     */
    private fun createPrimaryButton(
        internal: LedgerScanInternalState,
        hasDevices: Boolean,
        pageIssue: LedgerIssue?,
    ) = when {
        pageIssue?.kind == LedgerIssueKind.BLUETOOTH_UNAVAILABLE -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_select_cta),
                isEnabled = false,
            )
        }

        pageIssue != null -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_retry_cta),
                onClick = issueAction(internal, pageIssue),
            )
        }

        hasDevices -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_select_cta),
                isEnabled =
                    internal.selectedIdentifier != null && internal.phase != LedgerScanPhase.PAIRING,
                isLoading = internal.phase == LedgerScanPhase.PAIRING,
                onClick = ::onConnectClick,
            )
        }

        internal.phase == LedgerScanPhase.IDLE -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_retry_cta),
                onClick = ::onTryAgainClick,
            )
        }

        else -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_searching_cta),
                isEnabled = false,
                isLoading = internal.phase == LedgerScanPhase.SCANNING,
            )
        }
    }

    private fun createErrorSheet(internal: LedgerScanInternalState): LedgerErrorSheetState? {
        val alreadyAdded = internal.alreadyAdded
        val issue = internal.issue
        return when {
            alreadyAdded != null -> alreadyAddedSheet(alreadyAdded)
            issue != null -> issueSheet(internal, issue)
            else -> null
        }
    }

    private fun issueSheet(internal: LedgerScanInternalState, issue: LedgerIssue) =
        LedgerErrorSheetState(
            icon = issue.icon,
            title = issue.title,
            message = issue.message,
            primary =
                ButtonState(
                    text = issueActionText(internal, issue),
                    onClick = issueAction(internal, issue),
                ),
            secondary = null,
            onBack = ::onSheetDismissed,
        )

    private fun alreadyAddedSheet(account: WalletAccount) =
        LedgerErrorSheetState(
            icon = R.drawable.ic_ledger_alert_circle,
            title = stringRes(R.string.ledger_error_alreadyAdded_title),
            message = stringRes(R.string.ledger_error_alreadyAdded_message),
            primary =
                ButtonState(
                    text = stringRes(R.string.ledger_error_alreadyAdded_primary),
                    onClick = { onGoToAccountClick(account) },
                ),
            secondary =
                ButtonState(
                    text = stringRes(R.string.ledger_error_alreadyAdded_secondary),
                    style = ButtonStyle.SECONDARY,
                    onClick = ::onSheetDismissed,
                ),
            onBack = ::onSheetDismissed,
        )

    private fun issueActionText(internal: LedgerScanInternalState, issue: LedgerIssue) =
        when {
            issue.kind == LedgerIssueKind.BLUETOOTH_UNAVAILABLE -> {
                stringRes(R.string.ledger_error_unavailable_cta)
            }

            issue.kind == LedgerIssueKind.PERMISSIONS && !internal.canRequestPermissionsAgain -> {
                stringRes(R.string.ledger_error_permissions_cta)
            }

            else -> {
                stringRes(R.string.ledger_error_tryAgain)
            }
        }

    /**
     * What the sheet's primary button does, and the page's Try again with it.
     */
    private fun issueAction(internal: LedgerScanInternalState, issue: LedgerIssue): () -> Unit =
        when (issue.kind) {
            LedgerIssueKind.PERMISSIONS -> {
                if (internal.canRequestPermissionsAgain) {
                    ::onRequestPermissionsAgainClick
                } else {
                    ::onOpenSettingsClick
                }
            }

            LedgerIssueKind.BLUETOOTH_OFF -> {
                ::onEnableBluetoothClick
            }

            LedgerIssueKind.BLUETOOTH_UNAVAILABLE -> {
                ::onBack
            }

            else -> {
                ::onTryAgainClick
            }
        }

    fun onPermissionsGranted() {
        if (internalState.value.phase == LedgerScanPhase.PERMISSION) {
            startScan()
        }
    }

    fun onPermissionsDenied(canRequestAgain: Boolean) {
        internalState.update { it.copy(canRequestPermissionsAgain = canRequestAgain) }
        showIssue(LedgerIssue.permissions)
    }

    /**
     * The system dialog turned Bluetooth on; nothing but a fresh scan can find the device now.
     */
    fun onBluetoothEnabled() = startScan()

    /**
     * The user declined the system dialog. The inline issue stayed on the page throughout, so only
     * the sheet has to come back.
     */
    fun onBluetoothEnableDeclined() {
        internalState.update { it.copy(isSheetShown = it.issue != null) }
    }

    /**
     * Re-launches the runtime request the screen owns. A permission that can still be asked for
     * deserves another in-app prompt rather than a trip through Settings.
     */
    private fun onRequestPermissionsAgainClick() {
        internalState.update {
            it.copy(
                isSheetShown = false,
                phase = LedgerScanPhase.PERMISSION,
                permissionRequestNonce = it.permissionRequestNonce + 1,
            )
        }
    }

    private fun onEnableBluetoothClick() {
        internalState.update {
            it.copy(
                isSheetShown = false,
                enableBluetoothRequestNonce = it.enableBluetoothRequestNonce + 1,
            )
        }
    }

    private fun onDeviceClick(identifier: String) {
        if (internalState.value.phase == LedgerScanPhase.PAIRING) return
        internalState.update { it.copy(selectedIdentifier = identifier) }
    }

    private fun onConnectClick() {
        val device = selectedDevice() ?: return
        if (pairJob?.isActive == true) return
        stopScan()
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.PAIRING,
                issue = null,
                alreadyAdded = null,
                isSheetShown = false,
            )
        }
        pairJob = viewModelScope.launch { pair(device) }
    }

    private fun selectedDevice(): LedgerBluetoothDevice? {
        val internal = internalState.value
        return internal.devices.firstOrNull { it.identifier == internal.selectedIdentifier }
    }

    /**
     * A successful pairing leaves the screen idle rather than pairing: backing out of the birthday
     * screens returns here, and a screen still stuck in [LedgerScanPhase.PAIRING] would show
     * disabled rows and a spinning Connect with no way out.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun pair(device: LedgerBluetoothDevice) {
        try {
            when (val result = pairLedgerDevice(device)) {
                is PairLedgerDeviceResult.Paired -> {
                    navigationRouter.forward(HWNewOrActiveArgs(HWWalletEnrollment.Ledger))
                    internalState.update {
                        it.copy(
                            phase = LedgerScanPhase.IDLE,
                            devices = emptyList(),
                            selectedIdentifier = null,
                        )
                    }
                }

                is PairLedgerDeviceResult.AlreadyAdded -> {
                    showAlreadyAdded(result.account)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException) {
            showIssue(e.toEnrollmentIssue())
        } catch (e: Exception) {
            internalState.update { it.copy(phase = LedgerScanPhase.IDLE) }
            navigateToError(ErrorArgs.General(e))
        }
    }

    private fun onGoToAccountClick(account: WalletAccount) =
        viewModelScope.launch {
            selectWalletAccount(account, navigateBack = false)
            navigationRouter.backToRoot()
        }

    /**
     * Back to [LedgerScanPhase.PERMISSION], not IDLE: the screen's ON_RESUME hook only starts a
     * scan from that phase, and returning from Settings with the permission granted has to start
     * one without another tap.
     */
    private fun onOpenSettingsClick() {
        internalState.update { it.copy(isSheetShown = false, phase = LedgerScanPhase.PERMISSION) }
        getApplication<Application>().startActivity(
            SettingsUtil.newSettingsIntent(getApplication<Application>().packageName)
        )
    }

    /**
     * An issue that keeps the link repeats the request to the still selected device; any other
     * starts over with a scan. Each pairing attempt opens a fresh transport.
     */
    private fun onTryAgainClick() {
        if (selectedDevice() != null) {
            onConnectClick()
        } else {
            startScan()
        }
    }

    private fun onSheetDismissed() {
        internalState.update { it.copy(isSheetShown = false) }
    }

    private fun onBack() = navigationRouter.back()

    private fun startScan() {
        pairJob?.cancel()
        pairJob = null
        stopScan()
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.SCANNING,
                devices = emptyList(),
                selectedIdentifier = null,
                issue = null,
                alreadyAdded = null,
                isSheetShown = false,
            )
        }
        scanJob = viewModelScope.launch { scan() }
        scanTimeoutJob =
            viewModelScope.launch {
                delay(SCAN_TIMEOUT)
                if (internalState.value.devices.isEmpty()) {
                    showIssue(LedgerIssue.noDevices)
                }
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun scan() {
        try {
            observeLedgerDevices().collect { devices ->
                internalState.update { current ->
                    current.copy(
                        devices = devices,
                        selectedIdentifier =
                            current.selectedIdentifier?.takeIf { selected ->
                                devices.any { it.identifier == selected }
                            },
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException) {
            showIssue(e.toEnrollmentIssue())
        } catch (e: Exception) {
            stopScan()
            internalState.update { it.copy(phase = LedgerScanPhase.IDLE) }
            navigateToError(ErrorArgs.General(e))
        }
    }

    /**
     * Cancels the scan and the timer together: a timer left running from an earlier session would
     * otherwise fire into the next one and report "no devices" while it is still searching.
     */
    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
    }

    /**
     * An issue that keeps the link leaves the device row selected so Connect can repeat the
     * request; any other clears the list so the page shows the issue over the placeholder rows.
     */
    private fun showIssue(issue: LedgerIssue) {
        stopScan()
        val keepsDevice = issue.retry == LedgerIssueRetry.SAME_LINK
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.IDLE,
                devices = if (keepsDevice) it.devices else emptyList(),
                selectedIdentifier = if (keepsDevice) it.selectedIdentifier else null,
                issue = issue,
                alreadyAdded = null,
                isSheetShown = true,
            )
        }
    }

    private fun showAlreadyAdded(account: WalletAccount) {
        stopScan()
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.IDLE,
                issue = null,
                alreadyAdded = account,
                isSheetShown = true,
            )
        }
    }
}

/**
 * Logs only the exception's class and its [LedgerException.reason], which the SDK keeps free of
 * device identifiers.
 */
private fun LedgerException.toEnrollmentIssue(): LedgerIssue {
    Twig.warn { "Ledger enrollment failed: ${javaClass.simpleName}, reason: $reason" }
    return toLedgerIssue(LedgerIssueContext.ENROLLMENT)
}

private data class LedgerScanInternalState(
    val phase: LedgerScanPhase = LedgerScanPhase.PERMISSION,
    val devices: List<LedgerBluetoothDevice> = emptyList(),
    val selectedIdentifier: String? = null,
    val issue: LedgerIssue? = null,
    val alreadyAdded: WalletAccount? = null,
    val isSheetShown: Boolean = false,
    val canRequestPermissionsAgain: Boolean = false,
    val permissionRequestNonce: Int = 0,
    val enableBluetoothRequestNonce: Int = 0,
)

private enum class LedgerScanPhase {
    PERMISSION,
    SCANNING,
    PAIRING,
    IDLE,
}

private val SCAN_TIMEOUT = 30.seconds
