package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoteChainConfigVM(
    private val votingChainConfigRepository: VotingChainConfigRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val editorDraft = MutableStateFlow<EditorDraft?>(null)
    private val errorSheet = MutableStateFlow<ZashiConfirmationState?>(null)

    val state =
        combine(votingChainConfigRepository.state, editorDraft, errorSheet) { chainConfig, editor, errorSheet ->
            VoteChainConfigState(
                chains = buildChainItems(chainConfig),
                editor = editor?.toState(),
                errorSheet = errorSheet,
                onBack = ::onBack,
                onAddCustom = ::onAddCustom
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            initialValue = null
        )

    private fun buildChainItems(chainConfig: VotingChainConfigState): List<VoteChainConfigItemState> =
        buildList {
            add(
                VoteChainConfigItemState(
                    id = DEFAULT_CHAIN_ID,
                    radioButtonState = RadioButtonState(
                        text = stringRes(R.string.vote_chain_config_default_name),
                        subtitle = stringRes(compactSource(StaticVotingConfig.BUNDLED_PINNED_SOURCE)),
                        isChecked = chainConfig.selected is VotingChainConfigSelection.Default,
                        onClick = ::onDefaultSelected
                    ),
                    fullUrl = stringRes(StaticVotingConfig.BUNDLED_PINNED_SOURCE),
                    editButton = null,
                    deleteButton = null
                )
            )
            chainConfig.customChains.forEach { chain ->
                add(
                    VoteChainConfigItemState(
                        id = chain.id,
                        radioButtonState = RadioButtonState(
                            text = stringRes(chain.name),
                            subtitle = stringRes(compactSource(chain.pinnedSource)),
                            isChecked = chainConfig.selected == VotingChainConfigSelection.Custom(chain.id),
                            onClick = { onCustomSelected(chain.id) }
                        ),
                        fullUrl = stringRes(chain.pinnedSource),
                        editButton = ButtonState(
                            text = stringRes(R.string.vote_chain_config_edit),
                            style = ButtonStyle.TERTIARY,
                            onClick = { onEditCustom(chain) }
                        ),
                        deleteButton = ButtonState(
                            text = stringRes(R.string.vote_chain_config_delete),
                            style = ButtonStyle.DESTRUCTIVE2,
                            onClick = { onDeleteCustom(chain.id) }
                        )
                    )
                )
            }
        }

    private fun onBack() = navigationRouter.back()

    private fun onDefaultSelected() {
        viewModelScope.launch {
            votingChainConfigRepository.selectDefault()
        }
    }

    private fun onCustomSelected(id: String) {
        viewModelScope.launch {
            votingChainConfigRepository.selectCustom(id)
        }
    }

    private fun onAddCustom() {
        editorDraft.value = EditorDraft(
            id = null,
            name = "",
            pinnedSource = ""
        )
    }

    private fun onEditCustom(chain: VotingCustomChainConfig) {
        editorDraft.value = EditorDraft(
            id = chain.id,
            name = chain.name,
            pinnedSource = chain.pinnedSource
        )
    }

    private fun onDeleteCustom(id: String) {
        viewModelScope.launch {
            votingChainConfigRepository.deleteCustom(id)
        }
    }

    private fun onSaveEditor() {
        val draft = editorDraft.value ?: return
        viewModelScope.launch {
            val name = draft.name.trim()
            val pinnedSource = draft.pinnedSource.trim()
            if (name.isEmpty()) {
                showError(stringRes(R.string.vote_chain_config_error_name_required))
                return@launch
            }
            if (pinnedSource.isEmpty()) {
                showError(stringRes(R.string.vote_chain_config_error_url_required))
                return@launch
            }

            val parsedSource = parsePinnedSourceOrShowError(pinnedSource) ?: return@launch
            val current = votingChainConfigRepository.get()
            val duplicateError = duplicateError(
                parsedSource = parsedSource,
                current = current,
                editingId = draft.id
            )
            if (duplicateError != null) {
                showError(duplicateError)
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

    private fun parsePinnedSourceOrShowError(raw: String): PinnedConfigSource? =
        runCatching { PinnedConfigSource.parse(raw) }
            .getOrElse { throwable ->
                showError(stringRes(throwable.message.orEmpty().ifBlank { raw }))
                null
            }

    private fun duplicateError(
        parsedSource: PinnedConfigSource,
        current: VotingChainConfigState,
        editingId: String?
    ): StringResource? {
        if (parsedSource == PinnedConfigSource.parse(StaticVotingConfig.BUNDLED_PINNED_SOURCE)) {
            return stringRes(R.string.vote_chain_config_error_duplicate_default)
        }

        val hasDuplicateCustom = current.customChains
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
        errorSheet.value = ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = stringRes(R.string.vote_chain_config_error_title),
            message = message,
            primaryAction = ButtonState(
                text = stringRes(R.string.vote_dismiss),
                style = ButtonStyle.PRIMARY,
                onClick = ::dismissError
            ),
            secondaryAction = ButtonState(
                text = stringRes(R.string.vote_dismiss),
                style = ButtonStyle.TERTIARY,
                onClick = ::dismissError
            ),
            onBack = ::dismissError
        )
    }

    private fun dismissError() {
        errorSheet.value = null
    }

    private fun EditorDraft.toState() =
        VoteChainConfigEditorState(
            title = stringRes(
                if (id == null) {
                    R.string.vote_chain_config_add_title
                } else {
                    R.string.vote_chain_config_edit_title
                }
            ),
            name = TextFieldState(
                value = stringRes(name),
                onValueChange = ::onNameChanged
            ),
            url = TextFieldState(
                value = stringRes(pinnedSource),
                onValueChange = ::onUrlChanged
            ),
            saveButton = ButtonState(
                text = stringRes(R.string.vote_chain_config_save),
                style = ButtonStyle.PRIMARY,
                onClick = ::onSaveEditor
            ),
            cancelButton = ButtonState(
                text = stringRes(R.string.vote_chain_config_cancel),
                style = ButtonStyle.TERTIARY,
                onClick = ::onCancelEditor
            )
        )

    private companion object {
        const val DEFAULT_CHAIN_ID = "default"
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
