package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.LedgerAccountBindingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Persists the Ledger binding of an imported account — the paired device's identity encoding and
 * the account's ZIP 32 index on it — in encrypted preferences, keyed by the account's UUID.
 *
 * The device identity is privacy-sensitive (it is linkable to the account's first transparent
 * address once that address has spent on chain), which is why it lives in the encrypted store
 * alongside the selected-account UUID and is never logged.
 */
interface LedgerAccountBindingProvider {
    fun observe(accountUuid: AccountUuid): Flow<LedgerAccountBindingData?>

    suspend fun save(
        accountUuid: AccountUuid,
        deviceIdentityEncoding: String,
        zip32AccountIndex: Long
    )

    suspend fun clear(accountUuid: AccountUuid)
}

class LedgerAccountBindingProviderImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : LedgerAccountBindingProvider {
    override fun observe(accountUuid: AccountUuid): Flow<LedgerAccountBindingData?> =
        flow {
            val preferenceProvider = encryptedPreferenceProvider()
            emitAll(
                combine(
                    deviceIdentityDefault(accountUuid).observe(preferenceProvider),
                    zip32AccountIndexDefault(accountUuid).observe(preferenceProvider),
                ) { deviceIdentityEncoding, index ->
                    if (deviceIdentityEncoding == null) {
                        null
                    } else {
                        LedgerAccountBindingData(
                            deviceIdentityEncoding = deviceIdentityEncoding,
                            zip32AccountIndex = Zip32AccountIndex.new(index ?: 0L),
                        )
                    }
                }
            )
        }

    override suspend fun save(
        accountUuid: AccountUuid,
        deviceIdentityEncoding: String,
        zip32AccountIndex: Long
    ) {
        val preferenceProvider = encryptedPreferenceProvider()
        deviceIdentityDefault(accountUuid).putValue(preferenceProvider, deviceIdentityEncoding)
        zip32AccountIndexDefault(accountUuid).putValue(preferenceProvider, zip32AccountIndex)
    }

    override suspend fun clear(accountUuid: AccountUuid) {
        val preferenceProvider = encryptedPreferenceProvider()
        deviceIdentityDefault(accountUuid).putValue(preferenceProvider, null)
        zip32AccountIndexDefault(accountUuid).putValue(preferenceProvider, null)
    }
}

private fun deviceIdentityDefault(accountUuid: AccountUuid) =
    LedgerDeviceIdentityPreferenceDefault(PreferenceKey("$DEVICE_IDENTITY_KEY_PREFIX${accountUuid.toHex()}"))

private fun zip32AccountIndexDefault(accountUuid: AccountUuid) =
    LedgerZip32AccountIndexPreferenceDefault(PreferenceKey("$ZIP32_KEY_PREFIX${accountUuid.toHex()}"))

private class LedgerDeviceIdentityPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<String?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) = preferenceProvider.getString(key)

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: String?
    ) = preferenceProvider.putString(key, newValue)
}

private class LedgerZip32AccountIndexPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<Long?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) = preferenceProvider.getLong(key)

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: Long?
    ) = preferenceProvider.putLong(key, newValue)
}

@OptIn(ExperimentalStdlibApi::class)
private fun AccountUuid.toHex() = value.toHexString()

private const val DEVICE_IDENTITY_KEY_PREFIX = "ledger_device_identity_"

private const val ZIP32_KEY_PREFIX = "ledger_zip32_account_index_"
