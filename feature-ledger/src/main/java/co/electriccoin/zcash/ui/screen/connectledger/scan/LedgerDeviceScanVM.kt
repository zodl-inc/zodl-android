package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.provider.LEDGER_SCAN_TIMEOUT
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.ConnectLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceItemState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerIssueSheetBuilder
import co.electriccoin.zcash.ui.screen.connectledger.common.toEnrollmentIssue
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppArgs
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

/**
 * Drives the scan/select/connect phases of Ledger enrollment. Connecting only bonds the phone with
 * the device; the Zcash app is opened, and the account exported, on the screens that follow.
 *
 * Nothing Bluetooth-related outlives this screen: the scan job is cancelled in [onCleared], and
 * the transport a connection opens is closed by the data source before
 * [ConnectLedgerDeviceUseCase] returns. Device identifiers are used only as list keys — never
 * logged.
 */
@Suppress("TooManyFunctions")
class LedgerDeviceScanVM(
    application: Application,
    private val observeLedgerDevices: ObserveLedgerDevicesUseCase,
    private val connectLedgerDevice: ConnectLedgerDeviceUseCase,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val ledgerSelectedDeviceRepository: LedgerSelectedDeviceRepository,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val internalState = MutableStateFlow(LedgerScanInternalState())

    private var scanJob: Job? = null

    private var connectJob: Job? = null

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
     * This screen sits under the rest of the enrollment and is popped when the flow is abandoned
     * or finished, so its disposal is where the connected device and an unused pairing stop being
     * held.
     */
    override fun onCleared() {
        stopScan()
        connectJob?.cancel()
        connectJob = null
        ledgerSelectedDeviceRepository.clear()
        ledgerPairingRepository.clear()
        super.onCleared()
    }

    private fun createState(internal: LedgerScanInternalState): LedgerDeviceScanState {
        val hasDevices = internal.devices.isNotEmpty()
        val isIdle = internal.phase == LedgerScanPhase.IDLE
        val pageIssue = internal.issue?.takeIf { !hasDevices }
        val isConnecting = internal.phase == LedgerScanPhase.CONNECTING
        val sheets = issueSheets(internal)
        val page = pageCopy(hasDevices, isIdle, pageIssue)
        return LedgerDeviceScanState(
            title = stringRes(page.title),
            subtitle =
                if (hasDevices && isConnecting) {
                    stringRes(R.string.ledger_scan_connecting_subtitle)
                } else {
                    stringRes(page.subtitle)
                },
            navigation = page.navigation,
            isScanning = internal.phase == LedgerScanPhase.SCANNING && !hasDevices,
            showDeviceSkeletons = !hasDevices && (!isIdle || pageIssue != null),
            devices =
                internal.devices.map { device ->
                    LedgerDeviceItemState(
                        name = stringRes(device.name ?: device.model.productName),
                        isSelected = device.identifier == internal.selectedIdentifier,
                        isEnabled = !isConnecting,
                        onClick = { onDeviceClick(device.identifier) },
                    )
                },
            inlineIssue = pageIssue?.let { LedgerInlineIssueState(it.inlineIcon, it.inlineTitle, it.inlineMessage) },
            primaryButton = createPrimaryButton(internal, sheets, hasDevices, pageIssue),
            errorSheet = internal.issue?.takeIf { internal.isSheetShown }?.let(sheets::sheet),
            permissionRequestNonce = internal.permissionRequestNonce,
            enableBluetoothRequestNonce = internal.enableBluetoothRequestNonce,
            onBack = ::onBack,
        )
    }

    /**
     * The page copy and navigation icon per the Figma connect error frames: a Bluetooth issue, or a
     * device list, reads as the selection step behind a back arrow; no devices found keeps the
     * searching title over the selection subtitle; any other issue reads as the search behind a
     * close. Both icons leave the screen the same way.
     */
    private fun pageCopy(
        hasDevices: Boolean,
        isIdle: Boolean,
        pageIssue: LedgerIssue?,
    ): LedgerScanPageCopy =
        when {
            hasDevices || pageIssue?.kind in BLUETOOTH_ISSUE_KINDS -> {
                LedgerScanPageCopy(
                    title = R.string.ledger_scan_select_title,
                    subtitle = R.string.ledger_scan_select_subtitle,
                    navigation = LedgerDeviceScanNavigation.BACK,
                )
            }

            pageIssue?.kind == LedgerIssueKind.NO_DEVICES -> {
                LedgerScanPageCopy(
                    title = R.string.ledger_scan_searching_title,
                    subtitle = R.string.ledger_scan_select_subtitle,
                    navigation = LedgerDeviceScanNavigation.CLOSE,
                )
            }

            pageIssue == null && isIdle -> {
                LedgerScanPageCopy(
                    title = R.string.ledger_scan_idle_title,
                    subtitle = R.string.ledger_scan_searching_subtitle,
                    navigation = LedgerDeviceScanNavigation.CLOSE,
                )
            }

            else -> {
                LedgerScanPageCopy(
                    title = R.string.ledger_scan_searching_title,
                    subtitle = R.string.ledger_scan_searching_subtitle,
                    navigation = LedgerDeviceScanNavigation.CLOSE,
                )
            }
        }

    /**
     * Retained devices win over the idle retry: after an issue that keeps the link, the selected
     * row and an enabled Connect are how the user tries again. An issue that trying again cannot
     * fix leaves only a disabled Connect.
     */
    private fun createPrimaryButton(
        internal: LedgerScanInternalState,
        sheets: LedgerIssueSheetBuilder,
        hasDevices: Boolean,
        pageIssue: LedgerIssue?,
    ) = when {
        pageIssue?.retry == LedgerIssueRetry.NONE -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_select_cta),
                isEnabled = false,
            )
        }

        pageIssue != null && pageIssue.kind == LedgerIssueKind.PERMISSIONS && !internal.canRequestPermissionsAgain -> {
            ButtonState(
                text = sheets.actionText(pageIssue),
                onClick = sheets.action(pageIssue),
            )
        }

        pageIssue != null -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_retry_cta),
                onClick = sheets.action(pageIssue),
            )
        }

        hasDevices -> {
            ButtonState(
                text = stringRes(R.string.ledger_scan_select_cta),
                isEnabled =
                    internal.selectedIdentifier != null && internal.phase != LedgerScanPhase.CONNECTING,
                isLoading = internal.phase == LedgerScanPhase.CONNECTING,
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

    private fun issueSheets(internal: LedgerScanInternalState) =
        LedgerIssueSheetBuilder(
            canRequestPermissionsAgain = internal.canRequestPermissionsAgain,
            onTryAgain = ::onTryAgainClick,
            onRequestPermissionsAgain = ::onRequestPermissionsAgainClick,
            onOpenSettings = ::onOpenSettingsClick,
            onEnableBluetooth = ::onEnableBluetoothClick,
            onClose = ::onBack,
            onDismiss = ::onSheetDismissed,
        )

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
        if (internalState.value.phase == LedgerScanPhase.CONNECTING) return
        internalState.update { it.copy(selectedIdentifier = identifier) }
    }

    private fun onConnectClick() {
        val device = selectedDevice() ?: return
        if (connectJob?.isActive == true) return
        stopScan()
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.CONNECTING,
                issue = null,
                isSheetShown = false,
            )
        }
        connectJob = viewModelScope.launch { connect(device) }
    }

    private fun selectedDevice(): LedgerBluetoothDevice? {
        val internal = internalState.value
        return internal.devices.firstOrNull { it.identifier == internal.selectedIdentifier }
    }

    /**
     * A successful connection leaves the screen idle rather than connecting: backing out of the
     * screens that follow returns here, and a screen still stuck in [LedgerScanPhase.CONNECTING]
     * would show disabled rows and a spinning Connect with no way out.
     *
     * Connecting is the step that bonds the phone with the device, so a lost or refused connection
     * here reads as a failed pairing.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun connect(device: LedgerBluetoothDevice) {
        try {
            connectLedgerDevice(device)
            navigationRouter.forward(LedgerOpenAppArgs)
            internalState.update {
                it.copy(
                    phase = LedgerScanPhase.IDLE,
                    devices = emptyList(),
                    selectedIdentifier = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException) {
            showIssue(e.toEnrollmentIssue(LedgerIssueContext.ENROLLMENT_PAIRING))
        } catch (e: Exception) {
            internalState.update { it.copy(phase = LedgerScanPhase.IDLE) }
            navigateToError(ErrorArgs.General(e))
        }
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
     * An issue that keeps the link connects to the still selected device again; any other starts
     * over with a scan. Each attempt opens a fresh transport.
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

    /**
     * Location being off below API 31 is reported before scanning, as nothing could be found.
     */
    private fun startScan() {
        connectJob?.cancel()
        connectJob = null
        stopScan()
        if (observeLedgerDevices.isLocationOffForScan()) {
            internalState.update { it.copy(canRequestPermissionsAgain = false) }
            showIssue(LedgerIssue.locationOff)
            return
        }
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.SCANNING,
                devices = emptyList(),
                selectedIdentifier = null,
                issue = null,
                isSheetShown = false,
            )
        }
        scanJob = viewModelScope.launch { scan() }
        scanTimeoutJob =
            viewModelScope.launch {
                delay(LEDGER_SCAN_TIMEOUT)
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
            showIssue(e.toEnrollmentIssue(LedgerIssueContext.ENROLLMENT_PAIRING))
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
     * attempt; any other clears the list so the page shows the issue over the placeholder rows.
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
                isSheetShown = true,
            )
        }
    }
}

private data class LedgerScanInternalState(
    val phase: LedgerScanPhase = LedgerScanPhase.PERMISSION,
    val devices: List<LedgerBluetoothDevice> = emptyList(),
    val selectedIdentifier: String? = null,
    val issue: LedgerIssue? = null,
    val isSheetShown: Boolean = false,
    val canRequestPermissionsAgain: Boolean = false,
    val permissionRequestNonce: Int = 0,
    val enableBluetoothRequestNonce: Int = 0,
)

private data class LedgerScanPageCopy(
    @get:StringRes val title: Int,
    @get:StringRes val subtitle: Int,
    val navigation: LedgerDeviceScanNavigation,
)

private val BLUETOOTH_ISSUE_KINDS =
    setOf(
        LedgerIssueKind.PERMISSIONS,
        LedgerIssueKind.BLUETOOTH_OFF,
        LedgerIssueKind.BLUETOOTH_UNAVAILABLE,
    )

private enum class LedgerScanPhase {
    PERMISSION,
    SCANNING,
    CONNECTING,
    IDLE,
}
