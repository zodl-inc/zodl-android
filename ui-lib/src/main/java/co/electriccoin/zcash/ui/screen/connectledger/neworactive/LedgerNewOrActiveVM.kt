package co.electriccoin.zcash.ui.screen.connectledger.neworactive

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.usecase.CreateLedgerAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.date.LedgerDateArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class LedgerNewOrActiveVM(
    private val createLedgerAccount: CreateLedgerAccountUseCase,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<LedgerNewOrActiveState>> =
        createAccountLce.state
            .map { lce ->
                LedgerNewOrActiveState(
                    subtitle = stringRes(R.string.ledger_deviceQuestion),
                    message = stringRes(R.string.ledger_deviceDesc),
                    newDevice =
                        ButtonState(
                            text = stringRes(R.string.ledger_connectNew),
                            isLoading = lce.loading,
                            onClick = ::onNewDeviceClick,
                            hapticFeedbackType = HapticFeedbackType.Confirm,
                        ),
                    activeDevice =
                        ButtonState(
                            text = stringRes(R.string.ledger_connectActive),
                            isEnabled = !lce.loading,
                            onClick = ::onActiveDeviceClick,
                        ),
                    onBack = ::onBack,
                )
            }.withLce(createAccountLce, errorStateMapper::mapToState)
            .stateIn(this)

    init {
        if (ledgerPairingRepository.get() == null) {
            navigationRouter.backToRoot()
        }
    }

    private fun onNewDeviceClick() =
        createAccountLce.execute {
            createLedgerAccount(birthday = null)
        }

    private fun onActiveDeviceClick() =
        createAccountLce.guardLoading {
            navigationRouter.forward(LedgerDateArgs)
        }

    private fun onBack() =
        createAccountLce.guardLoading {
            navigationRouter.back()
        }
}
