package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VotingChainConfigState(
    val selected: VotingChainConfigSelection = VotingChainConfigSelection.Default,
    val customChains: List<VotingCustomChainConfig> = emptyList(),
) {
    val selectedPinnedSource: String?
        get() =
            when (val selection = selected) {
                VotingChainConfigSelection.Default -> null
                is VotingChainConfigSelection.Custom ->
                    customChains.firstOrNull { it.id == selection.id }?.pinnedSource
            }

    val isOnDefaultConfig: Boolean
        get() = selectedPinnedSource == null
}

sealed interface VotingChainConfigSelection {
    data object Default : VotingChainConfigSelection

    data class Custom(val id: String) : VotingChainConfigSelection
}

data class VotingCustomChainConfig(
    val id: String,
    val name: String,
    val pinnedSource: String,
)

interface VotingChainConfigRepository {
    val state: StateFlow<VotingChainConfigState>

    suspend fun get(): VotingChainConfigState

    suspend fun selectDefault()

    suspend fun selectCustom(id: String)

    suspend fun addCustom(
        name: String,
        pinnedSource: String
    ): VotingCustomChainConfig

    suspend fun updateCustom(
        id: String,
        name: String,
        pinnedSource: String
    )

    suspend fun deleteCustom(id: String)
}

class VotingChainConfigRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : VotingChainConfigRepository {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutableState = MutableStateFlow(VotingChainConfigState())
    private var hasLoaded = false

    override val state: StateFlow<VotingChainConfigState> = mutableState.asStateFlow()

    init {
        scope.launch {
            get()
        }
    }

    override suspend fun get(): VotingChainConfigState =
        mutex.withLock {
            if (!hasLoaded) {
                mutableState.value = readPersistedState()
                hasLoaded = true
            }
            mutableState.value
        }

    override suspend fun selectDefault() {
        updateState { it.copy(selected = VotingChainConfigSelection.Default) }
    }

    override suspend fun selectCustom(id: String) {
        updateState { current ->
            if (current.customChains.none { chain -> chain.id == id }) {
                current
            } else {
                current.copy(selected = VotingChainConfigSelection.Custom(id))
            }
        }
    }

    override suspend fun addCustom(
        name: String,
        pinnedSource: String
    ): VotingCustomChainConfig {
        val chain = VotingCustomChainConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            pinnedSource = pinnedSource
        )
        updateState { current ->
            current.copy(
                selected = VotingChainConfigSelection.Custom(chain.id),
                customChains = current.customChains + chain
            )
        }
        return chain
    }

    override suspend fun updateCustom(
        id: String,
        name: String,
        pinnedSource: String
    ) {
        updateState { current ->
            current.copy(
                customChains = current.customChains.map { chain ->
                    if (chain.id == id) {
                        chain.copy(name = name, pinnedSource = pinnedSource)
                    } else {
                        chain
                    }
                }
            )
        }
    }

    override suspend fun deleteCustom(id: String) {
        updateState { current ->
            val remaining = current.customChains.filterNot { chain -> chain.id == id }
            val selected = if (current.selected == VotingChainConfigSelection.Custom(id)) {
                VotingChainConfigSelection.Default
            } else {
                current.selected
            }
            current.copy(
                selected = selected,
                customChains = remaining
            )
        }
    }

    private suspend fun updateState(transform: (VotingChainConfigState) -> VotingChainConfigState) {
        mutex.withLock {
            if (!hasLoaded) {
                mutableState.value = readPersistedState()
                hasLoaded = true
            }
            val updated = transform(mutableState.value).withValidSelection()
            persist(updated)
            mutableState.value = updated
        }
    }

    private suspend fun readPersistedState(): VotingChainConfigState =
        encryptedPreferenceProvider()
            .getString(PREFERENCE_KEY)
            ?.toVotingChainConfigState()
            ?.withValidSelection()
            ?: VotingChainConfigState()

    private suspend fun persist(state: VotingChainConfigState) {
        encryptedPreferenceProvider().putString(
            key = PREFERENCE_KEY,
            value = state.encode()
        )
    }

    private companion object {
        val PREFERENCE_KEY = PreferenceKey("voting_chain_config")
    }
}

private fun VotingChainConfigState.withValidSelection(): VotingChainConfigState =
    when (val selection = selected) {
        VotingChainConfigSelection.Default -> this
        is VotingChainConfigSelection.Custom ->
            if (customChains.any { chain -> chain.id == selection.id }) {
                this
            } else {
                copy(selected = VotingChainConfigSelection.Default)
            }
    }

private fun VotingChainConfigState.encode(): String =
    JSONObject()
        .put(
            "selected",
            when (val selection = selected) {
                VotingChainConfigSelection.Default ->
                    JSONObject().put("type", "default")

                is VotingChainConfigSelection.Custom ->
                    JSONObject()
                        .put("type", "custom")
                        .put("id", selection.id)
            }
        )
        .put(
            "custom_chains",
            JSONArray(
                customChains.map { chain ->
                    JSONObject()
                        .put("id", chain.id)
                        .put("name", chain.name)
                        .put("pinned_source", chain.pinnedSource)
                }
            )
        )
        .toString()

private fun String.toVotingChainConfigState(): VotingChainConfigState {
    val json = runCatching { JSONObject(this) }.getOrNull() ?: return VotingChainConfigState()
    val customChains = json.optJSONArray("custom_chains")
        .toVotingCustomChainConfigs()
    val selectedJson = json.optJSONObject("selected")
    val selected = when (selectedJson?.optString("type")) {
        "custom" -> selectedJson.optString("id")
            .takeIf(String::isNotBlank)
            ?.let(VotingChainConfigSelection::Custom)
            ?: VotingChainConfigSelection.Default

        else -> VotingChainConfigSelection.Default
    }
    return VotingChainConfigState(
        selected = selected,
        customChains = customChains
    )
}

private fun JSONArray?.toVotingCustomChainConfigs(): List<VotingCustomChainConfig> =
    buildList {
        if (this@toVotingCustomChainConfigs == null) {
            return@buildList
        }
        for (index in 0 until length()) {
            val json = optJSONObject(index) ?: continue
            val id = json.optString("id").takeIf(String::isNotBlank) ?: continue
            val name = json.optString("name").takeIf(String::isNotBlank) ?: continue
            val pinnedSource = json.optString("pinned_source").takeIf(String::isNotBlank) ?: continue
            add(
                VotingCustomChainConfig(
                    id = id,
                    name = name,
                    pinnedSource = pinnedSource
                )
            )
        }
    }
