package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the Ledger account the connect flow pairs again when the sign sheet's Pair Ledger opened
 * it, so the pairing is checked against that account instead of adding a Ledger. Empty while the
 * flow adds a Ledger. In memory only, like the selected device: process death empties both, and
 * pairing without a target still refuses a second Ledger.
 *
 * The account's ZIP 32 index on the device is kept with it when its binding still holds one, so the
 * scan screen can offer it as the account to pair; the user may still choose another.
 */
interface LedgerRepairTargetRepository {
    fun set(
        accountUuid: AccountUuid,
        zip32AccountIndex: Zip32AccountIndex?,
    )

    fun get(): AccountUuid?

    /**
     * The target's stored index; null when there is no target or its binding holds no index.
     */
    fun getZip32AccountIndex(): Zip32AccountIndex?

    fun clear()
}

class LedgerRepairTargetRepositoryImpl : LedgerRepairTargetRepository {
    private val target = MutableStateFlow<Target?>(null)

    override fun set(
        accountUuid: AccountUuid,
        zip32AccountIndex: Zip32AccountIndex?,
    ) = target.update { Target(accountUuid, zip32AccountIndex) }

    override fun get(): AccountUuid? = target.value?.accountUuid

    override fun getZip32AccountIndex(): Zip32AccountIndex? = target.value?.zip32AccountIndex

    override fun clear() = target.update { null }

    private data class Target(
        val accountUuid: AccountUuid,
        val zip32AccountIndex: Zip32AccountIndex?,
    )
}
