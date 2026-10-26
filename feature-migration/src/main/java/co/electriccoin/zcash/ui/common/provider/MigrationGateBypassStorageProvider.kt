package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * Remembers the internal-build switch that lifts the SDK's NU7 pause on new scheduled migrations, so it
 * survives a process restart. Only internal builds can turn it on. It is applied to every SDK instance the
 * app creates, and a wallet reset clears it with the rest of the standard preferences.
 */
interface MigrationGateBypassStorageProvider {
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)
}

class MigrationGateBypassStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
) : MigrationGateBypassStorageProvider {
    override suspend fun isEnabled(): Boolean = default.getValue(preferenceHolder())

    override suspend fun setEnabled(enabled: Boolean) = default.putValue(preferenceHolder(), enabled)

    private val default =
        BooleanPreferenceDefault(
            key = PreferenceKey("migration_nu7_gate_bypass"),
            defaultValue = false,
        )
}
