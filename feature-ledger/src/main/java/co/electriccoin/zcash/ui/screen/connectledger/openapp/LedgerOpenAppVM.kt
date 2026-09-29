package co.electriccoin.zcash.ui.screen.connectledger.openapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.usecase.OpenLedgerZcashAppResult
import co.electriccoin.zcash.ui.common.usecase.OpenLedgerZcashAppUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerInlineIssueState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerIssueSheetBuilder
import co.electriccoin.zcash.ui.screen.connectledger.common.toEnrollmentIssue
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeArgs
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

/**
 * Asks the Ledger the scan screen bonded with to open the Zcash app as soon as the screen opens,
 * and moves on to the handshake once the app runs. The user confirms the request on the device.
 *
 * The request runs only once on its own, and not at all when [LedgerOpenAppArgs.autoOpen] is off
 * because the scan screen found the app open and went straight on to the handshake: coming back
 * from the handshake leaves the page idle, so the already open app does not bounce the user forward
 * again, and the page button asks again.
 * Failures open the same sheets as the other enrollment screens; the phone bonded with the device
 * on the scan screen already, so a lost connection here reads as a device that disconnected during
 * setup rather than a failed pairing. When process death has emptied
 * the selected device, the flow falls back to the wallet root. A running request is cancelled on
 * back and in [onCleared].
 */
