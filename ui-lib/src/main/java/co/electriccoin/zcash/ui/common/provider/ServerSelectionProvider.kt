package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.ServerSelection
import kotlinx.coroutines.flow.Flow

interface ServerSelectionProvider {
    val serverSelection: Flow<ServerSelection?>

    suspend fun store(serverSelection: ServerSelection)

    suspend fun getServerSelection(): ServerSelection?
}

class ServerSelectionProviderImpl(
    preferenceHolder: EncryptedPreferenceProvider
) : ServerSelectionProvider {
    private val storageProvider = ServerSelectionStorageProviderImpl(preferenceHolder)

    override val serverSelection = storageProvider.observe()

    override suspend fun store(serverSelection: ServerSelection) {
        storageProvider.store(serverSelection)
    }

    override suspend fun getServerSelection() = storageProvider.get()
}

private interface ServerSelectionStorageProvider : NullableStorageProvider<ServerSelection>

private class ServerSelectionStorageProviderImpl(
    override val preferenceHolder: EncryptedPreferenceProvider,
) : BaseNullableStorageProvider<ServerSelection>(),
    ServerSelectionStorageProvider {
    override val default = ServerSelectionPreferenceDefault(PreferenceKey("server_selection"))
}

private class ServerSelectionPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<ServerSelection?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let { persistedSelection ->
            ServerSelection
                .fromPersistedJson(persistedSelection)
                .also {
                    if (it == null) {
                        Twig.error {
                            "Corrupted server_selection JSON; defaulting to automatic mode"
                        }
                    }
                }
        }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: ServerSelection?
    ) = preferenceProvider.putString(key, newValue?.toJson())
}
