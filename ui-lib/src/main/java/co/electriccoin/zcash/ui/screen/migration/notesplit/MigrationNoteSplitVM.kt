package co.electriccoin.zcash.ui.screen.migration.notesplit

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.NoteSplitProposal
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferResult
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MigrationNoteSplitVM(
    private val sdk: OrchardMigrationSdk,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    // Holds the note-split proposal (output note amounts + fee) once computed. Null result
    // means no split is needed and the user has already been routed away.
    private val checkLce = mutableLce<NoteSplitProposal?>()

    // Holds the resulting transaction id once the split has been submitted.
    private val splitLce = mutableLce<String?>()

    private val phase = MutableStateFlow(NoteSplitPhase.EXPLAINER)
    private val isKeystoneAccount = getSelectedWalletAccount.observe().map { it is KeystoneAccount }

    init {
        checkLce.execute {
            val needed = sdk.isNoteSplitNeeded()
            if (!needed) {
                navigationRouter.replace(MigrationBatteryArgs)
                null
            } else {
                sdk.prepareNoteSplit()
            }
        }
    }

    val state: StateFlow<LceState<MigrationNoteSplitState>> =
        combine(phase, checkLce.state, splitLce.state, isKeystoneAccount) { p, checkState, splitState, isKeystone ->
            checkState.success?.let { proposal -> createState(p, proposal, splitState.success, isKeystone) }
        }.withLce(groupLce(checkLce, splitLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        currentPhase: NoteSplitPhase,
        proposal: NoteSplitProposal,
        transactionId: String?,
        isKeystone: Boolean,
    ) = MigrationNoteSplitState(
        phase = currentPhase,
        isKeystone = isKeystone,
        splitAmount = stringRes(Zatoshi(proposal.outputNotes.sum())),
        fee = stringRes(Zatoshi(proposal.fee)),
        transactionId = transactionId?.let { stringRes(truncateId(it)) },
        onCopyTransactionId = { transactionId?.let(copyToClipboard::invoke) },
        onContinue = { onContinue(currentPhase, proposal) },
        onBack = ::onBack,
    )

    private fun onContinue(currentPhase: NoteSplitPhase, proposal: NoteSplitProposal) = when (currentPhase) {
        NoteSplitPhase.EXPLAINER -> startSplit(proposal)
        NoteSplitPhase.IN_PROGRESS -> Unit
        NoteSplitPhase.COMPLETE -> navigationRouter.forward(MigrationBatteryArgs)
    }

    private fun startSplit(proposal: NoteSplitProposal) = splitLce.execute {
        phase.value = NoteSplitPhase.IN_PROGRESS
        val result = sdk.submitNoteSplit(proposal)
        phase.value = NoteSplitPhase.COMPLETE
        (result as? TransferResult.Success)?.txId
    }

    private fun onBack() = checkLce.guardLoading { navigationRouter.back() }

    private fun truncateId(id: String): String =
        if (id.length <= 12) id else "${id.take(5)}…${id.takeLast(5)}"
}
