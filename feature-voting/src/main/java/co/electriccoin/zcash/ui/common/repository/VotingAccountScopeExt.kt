package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.AccountUuid
import java.nio.ByteBuffer
import java.util.UUID

fun AccountUuid.toVotingAccountScopeId(): String = value.toHex()

/**
 * Canonical dashed UUID string form of this account identifier, as required by the
 * zcash_voting 1.0 JNI surface's `accountUuid` string parameters (parsed via
 * `uuid::Uuid::parse_str` to look up the account inside the real wallet database).
 */
fun AccountUuid.toCanonicalUuidString(): String {
    val buffer = ByteBuffer.wrap(value)
    return UUID(buffer.long, buffer.long).toString()
}