@Suppress("TooManyFunctions")
class LedgerOpenAppVM(
    args: LedgerOpenAppArgs,
    application: Application,
    private val openLedgerZcashApp: OpenLedgerZcashAppUseCase,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val internalState =
        MutableStateFlow(
            LedgerOpenAppInternalState(
                phase = if (args.autoOpen) LedgerOpenAppPhase.OPENING else LedgerOpenAppPhase.IDLE
            )
        )

    private var openAppJob: Job? = null

    val state: StateFlow<LedgerOpenAppState> =
        internalState
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(internalState.value)
            )

    init {
        if (args.autoOpen) startOpenApp()
    }

    override fun onCleared() {
        cancelOpenApp()
        super.onCleared()
    }

    private fun createState(internal: LedgerOpenAppInternalState): LedgerOpenAppState {
        val sheets = issueSheets(internal)
        val issue = internal.issue
        return LedgerOpenAppState(
            inlineIssue = issue?.let { LedgerInlineIssueState(it.icon, it.title, it.message) },
            primaryButton = createPrimaryButton(internal, sheets),
            errorSheet = if (internal.isSheetShown && issue != null) sheets.sheet(issue) else null,
            permissionRequestNonce = internal.permissionRequestNonce,
            enableBluetoothRequestNonce = internal.enableBluetoothRequestNonce,
            onBack = ::onBack,
        )
    }

    /**
     * An issue that trying again cannot fix leaves only a disabled Continue.
     */
    private fun createPrimaryButton(
        internal: LedgerOpenAppInternalState,
        sheets: LedgerIssueSheetBuilder,
    ): ButtonState {
        val issue = internal.issue
        val isOpening = internal.phase == LedgerOpenAppPhase.OPENING
        return when {
            issue != null && !isOpening && sheets.hasAction(issue) -> {
                ButtonState(
                    text = sheets.actionText(issue),
                    onClick = sheets.action(issue),
                )
            }

            issue != null && !isOpening -> {
                ButtonState(
                    text = stringRes(R.string.ledger_connect_continue),
                    isEnabled = false,
                )
            }

            else -> {
                ButtonState(
                    text = stringRes(R.string.ledger_connect_continue),
                    isEnabled = !isOpening,
                    isLoading = isOpening,
                    onClick = ::startOpenApp,
                )
            }
        }
    }

    private fun issueSheets(internal: LedgerOpenAppInternalState) =
        LedgerIssueSheetBuilder(
            canRequestPermissionsAgain = internal.canRequestPermissionsAgain,
            onTryAgain = ::startOpenApp,
            onRequestPermissionsAgain = ::onRequestPermissionsAgainClick,
            onOpenSettings = ::onOpenSettingsClick,
            onEnableBluetooth = ::onEnableBluetoothClick,
            onClose = ::onBack,
            onDismiss = ::onSheetDismissed,
        )

    /**
     * Restarts only after a permissions issue, never on a plain resume: the screen reports granted
     * permissions on every resume, including the return from the handshake.
     */
    fun onPermissionsGranted() {
        val internal = internalState.value
        if (internal.phase == LedgerOpenAppPhase.PERMISSION || internal.issue?.kind == LedgerIssueKind.PERMISSIONS) {
            startOpenApp()
        }
    }

    fun onPermissionsDenied(canRequestAgain: Boolean) {
        cancelOpenApp()
        internalState.update { it.copy(canRequestPermissionsAgain = canRequestAgain) }
        showIssue(LedgerIssue.permissions)
    }

    /**
     * The system dialog turned Bluetooth on, so the request can be tried again.
     */
    fun onBluetoothEnabled() = startOpenApp()

    /**
     * The user declined the system dialog. The inline issue stayed on the page throughout, so only
     * the sheet has to come back.
     */
    fun onBluetoothEnableDeclined() {
        internalState.update { it.copy(isSheetShown = it.issue != null) }
    }

    private fun startOpenApp() {
        if (openAppJob?.isActive == true) return
        internalState.update {
            it.copy(
                phase = LedgerOpenAppPhase.OPENING,
                issue = null,
                isSheetShown = false,
            )
        }
        openAppJob = viewModelScope.launch { openApp() }
    }

    /**
     * On success the page goes back to idle behind the handshake, so returning to it waits for the
     * user instead of asking the device again.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun openApp() {
        try {
            when (openLedgerZcashApp()) {
                OpenLedgerZcashAppResult.Opened -> {
                    internalState.update { it.copy(phase = LedgerOpenAppPhase.IDLE) }
                    navigationRouter.forward(LedgerHandshakeArgs)
                }

                OpenLedgerZcashAppResult.NoDevice -> {
                    internalState.update { it.copy(phase = LedgerOpenAppPhase.IDLE) }
                    navigationRouter.backToRoot()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LedgerException) {
            showIssue(e.toEnrollmentIssue(LedgerIssueContext.ENROLLMENT))
        } catch (e: Exception) {
            internalState.update {
                it.copy(
                    phase = LedgerOpenAppPhase.IDLE,
                    issue = LedgerIssue.unknown,
                    isSheetShown = false,
                )
            }
            navigateToError(ErrorArgs.General(e))
        }
    }

    private fun cancelOpenApp() {
        openAppJob?.cancel()
        openAppJob = null
    }

    /**
     * Re-launches the runtime request the screen owns.
     */
    private fun onRequestPermissionsAgainClick() {
        internalState.update {
            it.copy(
                isSheetShown = false,
                phase = LedgerOpenAppPhase.PERMISSION,
                permissionRequestNonce = it.permissionRequestNonce + 1,
            )
        }
    }

    /**
     * Back to [LedgerOpenAppPhase.PERMISSION]: returning from Settings with the permission granted
     * restarts the request through the screen's ON_RESUME hook.
     */
    private fun onOpenSettingsClick() {
        internalState.update { it.copy(isSheetShown = false, phase = LedgerOpenAppPhase.PERMISSION) }
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

    private fun onBack() {
        cancelOpenApp()
        internalState.update { it.copy(phase = LedgerOpenAppPhase.IDLE) }
        navigationRouter.back()
    }

    private fun showIssue(issue: LedgerIssue) {
        internalState.update {
            it.copy(
                phase = LedgerOpenAppPhase.IDLE,
                issue = issue,
                isSheetShown = true,
            )
        }
    }
}

private data class LedgerOpenAppInternalState(
    val phase: LedgerOpenAppPhase = LedgerOpenAppPhase.OPENING,
    val issue: LedgerIssue? = null,
    val isSheetShown: Boolean = false,
    val canRequestPermissionsAgain: Boolean = false,
    val permissionRequestNonce: Int = 0,
    val enableBluetoothRequestNonce: Int = 0,
)

private enum class LedgerOpenAppPhase {
    PERMISSION,
    OPENING,
    IDLE,
}
