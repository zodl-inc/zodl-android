package co.electriccoin.zcash.preference

import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A [PreferenceProvider] returning inert values, for tests that only care about instance
 * identity rather than preference behavior.
 */
internal class FakePreferenceProvider : PreferenceProvider {
    override suspend fun hasKey(key: PreferenceKey) = false

    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) = Unit

    override suspend fun putStringSet(
        key: PreferenceKey,
        value: Set<String>?
    ) = Unit

    override suspend fun putLong(
        key: PreferenceKey,
        value: Long?
    ) = Unit

    override suspend fun getLong(key: PreferenceKey): Long? = null

    override suspend fun getString(key: PreferenceKey): String? = null

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? = null

    override fun observe(key: PreferenceKey): Flow<String?> = flowOf(null)

    override suspend fun remove(key: PreferenceKey) = Unit

    override suspend fun clearPreferences() = true
}
