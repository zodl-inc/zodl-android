package co.electriccoin.zcash.ui.screen.viewingkey.remove

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.usecase.GetViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RemoveViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.screen.accountlist.AccountListArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Confirms removing the view-only account [RemoveVKAccountArgs.accountKeyId] names. Remove
 * Account authenticates the user and removes the account once, however often it is tapped; the
 * sheet stays up and ignores back until the removal ends. Success hides the sheet and only then
 * returns to the account list, past the edit screen of an account that no longer exists. A
 * dismissed or failed authentication leaves the sheet as it was; any other failure shows the error
 * screen over it. An id that names no view-only account goes straight back. The flow is only
 * reached from the account list, which the landing's `backTo(AccountListArgs::class)` requires on
 * the back stack; without it that call does nothing.
 */
internal class RemoveVKAccountVM(
    args: RemoveVKAccountArgs,
    private val getViewOnlyAccount: GetViewOnlyAccountUseCase,
    private val removeViewOnlyAccount: RemoveViewOnlyAccountUseCase,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val accountKeyId = args.accountKeyId

    private val isRemoving = MutableStateFlow(false)

    private val isHidden = MutableStateFlow(false)

    private var removeJob: Job? = null

    val state: StateFlow<RemoveVKAccountState?> =
        combine(isRemoving, isHidden) { isRemoving, isHidden ->
            if (isHidden) {
                null
            } else {
                RemoveVKAccountState.create(
                    isRemoving = isRemoving,
                    onRemoveClick = ::onRemoveClick,
                    onCancelClick = ::onBack,
                    onBack = ::onBack,
                )
            }
        }.stateIn(this)

    init {
        viewModelScope.launch {
            if (getViewOnlyAccount(accountKeyId) == null) navigationRouter.back()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun onRemoveClick() {
        if (removeJob?.isActive == true) return
        removeJob =
            viewModelScope.launch {
                isRemoving.update { true }
                try {
                    removeViewOnlyAccount(accountKeyId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: BiometricsCancelledException) {
                    isRemoving.update { false }
                    return@launch
                } catch (_: BiometricsFailureException) {
                    isRemoving.update { false }
                    return@launch
                } catch (e: Exception) {
                    isRemoving.update { false }
                    navigateToError(ErrorArgs.General(e))
                    return@launch
                }
                isHidden.update { true }
                delay(350.milliseconds)
                navigationRouter.backTo(AccountListArgs::class)
            }
    }

    /**
     * Ignored while the account is being removed or the sheet is already on its way out, so back
     * can neither interrupt a deletion halfway nor pop a second screen after the removal.
     */
    private fun onBack() {
        if (removeJob?.isActive == true) return
        navigationRouter.back()
    }
}
