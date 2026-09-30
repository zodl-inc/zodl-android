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
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueContext
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerPairingTimedOutException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceResult
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
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

/**
 * Asks the Ledger the scan screen bonded with for its account, over a fresh link, once the user
 * has opened the Zcash app on it. The handshake starts as soon as the screen opens.
 *
 * The device comes from [LedgerSelectedDeviceRepository]; when process death has emptied it, the
 * flow falls back to the wallet root. The transport each attempt opens is closed by the data
 * source before [PairLedgerDeviceUseCase] returns, and a running attempt is cancelled on back
 * and in [onCleared].
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

    private var handshakeJob: Job? = null

    val state: StateFlow<LedgerHandshakeState> =
        internalState
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(internalState.value)
            )

    init {
        startHandshake()
    }

    override fun onCleared() {
        cancelHandshake()
        super.onCleared()
    }

    private fun createState(internal: LedgerHandshakeInternalState): LedgerHandshakeState {
        val sheets = issueSheets(internal)
        val isConnecting = internal.phase == LedgerHandshakePhase.CONNECTING
        return LedgerHandshakeState(
            title = stringRes(R.string.ledger_handshake_title),
            message = stringRes(R.string.ledger_handshake_message),
            isConnecting = isConnecting,
            primaryButton = createPrimaryButton(internal, sheets),
            errorSheet = if (internal.isSheetShown) createErrorSheet(internal, sheets) else null,
            permissionRequestNonce = internal.permissionRequestNonce,
            enableBluetoothRequestNonce = internal.enableBluetoothRequestNonce,
            onBack = ::onBack,
        )
    }

    /**
     * The Figma CTA slot: a disabled Connect while the Ledger is asked, and Retry, which pairs the
     * same device again, once an issue has stopped it. An issue only Settings or a missing radio can
     * fix words the button after the sheet's action, one that trying again cannot fix leaves the
     * disabled Connect, and an account already in the wallet offers Go to Account.
     */
    private fun createPrimaryButton(
        internal: LedgerHandshakeInternalState,
        sheets: LedgerIssueSheetBuilder,
    ): ButtonState {
        val alreadyAdded = internal.alreadyAdded
        val issue = internal.issue
        return when {
            alreadyAdded != null -> {
                ButtonState(
                    text = stringRes(R.string.ledger_error_alreadyAdded_primary),
                    onClick = { onGoToAccountClick(alreadyAdded) },
                )
            }

            issue != null && sheets.hasAction(issue) -> {
                ButtonState(
                    text = sheets.actionText(issue, tryAgainText = stringRes(R.string.ledger_handshake_retry)),
                    onClick = sheets.action(issue),
                )
            }

            else -> {
                ButtonState(
                    text = stringRes(R.string.ledger_scan_select_cta),
                    isEnabled = false,
                )
            }
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
            onTryAgain = ::startHandshake,
            onRequestPermissionsAgain = ::onRequestPermissionsAgainClick,
            onOpenSettings = ::onOpenSettingsClick,
            onEnableBluetooth = ::onEnableBluetoothClick,
            onClose = ::onBack,
            onDismiss = ::onSheetDismissed,
        )

    /**
     * Also restarts after a permissions issue: permissions revoked since the scan make the
     * handshake fail while the system dialog is still up, and granting them should not need
     * another tap.
     */
    fun onPermissionsGranted() {
        val internal = internalState.value
        if (internal.phase == LedgerHandshakePhase.PERMISSION || internal.issue?.kind == LedgerIssueKind.PERMISSIONS) {
            startHandshake()
        }
    }

    fun onPermissionsDenied(canRequestAgain: Boolean) {
        cancelHandshake()
        internalState.update { it.copy(canRequestPermissionsAgain = canRequestAgain) }
        showIssue(LedgerIssue.permissions)
    }

    /**
     * The system dialog turned Bluetooth on, so the handshake can be tried again.
     */
    fun onBluetoothEnabled() = startHandshake()

    /**
     * The user declined the system dialog. The inline issue stayed on the page throughout, so only
     * the sheet has to come back.
     */
    fun onBluetoothEnableDeclined() {
        internalState.update { it.copy(isSheetShown = it.issue != null) }
    }

    /**
     * Each attempt opens a fresh transport to the device the scan screen bonded with. A running
     * attempt is left alone before the device is looked up, so a restart can never unwind the flow
     * under it.
     */
    private fun startHandshake() {
        if (handshakeJob?.isActive == true) return
        val device = ledgerSelectedDeviceRepository.get()
        if (device == null) {
            navigationRouter.backToRoot()
            return
        }
        internalState.update {
            it.copy(
                phase = LedgerHandshakePhase.CONNECTING,
                issue = null,
                alreadyAdded = null,
                isSheetShown = false,
            )
        }
        handshakeJob = viewModelScope.launch { handshake(device) }
    }

    /**
     * On success this screen is replaced, so backing out of the birthday screens lands on the
     * open-the-app screen rather than on a finished handshake. An account that was only missing its
     * binding, or is being paired again, needs no birthday: it is connected again at once. The wrong
     * Ledger shows its issue, and Try again pairs the same device again, which may have switched
     * seeds since.
     *
     * The phone bonded with the device on the scan screen, so a lost connection here, including the
     * handshake running out of time, reads as a device that disconnected during setup.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun handshake(device: LedgerBluetoothDevice) {
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

                is PairLedgerDeviceResult.WrongLedger -> {
                    Twig.warn { "Ledger enrollment: the Ledger is not the account's" }
                    showIssue(LedgerIssue.wrongDevice)
                }
            }
        } catch (e: CancellationException) {
            throw e
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
     * granted restarts the handshake through the screen's ON_RESUME hook.
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
     * A handshake still waiting on the device is dropped at once rather than when the screen is
     * disposed, so the device is not left holding an export nobody will read.
     */
    private fun onBack() {
        cancelHandshake()
        navigationRouter.back()
    }

    private fun cancelHandshake() {
        handshakeJob?.cancel()
        handshakeJob = null
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
    val phase: LedgerHandshakePhase = LedgerHandshakePhase.CONNECTING,
    val issue: LedgerIssue? = null,
    val alreadyAdded: WalletAccount? = null,
    val isSheetShown: Boolean = false,
    val canRequestPermissionsAgain: Boolean = false,
    val permissionRequestNonce: Int = 0,
    val enableBluetoothRequestNonce: Int = 0,
)

private enum class LedgerHandshakePhase {
    PERMISSION,
    CONNECTING,
    IDLE,
}
