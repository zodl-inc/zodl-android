package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.AccountUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the Ledger account the connect flow pairs again when the sign sheet's Pair Ledger opened
 * it, so the pairing is checked against that account instead of adding a Ledger. Empty while the
 * flow adds a Ledger. In memory only, like the selected device: process death empties both, and
 * pairing without a target still refuses a second Ledger.
 */
interface LedgerRepairTargetRepository {
    fun set(accountUuid: AccountUuid)

    fun get(): AccountUuid?

    fun clear()
}

class LedgerRepairTargetRepositoryImpl : LedgerRepairTargetRepository {
    private val target = MutableStateFlow<AccountUuid?>(null)

    override fun set(accountUuid: AccountUuid) = target.update { accountUuid }

    override fun get(): AccountUuid? = target.value

    override fun clear() = target.update { null }
}
