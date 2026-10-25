package co.electriccoin.zcash.ui.screen.editaccount

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.IsAccountNameTakenUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveAccountIconUseCase
import co.electriccoin.zcash.ui.common.usecase.RenameViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits the account [EditAccountArgs.accountKeyId] names, which must be the Zodl account or a
 * view-only one; any other id, a hardware wallet's included, goes straight back.
 *
 * The avatar shows the account's stored colour; its pencil badge opens the colour picker on the
 * colour shown, the avatar follows the picks live and keeps the last one however the picker closes,
 * and a colour other than the stored one enables Save. A view-only account's name starts in the
 * field and is capped at [AccountNameLimit.MAX_LENGTH] code points as it is typed; a new, non-blank
 * name enables Save as well, and the account can be removed. A name another account already shows,
 * by [IsAccountNameTakenUseCase], disables Save and says so under the field; the account's own name is
 * never a duplicate. The Zodl account's name cannot be changed, so its field is disabled and only the
 * colour saves. Save stores whichever changed and returns; a failure shows the error screen and keeps
 * what was typed and picked.
 */
class EditAccountVM(
    private val args: EditAccountArgs,
    getWalletAccounts: GetWalletAccountsUseCase,
    private val renameViewOnlyAccount: RenameViewOnlyAccountUseCase,
    private val observeAccountIcon: ObserveAccountIconUseCase,
    private val metadataRepository: MetadataRepository,
    private val navigateToColorPicker: NavigateToColorPickerUseCase,
    private val navigateToError: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
    private val viewOnlyNavigator: ViewOnlyNavigator,
    private val viewOnlyContent: ViewOnlyContent,
    private val isAccountNameTaken: IsAccountNameTakenUseCase,
) : ViewModel() {
    /** The account the route names while it is the Zodl account or a view-only one, otherwise null. */
    private val account: Flow<WalletAccount?> =
        getWalletAccounts.require().map { accounts ->
            accounts
                .firstOrNull { it.sdkAccount.accountUuid.toStorageKeyId() == args.accountKeyId }
                ?.takeIf { it is ZodlAccount || it is ViewOnlyAccount }
        }

    /** Null until the account's current name has been put into the field. */
    private val name = MutableStateFlow<String?>(null)

    private val isSaving = MutableStateFlow(false)

    /** The colour picked on this screen, null while it is the stored colour or none has been picked. */
    private val pickedColor = MutableStateFlow<Color?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val storedColor: StateFlow<Color?> =
        account
            .filterNotNull()
            .map { it.sdkAccount.accountUuid }
            .distinctUntilChanged()
            .flatMapLatest(metadataRepository::observeColor)
            .stateIn(viewModel = this, initialValue = null)

    private var saveJob: Job? = null

    private var colorPickerJob: Job? = null

    internal val state: StateFlow<EditAccountState?> =
        combine(
            account
                .filterNotNull()
                .distinctUntilChanged { old, new ->
                    old.sdkAccount.accountUuid == new.sdkAccount.accountUuid && old.currentName() == new.currentName()
                },
            isAccountNameTaken.observe(name.filterNotNull(), excludingAccountKeyId = args.accountKeyId),
            isSaving,
            storedColor,
            pickedColor,
        ) { account, nameCheck, isSaving, stored, picked ->
            createState(
                account = account,
                name = nameCheck.name,
                isDuplicate = nameCheck.isTaken,
                isSaving = isSaving,
                color = picked ?: stored,
                isColorChanged = newColor(picked, stored) != null
            )
        }.stateIn(this)

    init {
        viewModelScope.launch {
            val current = account.first()
            if (current == null) {
                navigationRouter.back()
            } else {
                name.update { it ?: AccountNameLimit.cap(current.currentName()) }
            }
        }
    }

    private fun createState(
        account: WalletAccount,
        name: String,
        isDuplicate: Boolean,
        isSaving: Boolean,
        color: Color?,
        isColorChanged: Boolean,
    ): EditAccountState {
        val viewOnly = account as? ViewOnlyAccount
        val isNameDuplicate = viewOnly != null && isDuplicate
        return EditAccountState.create(
            image = observeAccountIcon.iconOf(account, color),
            name = if (viewOnly == null) account.name else stringRes(name),
            nameLength = viewOnly?.let { AccountNameLimit.lengthOf(name) },
            isDuplicate = isNameDuplicate,
            canSave = !isNameDuplicate && (isColorChanged || (viewOnly != null && canRename(name, viewOnly))),
            isSaving = isSaving,
            remove =
                viewOnly?.let {
                    EditAccountRemoveState.create(
                        icon = viewOnlyContent.removeAccountInfoIcon,
                        info = viewOnlyContent.removeAccountInfo,
                        buttonText = viewOnlyContent.removeAccountButton,
                        isSaving = isSaving,
                        onClick = ::onRemoveClick,
                    )
                },
            onAvatarEditClick = ::onAvatarEditClick,
            onValueChange = ::onValueChange,
            onSaveClick = ::onSaveClick,
            onBack = ::onBack,
        )
    }

    private fun onAvatarEditClick() {
        if (colorPickerJob?.isActive == true) return
        colorPickerJob =
            viewModelScope.launch {
                navigateToColorPicker(pickedColor.value ?: storedColor.value).collect { color ->
                    pickedColor.update { color.takeUnless { it.toArgb() == storedColor.value?.toArgb() } }
                }
            }
    }

    private fun onValueChange(value: String) = name.update { AccountNameLimit.cap(value) }

    @Suppress("TooGenericExceptionCaught")
    private fun onSaveClick() {
        if (saveJob?.isActive == true) return
        val typed = name.value ?: return
        val picked = pickedColor.value
        saveJob =
            viewModelScope.launch {
                val current = account.first()
                if (current == null) {
                    navigationRouter.back()
                    return@launch
                }
                val isTaken =
                    current is ViewOnlyAccount && isAccountNameTaken(typed, excludingAccountKeyId = args.accountKeyId)
                if (isTaken) return@launch
                val renamed = (current as? ViewOnlyAccount)?.takeIf { canRename(typed, it) }
                val color = newColor(picked, storedColor.value)
                if (renamed == null && color == null) return@launch
                isSaving.update { true }
                try {
                    renamed?.let { renameViewOnlyAccount(it, typed) }
                    val isColorSaved = color == null || metadataRepository.setAccountColor(current, color)
                    ensureActive()
                    if (isColorSaved) {
                        navigationRouter.back()
                    } else {
                        navigateToError(ErrorArgs.General(AccountColorNotSavedException()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    navigateToError(ErrorArgs.General(e))
                } finally {
                    isSaving.update { false }
                }
            }
    }

    private fun onRemoveClick() {
        if (saveJob?.isActive == true) return
        viewOnlyNavigator.forwardToRemoveAccount(args.accountKeyId)
    }

    private fun onBack() {
        saveJob?.cancel()
        navigationRouter.back()
    }

    /** Whether [name] is a new, non-blank name for [account]. */
    private fun canRename(
        name: String,
        account: ViewOnlyAccount
    ): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed != account.currentName()
    }

    /**
     * The name the field edits: a view-only account's stored or imported name, empty when it shows
     * the default view-only name. The Zodl account has no name of its own.
     */
    private fun WalletAccount.currentName(): String = (this as? ViewOnlyAccount)?.customName.orEmpty()

    /** [picked] when it differs from the [stored] colour, otherwise null. */
    private fun newColor(
        picked: Color?,
        stored: Color?
    ): Color? = picked?.takeIf { it.toArgb() != stored?.toArgb() }
}

/** The picked colour could not be written to the account's metadata. */
internal class AccountColorNotSavedException : Exception("The account colour was not saved")
