package co.electriccoin.zcash.ui.screen.migration.torfailure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.repository.PendingMigrationTorFailureDecisionRepository
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.screen.home.HomeArgs
import co.electriccoin.zcash.ui.screen.migration.progress.MigrationProgressArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MigrationTorFailureVM(
    private val navigationRouter: NavigationRouter,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val pendingMigrationTorFailureDecisionRepository: PendingMigrationTorFailureDecisionRepository,
    private val isMigrationTorEnabledStorageProvider: IsMigrationTorEnabledStorageProvider,
) : ViewModel() {
    val state: StateFlow<MigrationTorFailureState?> =
        flowOf(
            MigrationTorFailureState(
                onContinueWithoutTor = ::onContinueWithoutTor,
                onTryAgain = ::onTryAgain,
                onBack = ::onBack,
            )
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null,
        )

    private fun onContinueWithoutTor() {
        // Persists the migration-scoped Tor setting itself (distinct from the app's global Tor
        // setting) so future migration broadcasts — background or foreground — also skip Tor,
        // not just this one retry.
        viewModelScope.launch { isMigrationTorEnabledStorageProvider.store(false) }
        viewModelScope.launch {
            val accountKeyId = getSelectedWalletAccount().sdkAccount.accountUuid.toStorageKeyId()
            pendingMigrationTorFailureDecisionRepository.set(accountKeyId, useTor = false)
        }
        navigationRouter.back()
    }

    // "Try again" is NOT an in-place Tor retry (that would loop forever on a persistent Tor
    // outage) — it just dismisses this sheet and sends the user to the standard missed/overdue
    // transfer resolution screen (Reschedule or Send Now), per spec §6.5.4/§9.1. No decision is
    // recorded on PendingMigrationTorFailureDecisionRepository: navigating straight to
    // MigrationProgressArgs (rather than relying on back() to reveal a live listener somewhere on
    // the stack) works identically regardless of whether this sheet was reached interactively or
    // via CheckMigrationRecoveryUseCase's background-failure routing.
    private fun onTryAgain() {
        navigationRouter.replaceAll(HomeArgs, MigrationProgressArgs)
    }

    private fun onBack() = navigationRouter.back()
}
