package co.electriccoin.zcash.ui.screen.connectledger.scan

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthardware.neworactive.HardwareNewOrActiveArgs
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
        return LedgerDeviceScanState(
            title =
                when {
                    hasDevices -> stringRes(R.string.ledger_scan_select_title)
                    isIdle -> stringRes(R.string.ledger_scan_idle_title)
                    else -> stringRes(R.string.ledger_scan_searching_title)
                },
            subtitle =
                if (hasDevices) {
                    stringRes(R.string.ledger_scan_select_subtitle)
                } else {
                    stringRes(R.string.ledger_scan_searching_subtitle)
                },
            isScanning = internal.phase == LedgerScanPhase.SCANNING && !hasDevices,
            showDeviceSkeletons = !hasDevices && !isIdle,
            devices =
                internal.devices.map { device ->
                    LedgerDeviceItemState(
                        name = stringRes(device.name ?: device.model.productName),
                        isSelected = device.identifier == internal.selectedIdentifier,
                        isEnabled = internal.phase != LedgerScanPhase.PAIRING,
                        onClick = { onDeviceClick(device.identifier) },
                    )
                },
            primaryButton = createPrimaryButton(internal, hasDevices),
            errorSheet = internal.error?.let { createErrorSheet(it) },
            permissionRequestNonce = internal.permissionRequestNonce,
            onPermissionsGranted = ::onPermissionsGranted,
            onPermissionsDenied = ::onPermissionsDenied,
            onBack = ::onBack,
        )
    }

    private fun createPrimaryButton(internal: LedgerScanInternalState, hasDevices: Boolean) =
        when {
            internal.phase == LedgerScanPhase.IDLE -> {
                ButtonState(
                    text = stringRes(R.string.ledger_scan_retry_cta),
                    onClick = ::onTryAgainClick,
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

            else -> {
                ButtonState(
                    text = stringRes(R.string.ledger_scan_searching_cta),
                    isEnabled = false,
                    isLoading = internal.phase == LedgerScanPhase.SCANNING,
                )
            }
        }

    private fun createErrorSheet(error: LedgerScanError): LedgerErrorSheetState =
        when (error) {
            is LedgerScanError.NoDevices -> {
                retrySheet(R.string.ledger_error_noDevices_title, R.string.ledger_error_noDevices_message)
            }

            is LedgerScanError.Locked -> {
                retrySheet(R.string.ledger_error_locked_title, R.string.ledger_error_locked_message)
            }

            is LedgerScanError.PairingFailed -> {
                retrySheet(R.string.ledger_error_pairingFailed_title, R.string.ledger_error_pairingFailed_message)
            }

            is LedgerScanError.ImportRejected -> {
                retrySheet(R.string.ledger_error_importRejected_title, R.string.ledger_error_importRejected_message)
            }

            is LedgerScanError.Disconnected -> {
                retrySheet(R.string.ledger_error_disconnected_title, R.string.ledger_error_disconnected_message)
            }

            is LedgerScanError.Unavailable -> {
                LedgerErrorSheetState(
                    title = stringRes(R.string.ledger_error_unavailable_title),
                    message = stringRes(R.string.ledger_error_unavailable_message),
                    primary =
                        ButtonState(
                            text = stringRes(R.string.ledger_error_unavailable_cta),
                            onClick = ::onBack,
                        ),
                    secondary = null,
                    onBack = ::onBack,
                )
            }

            is LedgerScanError.Permissions -> {
                LedgerErrorSheetState(
                    title = stringRes(R.string.ledger_error_permissions_title),
                    message = stringRes(R.string.ledger_error_permissions_message),
                    primary =
                        if (error.canRequestAgain) {
                            ButtonState(
                                text = stringRes(R.string.ledger_error_tryAgain),
                                onClick = ::onRequestPermissionsAgainClick,
                            )
                        } else {
                            ButtonState(
                                text = stringRes(R.string.ledger_error_permissions_cta),
                                onClick = { onOpenSettingsClick(error.openBluetoothSettings) },
                            )
                        },
                    secondary = null,
                    onBack = ::onSheetDismissed,
                )
            }

            is LedgerScanError.AlreadyAdded -> {
                LedgerErrorSheetState(
                    title = stringRes(R.string.ledger_error_alreadyAdded_title),
                    message = stringRes(R.string.ledger_error_alreadyAdded_message),
                    primary =
                        ButtonState(
                            text = stringRes(R.string.ledger_error_alreadyAdded_primary),
                            onClick = { onGoToAccountClick(error.account) },
                        ),
                    secondary =
                        ButtonState(
                            text = stringRes(R.string.ledger_error_alreadyAdded_secondary),
                            style = ButtonStyle.SECONDARY,
                            onClick = ::onSheetDismissed,
                        ),
                    onBack = ::onSheetDismissed,
                )
            }
        }

    private fun retrySheet(title: Int, message: Int) =
        LedgerErrorSheetState(
            title = stringRes(title),
            message = stringRes(message),
            primary =
                ButtonState(
                    text = stringRes(R.string.ledger_error_tryAgain),
                    onClick = ::onTryAgainClick,
                ),
            secondary = null,
            onBack = ::onSheetDismissed,
        )

    private fun onPermissionsGranted() {
        if (internalState.value.phase == LedgerScanPhase.PERMISSION) {
            startScan()
        }
    }

    private fun onPermissionsDenied(canRequestAgain: Boolean) {
        stopScan()
        internalState.update {
            it.copy(
                phase = LedgerScanPhase.IDLE,
                error = LedgerScanError.Permissions(canRequestAgain = canRequestAgain),
            )
        }
    }

    /**
     * Re-launches the runtime request the screen owns. A permission that can still be asked for
     * deserves another in-app prompt rather than a trip through Settings.
     */
    private fun onRequestPermissionsAgainClick() {
        internalState.update {
            it.copy(
                error = null,
                phase = LedgerScanPhase.PERMISSION,
                permissionRequestNonce = it.permissionRequestNonce + 1,
            )
        }
    }

    private fun onDeviceClick(identifier: String) {
        if (internalState.value.phase == LedgerScanPhase.PAIRING) return
        internalState.update { it.copy(selectedIdentifier = identifier) }
    }

    private fun onConnectClick() {
        val internal = internalState.value
        val device = internal.devices.firstOrNull { it.identifier == internal.selectedIdentifier } ?: return
        if (pairJob?.isActive == true) return
        stopScan()
        internalState.update { it.copy(phase = LedgerScanPhase.PAIRING, error = null) }
        pairJob = viewModelScope.launch { pair(device) }
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
                    navigationRouter.forward(HardwareNewOrActiveArgs(HardwareWalletEnrollment.Ledger))
                    internalState.update {
                        it.copy(
                            phase = LedgerScanPhase.IDLE,
                            devices = emptyList(),
                            selectedIdentifier = null,
                        )
                    }
                }

                is PairLedgerDeviceResult.AlreadyAdded -> {
                    showError(LedgerScanError.AlreadyAdded(result.account))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException) {
            val error = e.toScanError()
            if (error == null) {
                internalState.update { it.copy(phase = LedgerScanPhase.IDLE) }
                navigateToError(ErrorArgs.General(e))
            } else {
                showError(error)
            }
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
    private fun onOpenSettingsClick(openBluetoothSettings: Boolean) {
        internalState.update { it.copy(error = null, phase = LedgerScanPhase.PERMISSION) }
        val intent =
            if (openBluetoothSettings) {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { flags = SettingsUtil.FLAGS }
            } else {
                SettingsUtil.newSettingsIntent(getApplication<Application>().packageName)
            }
        getApplication<Application>().startActivity(intent)
    }

    private fun onTryAgainClick() = startScan()

    private fun onSheetDismissed() {
        internalState.update { it.copy(error = null, phase = LedgerScanPhase.IDLE) }
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
                error = null,
            )
        }
        scanJob = viewModelScope.launch { scan() }
        scanTimeoutJob =
            viewModelScope.launch {
                delay(SCAN_TIMEOUT)
                if (internalState.value.devices.isEmpty()) {
                    showError(LedgerScanError.NoDevices)
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
            showError(e.toScanError() ?: LedgerScanError.NoDevices)
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

    private fun showError(error: LedgerScanError) {
        stopScan()
        internalState.update { it.copy(phase = LedgerScanPhase.IDLE, error = error) }
    }
}

/**
 * The sheet a [LedgerException] maps to, or null when it has no Ledger-specific copy and belongs
 * in the generic error dialog instead.
 *
 * A [LedgerException.BluetoothUnavailable] carrying a scan error code means the scan itself failed
 * to start; without one the phone has no Bluetooth LE at all, which no settings screen can fix.
 */
private fun LedgerException.toScanError(): LedgerScanError? =
    when (this) {
        is LedgerException.PairingRefused,
        is LedgerException.ConnectionFailed,
        is LedgerException.DeviceNotFound,
        is LedgerException.Timeout -> {
            LedgerScanError.PairingFailed
        }

        is LedgerException.WrongApp,
        is LedgerException.DeviceRefused,
        is LedgerException.DerivationBudgetExhausted,
        is LedgerException.AppTooOld -> {
            LedgerScanError.Locked
        }

        is LedgerException.UserRejected -> {
            LedgerScanError.ImportRejected
        }

        is LedgerException.Disconnected -> {
            LedgerScanError.Disconnected
        }

        is LedgerException.BluetoothDisabled -> {
            LedgerScanError.Permissions(openBluetoothSettings = true)
        }

        is LedgerException.BluetoothUnauthorized -> {
            LedgerScanError.Permissions(canRequestAgain = false)
        }

        is LedgerException.BluetoothUnavailable -> {
            if (scanErrorCode != null) {
                LedgerScanError.NoDevices
            } else {
                LedgerScanError.Unavailable
            }
        }

        else -> {
            null
        }
    }

private data class LedgerScanInternalState(
    val phase: LedgerScanPhase = LedgerScanPhase.PERMISSION,
    val devices: List<LedgerBluetoothDevice> = emptyList(),
    val selectedIdentifier: String? = null,
    val error: LedgerScanError? = null,
    val permissionRequestNonce: Int = 0,
)

private enum class LedgerScanPhase {
    PERMISSION,
    SCANNING,
    PAIRING,
    IDLE,
}

private sealed interface LedgerScanError {
    data object NoDevices : LedgerScanError

    data object Unavailable : LedgerScanError

    data object Locked : LedgerScanError

    data object PairingFailed : LedgerScanError

    data object ImportRejected : LedgerScanError

    data object Disconnected : LedgerScanError

    data class Permissions(
        val canRequestAgain: Boolean = false,
        val openBluetoothSettings: Boolean = false,
    ) : LedgerScanError

    data class AlreadyAdded(
        val account: WalletAccount
    ) : LedgerScanError
}

private val SCAN_TIMEOUT = 30.seconds
