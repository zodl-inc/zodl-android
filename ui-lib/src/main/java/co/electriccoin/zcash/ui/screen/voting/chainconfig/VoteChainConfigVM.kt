package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class VoteChainConfigVM(
    private val votingChainConfigRepository: VotingChainConfigRepository,
    private val votingApiProvider: VotingApiProvider,
    private val navigationRouter: NavigationRouter,
    private val copyToClipboard: CopyToClipboardUseCase,
) : ViewModel() {
    private val editorDraft = MutableStateFlow<EditorDraft?>(null)
    private val errorSheet = MutableStateFlow<ZashiConfirmationState?>(null)
    private val isValidating = MutableStateFlow(false)

    val state =
        combine(
            votingChainConfigRepository.state,
            editorDraft,
            errorSheet,
            isValidating
        ) { chainConfig, editor, errorSheet, isValidating ->
            VoteChainConfigState(
                chains =
                    buildChainItems(
                        chainConfig = chainConfig,
                        isValidating = isValidating
                    ),
                editor = editor?.toState(isValidating),
                errorSheet = errorSheet,
                isValidating = isValidating,
                saveChangesButton =
                    ButtonState(
                        text = stringRes(R.string.vote_chain_config_save_changes),
                        style = ButtonStyle.PRIMARY,
                        isEnabled = !isValidating,
                        onClick = ::onBack
                    ),
                onBack = ::onBack,
                onAddCustom = ::onAddCustom
            )
        }.stateIn(this)

    private fun buildChainItems(
        chainConfig: VotingChainConfigState,
        isValidating: Boolean
    ): List<VoteChainConfigItemState> =
        buildList {
            add(
                VoteChainConfigItemState(
                    id = DEFAULT_CHAIN_ID,
                    radioButtonState =
                        RadioButtonState(
                            text = stringRes(R.string.vote_chain_config_default_name),
                            subtitle = stringRes(compactSource(StaticVotingConfig.BUNDLED_PINNED_SOURCE)),
                            isChecked = chainConfig.selected is VotingChainConfigSelection.Default,
                            onClick = ::onDefaultSelected
                        ),
                    fullUrl = stringRes(StaticVotingConfig.BUNDLED_PINNED_SOURCE),
                    isDefault = true,
                    editButton = null,
                    deleteButton = null
                )
            )
            chainConfig.customChains.forEach { chain ->
                add(
                    VoteChainConfigItemState(
                        id = chain.id,
                        radioButtonState =
                            RadioButtonState(
                                text = stringRes(chain.name),
                                subtitle = stringRes(compactSource(chain.pinnedSource)),
                                isChecked = chainConfig.selected == VotingChainConfigSelection.Custom(chain.id),
                                onClick = { onCustomSelected(chain.id) }
                            ),
                        fullUrl = stringRes(chain.pinnedSource),
                        isDefault = false,
                        editButton =
                            ButtonState(
                                text = stringRes(R.string.vote_chain_config_edit),
                                style = ButtonStyle.TERTIARY,
                                isEnabled = !isValidating,
                                onClick = { onEditCustom(chain) }
                            ),
                        deleteButton =
                            ButtonState(
                                text = stringRes(R.string.vote_chain_config_delete),
                                style = ButtonStyle.DESTRUCTIVE2,
                                isEnabled = !isValidating,
                                onClick = { onDeleteCustom(chain.id) }
                            )
                    )
                )
            }
        }

    private fun onBack() = navigationRouter.back()

    private fun onDefaultSelected() {
        if (isValidating.value) return
        viewModelScope.launch {
            votingChainConfigRepository.selectDefault()
        }
    }

    private fun onCustomSelected(id: String) {
        if (isValidating.value) return
        viewModelScope.launch {
            val chain =
                votingChainConfigRepository
                    .get()
                    .customChains
                    .firstOrNull { chain -> chain.id == id }
                    ?: return@launch
            val parsedSource = parsePinnedSourceOrShowError(chain.pinnedSource) ?: return@launch
            if (!validatePinnedSourceOrShowError(parsedSource)) {
                return@launch
            }
            if (parsedSource.isBundledDefaultUrl()) {
                votingChainConfigRepository.selectDefault()
            } else {
                votingChainConfigRepository.selectCustom(id)
            }
        }
    }

    private fun onAddCustom() {
        if (isValidating.value) return
        editorDraft.value =
            EditorDraft(
                id = null,
                name = "",
                pinnedSource = ""
            )
    }

    private fun onEditCustom(chain: VotingCustomChainConfig) {
        if (isValidating.value) return
        editorDraft.value =
            EditorDraft(
                id = chain.id,
                name = chain.name,
                pinnedSource = chain.pinnedSource
            )
    }

    private fun onDeleteCustom(id: String) {
        if (isValidating.value) return
        viewModelScope.launch {
            votingChainConfigRepository.deleteCustom(id)
            if (editorDraft.value?.id == id) {
                editorDraft.value = null
            }
        }
    }

    private fun onSaveEditor() {
        if (isValidating.value) return
        val draft = editorDraft.value ?: return
        val isInvalid =
            draft.name.trim().length > MAX_CUSTOM_CHAIN_NAME_LENGTH ||
                draft.pinnedSource.trim().isInvalidPinnedSource()
        if (isInvalid) {
            return
        }
        viewModelScope.launch {
            val name = draft.name.trim().ifEmpty { DEFAULT_CUSTOM_CHAIN_NAME }
            val pinnedSource = draft.pinnedSource.trim()
            if (pinnedSource.isEmpty()) {
                showError(stringRes(R.string.vote_chain_config_error_url_required))
                return@launch
            }

            val parsedSource = parsePinnedSourceOrShowError(pinnedSource) ?: return@launch
            val current = votingChainConfigRepository.get()
            val duplicateError =
                duplicateError(
                    parsedSource = parsedSource,
                    current = current,
                    editingId = draft.id
                )
            if (duplicateError != null) {
                showError(duplicateError)
                return@launch
            }

            val existing = draft.id?.let { id -> current.customChains.firstOrNull { it.id == id } }
            if (existing != null && existing.pinnedSource.trim() == pinnedSource) {
                votingChainConfigRepository.updateCustom(
                    id = existing.id,
                    name = name,
                    pinnedSource = pinnedSource
                )
                editorDraft.value = null
                return@launch
            }

            if (!validatePinnedSourceOrShowError(parsedSource)) {
                return@launch
            }

            if (parsedSource.isBundledDefaultUrl()) {
                votingChainConfigRepository.selectDefault()
                editorDraft.value = null
                return@launch
            }

            if (draft.id == null) {
                votingChainConfigRepository.addCustom(name = name, pinnedSource = pinnedSource)
            } else {
                votingChainConfigRepository.updateCustom(
                    id = draft.id,
                    name = name,
                    pinnedSource = pinnedSource
                )
            }
            editorDraft.value = null
        }
    }

    private fun onCancelEditor() {
        editorDraft.value = null
    }

    private fun onNameChanged(value: String) {
        editorDraft.value = editorDraft.value?.copy(name = value)
    }

    private fun onUrlChanged(value: String) {
        editorDraft.value = editorDraft.value?.copy(pinnedSource = value)
    }

    private fun onUrlCopyClick() {
        val pinnedSource =
            editorDraft.value
                ?.pinnedSource
                ?.trim()
                .orEmpty()
        if (pinnedSource.isNotEmpty()) {
            copyToClipboard(pinnedSource)
        }
    }

    private fun parsePinnedSourceOrShowError(raw: String): PinnedConfigSource? =
        runCatching { PinnedConfigSource.parse(raw) }
            .getOrElse { throwable ->
                showError(stringRes(throwable.message.orEmpty().ifBlank { raw }))
                null
            }

    private suspend fun validatePinnedSourceOrShowError(source: PinnedConfigSource): Boolean {
        isValidating.value = true
        return try {
            votingApiProvider.validateConfigSource(source)
            true
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
            showError(
                throwable.message
                    ?.takeIf(String::isNotBlank)
                    ?.let(::stringRes)
                    ?: stringRes(R.string.vote_chain_config_error_validation_failed)
            )
            false
        } finally {
            isValidating.value = false
        }
    }

    private fun duplicateError(
        parsedSource: PinnedConfigSource,
        current: VotingChainConfigState,
        editingId: String?
    ): StringResource? {
        if (parsedSource == PinnedConfigSource.parse(StaticVotingConfig.BUNDLED_PINNED_SOURCE)) {
            return stringRes(R.string.vote_chain_config_error_duplicate_default)
        }

        val hasDuplicateCustom =
            current.customChains
                .filterNot { chain -> chain.id == editingId }
                .any { chain ->
                    runCatching { PinnedConfigSource.parse(chain.pinnedSource) }.getOrNull() == parsedSource
                }
        return if (hasDuplicateCustom) {
            stringRes(R.string.vote_chain_config_error_duplicate_custom)
        } else {
            null
        }
    }

    private fun showError(message: StringResource) {
        errorSheet.value =
            ZashiConfirmationState.error(
                title = stringRes(R.string.vote_chain_config_error_title),
                message = message,
                primaryText = stringRes(R.string.vote_dismiss),
                secondaryText = null,
                primaryStyle = ButtonStyle.PRIMARY,
                onPrimary = ::dismissError,
                onBack = ::dismissError,
            )
    }

    private fun dismissError() {
        errorSheet.value = null
    }

    private fun EditorDraft.toState(isValidating: Boolean) =
        VoteChainConfigEditorState(
            sheetTitle =
                stringRes(
                    if (id == null) {
                        R.string.vote_chain_config_add_source_nav
                    } else {
                        R.string.vote_chain_config_edit_source_nav
                    }
                ),
            title =
                stringRes(
                    if (id == null) {
                        R.string.vote_chain_config_add_title
                    } else {
                        R.string.vote_chain_config_edit_title
                    }
                ),
            description =
                stringRes(
                    if (id == null) {
                        R.string.vote_chain_config_add_source_description
                    } else {
                        R.string.vote_chain_config_edit_source_description
                    }
                ),
            name =
                TextFieldState(
                    value = stringRes(name),
                    error =
                        stringRes(R.string.vote_chain_config_error_name_too_long)
                            .takeIf { name.trim().length > MAX_CUSTOM_CHAIN_NAME_LENGTH },
                    isEnabled = !isValidating,
                    onValueChange = ::onNameChanged
                ),
            url =
                TextFieldState(
                    value = stringRes(pinnedSource),
                    error =
                        stringRes(R.string.vote_chain_config_error_url_invalid)
                            .takeIf { pinnedSource.trim().isInvalidPinnedSource() },
                    isEnabled = !isValidating,
                    onValueChange = ::onUrlChanged
                ),
            showsUrlCopyButton = id != null,
            onUrlCopyClick = ::onUrlCopyClick,
            deleteButton =
                id?.let { customId ->
                    ButtonState(
                        text = stringRes(R.string.vote_chain_config_delete),
                        style = ButtonStyle.DESTRUCTIVE1,
                        isEnabled = !isValidating,
                        onClick = { onDeleteCustom(customId) }
                    )
                },
            saveButton =
                ButtonState(
                    text = stringRes(R.string.vote_chain_config_save_changes),
                    style = ButtonStyle.PRIMARY,
                    isEnabled = !isValidating && canSave(),
                    isLoading = isValidating,
                    onClick = ::onSaveEditor
                ),
            cancelButton =
                ButtonState(
                    text = stringRes(R.string.vote_chain_config_cancel),
                    style = ButtonStyle.TERTIARY,
                    isEnabled = !isValidating,
                    onClick = ::onCancelEditor
                )
        )

    private companion object {
        const val DEFAULT_CHAIN_ID = "default"
        const val DEFAULT_CUSTOM_CHAIN_NAME = "Custom source"
    }
}

private data class EditorDraft(
    val id: String?,
    val name: String,
    val pinnedSource: String,
)

private fun compactSource(raw: String): String =
    runCatching { PinnedConfigSource.parse(raw).url }
        .getOrDefault(raw)

private fun PinnedConfigSource.isBundledDefaultUrl(): Boolean =
    url == PinnedConfigSource.parse(StaticVotingConfig.BUNDLED_PINNED_SOURCE).url

private fun EditorDraft.canSave(): Boolean =
    name.trim().length <= MAX_CUSTOM_CHAIN_NAME_LENGTH &&
        pinnedSource.trim().isNotEmpty() &&
        !pinnedSource.trim().isInvalidPinnedSource()

private fun String.isInvalidPinnedSource(): Boolean =
    isNotBlank() && runCatching { PinnedConfigSource.parse(this) }.isFailure

private const val MAX_CUSTOM_CHAIN_NAME_LENGTH = 15
