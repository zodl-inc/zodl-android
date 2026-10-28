package co.electriccoin.zcash.ui.screen.viewingkey.name

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.ViewOnlyPendingImportMissingException
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ImportViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsAccountNameTakenUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Names the pending view-only account, the last step of the import: the key and the birthday the
 * earlier screens collected are held with the pending import, and Continue imports the account with
 * the name and the colour picked here. The name is capped at [AccountNameLimit.MAX_LENGTH] code
 * points as it is typed. Without a pending key, after process death, there is nothing to name and
 * the flow returns to the root. The avatar's pencil badge opens the colour picker, the avatar follows
 * the picks live and keeps the last one however the picker closes. A name another account already
 * shows, by [IsAccountNameTakenUseCase], disables Continue and says so under the field.
 *
 * While the import runs, Continue shows its progress and the name, the colour and back are ignored;
 * a failed import shows the error, from which it can be tried again.
 */
internal class NameVKVM(
    private val pendingImportRepository: ViewOnlyPendingImportRepository,
    private val navigationRouter: NavigationRouter,
    private val navigateToColorPicker: NavigateToColorPickerUseCase,
    private val isAccountNameTaken: IsAccountNameTakenUseCase,
    private val importViewOnlyAccount: ImportViewOnlyAccountUseCase,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val name = MutableStateFlow(pendingImportRepository.get()?.name.orEmpty())

    private val color = MutableStateFlow(pendingImportRepository.get()?.color)

    private val importLce = mutableLce<Unit>()

    private var colorPickerJob: Job? = null

    private var continueJob: Job? = null

    /**
     * The typed name with whether another account already shows it, which the new name must not
     * repeat; until the accounts load, the name shows without the error.
     */
    val state: StateFlow<LceState<NameVKState>> =
        combine(isAccountNameTaken.observe(name), color, importLce.state) { check, color, lce ->
            createState(check.name, color, isDuplicate = check.isTaken, isImporting = lce.loading)
        }.withLce(importLce, errorStateMapper::mapToState)
            .stateIn(
                viewModel = this,
                initialValue =
                    LceState(
                        content = createState(name.value, color.value, isDuplicate = false, isImporting = false)
                    )
            )

    init {
        if (pendingImportRepository.get() == null) {
            navigationRouter.backToRoot()
        }
    }

    private fun createState(
        name: String,
        color: Color?,
        isDuplicate: Boolean,
        isImporting: Boolean,
    ) = NameVKState.create(
        name = name,
        color = color,
        isDuplicate = isDuplicate,
        isImporting = isImporting,
        onAvatarEditClick = ::onAvatarEditClick,
        onValueChange = ::onValueChange,
        onContinueClick = ::onContinueClick,
        onBack = ::onBack,
    )

    private fun onAvatarEditClick() =
        importLce.guardLoading {
            if (colorPickerJob?.isActive == true) return@guardLoading
            colorPickerJob =
                viewModelScope.launch {
                    navigateToColorPicker(color.value).collect { picked -> color.update { picked } }
                }
        }

    private fun onValueChange(value: String) =
        importLce.guardLoading {
            name.update { AccountNameLimit.cap(value) }
        }

    /**
     * A second tap while the import runs is ignored: restarting would cancel the running import
     * inside the SDK call, after the account exists but before it is selected.
     */
    private fun onContinueClick() =
        importLce.guardLoading {
            if (continueJob?.isActive == true) return@guardLoading
            val normalized = AccountNameLimit.normalize(name.value)
            if (normalized.isEmpty()) return@guardLoading
            continueJob =
                viewModelScope.launch {
                    if (isAccountNameTaken(normalized)) return@launch
                    val pending = pendingImportRepository.get()
                    if (pending?.birthday == null) {
                        navigationRouter.backToRoot()
                        return@launch
                    }
                    pendingImportRepository.set(pending.copy(name = normalized, color = color.value))
                    importLce.execute {
                        try {
                            importViewOnlyAccount()
                        } catch (_: ViewOnlyPendingImportMissingException) {
                            navigationRouter.backToRoot()
                        }
                    }
                }
        }

    /**
     * Returns to the birthday screen underneath, which still needs the pending key and birthday, so
     * both are kept; the name and the colour are dropped, since they are only stored on Continue.
     * Ignored while the import runs.
     */
    private fun onBack() =
        importLce.guardLoading {
            colorPickerJob?.cancel()
            continueJob?.cancel()
            navigationRouter.back()
        }
}
