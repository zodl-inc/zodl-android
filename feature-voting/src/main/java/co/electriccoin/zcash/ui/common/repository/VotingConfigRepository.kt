package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.voting.VotingServiceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant

enum class VotingConfigSource {
    LOCAL_OVERRIDE,
    REMOTE,
    REVIEW_OVERRIDE,
    FALLBACK
}

data class VotingConfigSnapshot(
    val serviceConfig: VotingServiceConfig,
    val source: VotingConfigSource,
    val loadedAt: Instant = Instant.now()
)

interface VotingConfigRepository {
    val currentConfig: StateFlow<VotingConfigSnapshot?>

    suspend fun get(): VotingConfigSnapshot?

    fun observe(): Flow<VotingConfigSnapshot?>

    suspend fun store(snapshot: VotingConfigSnapshot)

    suspend fun clear()
}

class VotingConfigRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : VotingConfigRepository {
    private val mutableCurrentConfig = MutableStateFlow<VotingConfigSnapshot?>(null)

    private val configMutex = Mutex()

    override val currentConfig: StateFlow<VotingConfigSnapshot?> = mutableCurrentConfig.asStateFlow()

    override suspend fun get(): VotingConfigSnapshot? {
        currentConfig.value?.let { cached -> return cached }

        val restored =
            encryptedPreferenceProvider()
                .getString(PREFERENCE_KEY)
                ?.toVotingConfigSnapshot()
        mutableCurrentConfig.value = restored
        return restored
    }

    override fun observe(): Flow<VotingConfigSnapshot?> =
        flow {
            emitAll(
                encryptedPreferenceProvider()
                    .observe(PREFERENCE_KEY)
                    .map { encoded -> encoded?.toVotingConfigSnapshot() }
            )
        }

    override suspend fun store(snapshot: VotingConfigSnapshot) {
        configMutex.withLock {
            writeSnapshotLocked(snapshot)
        }
    }

    override suspend fun clear() {
        configMutex.withLock {
            encryptedPreferenceProvider().remove(PREFERENCE_KEY)
            mutableCurrentConfig.value = null
        }
    }

    private suspend fun writeSnapshotLocked(snapshot: VotingConfigSnapshot) {
        encryptedPreferenceProvider().putString(
            key = PREFERENCE_KEY,
            value = snapshot.encode()
        )
        mutableCurrentConfig.value = snapshot
    }

    private companion object {
        val PREFERENCE_KEY = PreferenceKey("voting_current_config")
    }
}

private fun VotingConfigSnapshot.encode(): String =
    JSONObject()
        .put("source", source.name)
        .put("loaded_at", loadedAt.toEpochMilli())
        .put("service_config", JSONObject(serviceConfig.encode()))
        .toString()

private fun String.toVotingConfigSnapshot(): VotingConfigSnapshot {
    val json = JSONObject(this)
    return VotingConfigSnapshot(
        serviceConfig =
            json
                .optJSONObject("service_config")
                ?.toString()
                ?.let(VotingServiceConfig::decode)
                ?: VotingServiceConfig.EMPTY,
        source =
            json
                .optString("source")
                .takeIf(String::isNotEmpty)
                ?.let(VotingConfigSource::valueOf)
                ?: VotingConfigSource.REMOTE,
        loadedAt =
            json
                .optLong("loaded_at")
                .takeIf { json.has("loaded_at") && !json.isNull("loaded_at") }
                ?.let(Instant::ofEpochMilli)
                ?: Instant.now()
    )
}
