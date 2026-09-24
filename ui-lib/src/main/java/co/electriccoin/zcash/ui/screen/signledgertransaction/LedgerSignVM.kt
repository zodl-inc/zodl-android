package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LedgerIssue
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.usecase.CancelLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerSigningStateUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.RetryLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectLedgerSigningDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.StartLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitLedgerProposalUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceItemState
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
 * Drives the Ledger sign sheet. The session itself lives in the repository; this only renders its
 * phase and forwards the user's choices. Device identifiers are used as selection keys only.
 *
 * An empty repository at start means the process was recreated under the sheet: there is nothing
 * left to sign, so the wallet root is shown instead.
 */
class LedgerSignVM(
    private val observeLedgerSigningState: ObserveLedgerSigningStateUseCase,
    private val observeProposal: ObserveProposalUseCase,
    private val startLedgerSigning: StartLedgerSigningUseCase,
    private val selectLedgerSigningDevice: SelectLedgerSigningDeviceUseCase,
    private val retryLedgerSigning: RetryLedgerSigningUseCase,
    private val cancelLedgerSigning: CancelLedgerSigningUseCase,
    private val submitLedgerProposal: SubmitLedgerProposalUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val selectedIdentifier = MutableStateFlow<String?>(null)

    val state: StateFlow<LedgerSignSheetState?> =
        combine(observeLedgerSigningState(), selectedIdentifier) { signingState, selected ->
            createState(signingState, selected)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            if (observeProposal.observeNullable().first() == null) {
                navigationRouter.backToRoot()
            } else {
                startLedgerSigning()
            }
        }
        viewModelScope.launch {
            observeLedgerSigningState().filterIsInstance<LedgerSigningState.Signed>().first()
            submitLedgerProposal()
        }
    }

    private fun createState(
        signingState: LedgerSigningState?,
        selected: String?,
    ): LedgerSignSheetState {
        val selecting = signingState as? LedgerSigningState.Selecting
        val effectiveSelection =
            (selected ?: selecting?.selectedIdentifier)
                ?.takeIf { identifier -> selecting?.devices?.any { it.identifier == identifier } == true }
        val issue = (signingState as? LedgerSigningState.Failed)?.issue
        return LedgerSignSheetState(
            phase = signingState.toPhase(),
            devices =
                selecting?.devices.orEmpty().map { device ->
                    LedgerDeviceItemState(
                        name = stringRes(device.name),
                        isSelected = device.identifier == effectiveSelection,
                        isEnabled = true,
                        onClick = { selectedIdentifier.update { device.identifier } },
                    )
                },
            issueTitle = issue?.title,
            issueMessage = issue?.message,
            primaryButton =
                when {
                    selecting != null -> {
                        ButtonState(
                            text = stringRes(R.string.ledger_scan_select_cta),
                            isEnabled = effectiveSelection != null,
                            onClick = { effectiveSelection?.let(::onConnectClick) },
                        )
                    }

                    issue != null && issue.hasRetry() -> {
                        ButtonState(
                            text = stringRes(R.string.ledger_error_tryAgain),
                            onClick = ::onTryAgainClick,
                        )
                    }

                    else -> {
                        null
                    }
                },
            cancelButton =
                ButtonState(
                    text = stringRes(R.string.ledger_sign_cancel),
                    style = ButtonStyle.DESTRUCTIVE2,
                    onClick = ::onCancelClick,
                ),
            onBack = {},
        )
    }

    private fun onConnectClick(identifier: String) {
        selectedIdentifier.update { null }
        selectLedgerSigningDevice(identifier)
    }

    private fun onTryAgainClick() {
        selectedIdentifier.update { null }
        retryLedgerSigning()
    }

    private fun onCancelClick() = cancelLedgerSigning()
}

private fun LedgerIssue.hasRetry() =
    kind != LedgerIssueKind.BLUETOOTH_UNAVAILABLE &&
        kind != LedgerIssueKind.NOT_SIGNABLE &&
        kind != LedgerIssueKind.UNBOUND

private fun LedgerSigningState?.toPhase(): StringResource =
    when (this) {
        null -> stringRes("Starting…")
        LedgerSigningState.Scanning -> stringRes("Looking for your Ledger…")
        is LedgerSigningState.Selecting -> stringRes("Select your device")
        LedgerSigningState.Connecting -> stringRes("Connecting to your Ledger…")
        LedgerSigningState.Preparing -> stringRes("Preparing the transaction…")
        is LedgerSigningState.Streaming -> stringRes("Sending to your Ledger ($sent/$total)")
        LedgerSigningState.AwaitingReview -> stringRes("Confirm the transaction on your Ledger")
        LedgerSigningState.Signing -> stringRes("Signing…")
        LedgerSigningState.Signed -> stringRes("Signed")
        is LedgerSigningState.Failed -> stringRes("Failed")
    }
