package co.electriccoin.zcash.ui.screen.migration.sending

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class MigrationSendingVM(
    private val sdk: OrchardMigrationSdk,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val sendLce = mutableLce<Unit>()

    val state: StateFlow<LceState<Unit>> =
        flowOf(Unit)
            .withLce(sendLce, errorStateMapper::mapToState)
            .stateIn(this)

    fun send() = sendLce.execute {
        sdk.executeNextPendingTransfer(NetworkPrivacyOptions(useTor = false))
        navigationRouter.forward(MigrationSuccessArgs)
    }
}
