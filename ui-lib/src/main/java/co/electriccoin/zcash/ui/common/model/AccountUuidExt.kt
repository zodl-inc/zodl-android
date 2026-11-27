package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.AccountUuid

/**
 * Stable hex-string form of this account's UUID, suitable for use as a per-account storage key
 * suffix (e.g. `PreferenceKey("some_flag_${accountUuid.toStorageKeyId()}")`). [AccountUuid] doesn't
 * override [Any.toString], so its default `data class` form (`AccountUuid(value=[B@...)`) is
 * neither stable nor human-usable as a key — this is the one to use instead for that purpose.
 *
 * Not the canonical dashed-UUID string form some external APIs need — see
 * `AccountUuid.toCanonicalUuidString()` (voting) for that.
 */
fun AccountUuid.toStorageKeyId(): String = value.toHex()

/**
 * A stable per-account offset in `0..0xFFFF`, derived from the account's storage-key id
 * ([toStorageKeyId]). Used to make otherwise-global integer identifiers (WorkManager work name
 * suffix, AlarmManager request code, notification ids) distinct per account so a Zashi and a
 * Keystone account's migration never overwrite each other's. Hash collision across two accounts is
 * theoretically possible but negligible; a registry was considered and rejected as unnecessary state.
 */
fun accountIdOffset(accountKeyId: String): Int = accountKeyId.hashCode() and ACCOUNT_ID_OFFSET_MASK

private const val ACCOUNT_ID_OFFSET_MASK = 0xFFFF
