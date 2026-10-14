package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.LedgerAccountBindingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Persists the Ledger binding of an imported account — the paired device's identity encoding and
 * the account's ZIP 32 index on it — in encrypted preferences, keyed by the account's UUID.
 *
 * The two halves live in one value under one key, so a binding is either fully stored or absent:
 * split across two keys, a half-completed write would be observable as an identity with no index,
 * and an index guessed for a signing account is how you sign with the wrong key.
 *
 * The device identity is privacy-sensitive — it is linkable to the account's first transparent
 * address once that address has spent on chain — which is why this lives in the encrypted store
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
            emitAll(bindingDefault(accountUuid).observe(encryptedPreferenceProvider()))
        }

    override suspend fun save(
        accountUuid: AccountUuid,
        deviceIdentityEncoding: String,
        zip32AccountIndex: Long
    ) = bindingDefault(accountUuid).putValue(
        encryptedPreferenceProvider(),
        LedgerAccountBindingData(
            deviceIdentityEncoding = deviceIdentityEncoding,
            zip32AccountIndex = Zip32AccountIndex.new(zip32AccountIndex),
        )
    )

    override suspend fun clear(accountUuid: AccountUuid) =
        bindingDefault(accountUuid).putValue(encryptedPreferenceProvider(), null)
}

private fun bindingDefault(accountUuid: AccountUuid) =
    LedgerBindingPreferenceDefault(PreferenceKey("$BINDING_KEY_PREFIX${accountUuid.toHex()}"))

/**
 * Encodes the binding as `<identity>|<index>`. A device identity is `tpk0-` followed by hex, so it
 * never contains the separator. Anything that does not parse, an index outside the ZIP 32 range
 * included, reads as no binding at all rather than as a partially recovered one.
 */
private class LedgerBindingPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<LedgerAccountBindingData?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider): LedgerAccountBindingData? {
        val encoded = preferenceProvider.getString(key).orEmpty()
        val identity = encoded.substringBeforeLast(SEPARATOR, missingDelimiterValue = "")
        val index =
            encoded
                .substringAfterLast(SEPARATOR, missingDelimiterValue = "")
                .toLongOrNull()
                ?.let { runCatching { Zip32AccountIndex.new(it) }.getOrNull() }
        return if (identity.isEmpty() || index == null) {
            null
        } else {
            LedgerAccountBindingData(
                deviceIdentityEncoding = identity,
                zip32AccountIndex = index,
            )
        }
    }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: LedgerAccountBindingData?
    ) = preferenceProvider.putString(
        key,
        newValue?.let { "${it.deviceIdentityEncoding}$SEPARATOR${it.zip32AccountIndex.index}" }
    )
}

@OptIn(ExperimentalStdlibApi::class)
private fun AccountUuid.toHex() = value.toHexString()

private const val SEPARATOR = "|"

private const val BINDING_KEY_PREFIX = "ledger_account_binding_"
