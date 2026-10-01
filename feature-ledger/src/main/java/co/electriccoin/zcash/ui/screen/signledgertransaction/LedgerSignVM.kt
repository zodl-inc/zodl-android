package co.electriccoin.zcash.ui.screen.signledgertransaction

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.usecase.CancelLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToLedgerRepairUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerSigningStateUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.RetryLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectLedgerSigningDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.StartLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitLedgerProposalUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceItemState
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRowRole
import co.electriccoin.zcash.ui.util.SettingsUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Ledger sign sheet. The session itself lives in the repository; this renders its phase,
 * forwards the user's choices and owns only what the screen's Bluetooth gate reports. Device
 * identifiers are used as selection keys only.
 *
 * Signing starts once the gate reports the permissions granted, which it also does on every resume,
 * so coming back from Settings with the permission granted starts the session without a tap.
 *
 * Cancel is disabled once the device has signed, as submission is then under way. A sheet that goes
 * away any other way stops the session it leaves behind.
 *
 * An empty repository at start means the process was recreated under the sheet: there is nothing
 * left to sign, so the wallet root is shown instead.
 */
@Suppress("TooManyFunctions")
class LedgerSignVM(
    application: Application,
    private val observeLedgerSigningState: ObserveLedgerSigningStateUseCase,
    private val observeProposal: ObserveProposalUseCase,
    private val startLedgerSigning: StartLedgerSigningUseCase,
    private val selectLedgerSigningDevice: SelectLedgerSigningDeviceUseCase,
    private val retryLedgerSigning: RetryLedgerSigningUseCase,
    private val cancelLedgerSigning: CancelLedgerSigningUseCase,
    private val submitLedgerProposal: SubmitLedgerProposalUseCase,
    private val navigateToLedgerRepair: NavigateToLedgerRepairUseCase,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val permissionDenial = MutableStateFlow<PermissionDenial?>(null)

    private val permissionRequestNonce = MutableStateFlow(0)

    private val enableBluetoothRequestNonce = MutableStateFlow(0)

    private val hasProposal = CompletableDeferred<Boolean>()

    private var repairJob: Job? = null

    val state: StateFlow<LedgerSignSheetState?> =
        combine(
            observeLedgerSigningState(),
            permissionDenial,
            permissionRequestNonce,
            enableBluetoothRequestNonce,
        ) { signingState, denial, permissionNonce, enableBluetoothNonce ->
            LedgerSignSheetState(
                content = createContent(signingState, denial),
                cancelButton =
                    ButtonState(
                        text = stringRes(R.string.ledger_sign_cancel),
                        isEnabled = signingState != LedgerSigningState.Signed,
                        onClick = ::onCancelClick,
                    ),
                permissionRequestNonce = permissionNonce,
                enableBluetoothRequestNonce = enableBluetoothNonce,
                onBack = {},
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            val isPresent = observeProposal.observeNullable().first() != null
            hasProposal.complete(isPresent)
            if (!isPresent) {
                navigationRouter.backToRoot()
            }
        }
        viewModelScope.launch {
            observeLedgerSigningState().filterIsInstance<LedgerSigningState.Signed>().first()
            submitLedgerProposal()
        }
    }

    /**
     * Starts the session when none has run yet, or restarts one that failed for want of the
     * permissions; a session already under way is left alone.
     */
    fun onPermissionsGranted() {
        permissionDenial.update { null }
        viewModelScope.launch {
            if (!hasProposal.await()) return@launch
            when (val signingState = observeLedgerSigningState().value) {
                null -> {
                    startLedgerSigning()
                }

                is LedgerSigningState.Failed -> {
                    if (signingState.issue.kind == LedgerIssueKind.PERMISSIONS) retryLedgerSigning()
                }

                else -> {
                    Unit
                }
            }
        }
    }

    fun onPermissionsDenied(canRequestAgain: Boolean) {
        permissionDenial.update { PermissionDenial(canRequestAgain) }
    }

    /**
     * The system dialog turned Bluetooth on; the failed session can now look for the device again.
     */
    fun onBluetoothEnabled() = retryLedgerSigning()

    /**
     * The user declined the system dialog; the Bluetooth issue is still on the sheet, so there is
     * nothing to restore.
     */
    fun onBluetoothEnableDeclined() = Unit

    private fun createContent(
        signingState: LedgerSigningState?,
        denial: PermissionDenial?,
    ): LedgerSignContent {
        if (denial != null) {
            return issueContent(LedgerIssue.permissions, denial.canRequestAgain)
        }
        val body = body(signingState)
        return when (signingState) {
            null,
            LedgerSigningState.Scanning -> {
                progress(body, R.string.ledger_sign_scanning)
            }

            is LedgerSigningState.Selecting -> {
                devicesContent(body, signingState)
            }

            LedgerSigningState.Connecting -> {
                progress(body, R.string.ledger_sign_connecting)
            }

            LedgerSigningState.OpeningZcashApp -> {
                progress(body, R.string.ledger_sign_openingApp)
            }

            LedgerSigningState.Preparing -> {
                progress(body, R.string.ledger_sign_preparing)
            }

            is LedgerSigningState.Streaming -> {
                progress(body, R.string.ledger_sign_streaming)
            }

            LedgerSigningState.AwaitingReview -> {
                progress(body, R.string.ledger_sign_awaitingReview)
            }

            LedgerSigningState.Signing,
            LedgerSigningState.Signed -> {
                progress(body, R.string.ledger_sign_signing)
            }

            is LedgerSigningState.Failed -> {
                issueContent(signingState.issue, canRequestPermissionsAgain = false)
            }
        }
    }

    /**
     * Once the device shows the transaction, the line under the title asks for its confirmation;
     * before that, it asks to keep the device ready.
     */
    private fun body(signingState: LedgerSigningState?) =
        when (signingState) {
            LedgerSigningState.AwaitingReview,
            LedgerSigningState.Signing,
            LedgerSigningState.Signed -> {
                stringRes(R.string.ledger_sign_body_review)
            }

            else -> {
                stringRes(R.string.ledger_sign_body_beforeReview)
            }
        }

    private fun progress(
        body: StringResource,
        @StringRes status: Int,
    ) = LedgerSignContent.Progress(
        body = body,
        status = stringRes(status),
    )

    /**
     * A tap on a device connects to it; the repository takes only the first one while the picker
     * is open.
     */
    private fun devicesContent(
        body: StringResource,
        selecting: LedgerSigningState.Selecting,
    ) = LedgerSignContent.Devices(
        body = body,
        title = stringRes(R.string.ledger_sign_select_title),
        devices =
            selecting.devices.map { device ->
                LedgerDeviceItemState(
                    name = stringRes(device.name),
                    isSelected = false,
                    isEnabled = true,
                    role = LedgerDeviceRowRole.BUTTON,
                    onClick = { selectLedgerSigningDevice(device.identifier) },
                )
            },
    )

    private fun issueContent(
        issue: LedgerIssue,
        canRequestPermissionsAgain: Boolean,
    ) = LedgerSignContent.Issue(
        icon = issue.icon,
        isBadge = issue.isBadge,
        title = issue.title,
        message = issue.message,
        primary = issuePrimary(issue, canRequestPermissionsAgain),
    )

    /**
     * A permission the system can still ask for is requested again in-app; one denied for good
     * only Settings can grant. Bluetooth that is off is turned on through the system dialog. An
     * account without a Ledger pairing is paired again through the connect flow. Kinds nothing in
     * the app can fix, and every issue that trying again cannot fix, leave Cancel Transaction as
     * the only way on.
     */
    private fun issuePrimary(
        issue: LedgerIssue,
        canRequestPermissionsAgain: Boolean,
    ): ButtonState? =
        when (issue.kind) {
            LedgerIssueKind.PERMISSIONS -> {
                if (canRequestPermissionsAgain) {
                    tryAgain(::onRequestPermissionsAgainClick)
                } else {
                    ButtonState(
                        text = stringRes(R.string.ledger_error_permissions_cta),
                        onClick = ::onOpenSettingsClick,
                    )
                }
            }

            LedgerIssueKind.BLUETOOTH_OFF -> {
                tryAgain(::onEnableBluetoothClick)
            }

            LedgerIssueKind.UNBOUND -> {
                ButtonState(
                    text = stringRes(R.string.ledger_sign_error_unbound_cta),
                    onClick = ::onPairLedgerClick,
                )
            }

            LedgerIssueKind.BLUETOOTH_UNAVAILABLE,
            LedgerIssueKind.NOT_SIGNABLE -> {
                null
            }

            else -> {
                if (issue.retry == LedgerIssueRetry.NONE) null else tryAgain(::onTryAgainClick)
            }
        }

    private fun tryAgain(onClick: () -> Unit) =
        ButtonState(
            text = stringRes(R.string.ledger_error_tryAgain),
            onClick = onClick,
        )

    private fun onTryAgainClick() = retryLedgerSigning()

    /**
     * The denial stays on the sheet while the system asks again; the gate reports the answer.
     */
    private fun onRequestPermissionsAgainClick() {
        permissionRequestNonce.update { it + 1 }
    }

    private fun onEnableBluetoothClick() {
        enableBluetoothRequestNonce.update { it + 1 }
    }

    /**
     * The gate's resume check reports the permissions granted on the way back, which starts the
     * session.
     */
    private fun onOpenSettingsClick() {
        getApplication<Application>().startActivity(
            SettingsUtil.newSettingsIntent(getApplication<Application>().packageName)
        )
    }

    private fun onCancelClick() = cancelLedgerSigning()

    override fun onCleared() {
        cancelLedgerSigning.stopSession()
        super.onCleared()
    }

    /**
     * Ends the session the way Cancel does, then opens the Ledger connect flow to pair the account
     * again, which stores the pairing with it. A tap while the previous one is still reading the
     * account is ignored, so the flow opens once.
     */
    private fun onPairLedgerClick() {
        if (repairJob?.isActive == true) return
        repairJob = viewModelScope.launch { navigateToLedgerRepair() }
    }
}

private data class PermissionDenial(
    val canRequestAgain: Boolean,
)
