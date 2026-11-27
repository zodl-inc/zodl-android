package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the pairing a Ledger just produced while the user answers the birthday question, and
 * nothing longer. In memory only: the pairing carries a viewing key, and the enrollment screens
 * that read it fall back to the wallet root when process death empties it.
 */
interface LedgerPairingRepository {
    val pairing: StateFlow<LedgerAccountPairing?>

    fun set(pairing: LedgerAccountPairing)

    fun get(): LedgerAccountPairing?

    fun clear()
}

class LedgerPairingRepositoryImpl : LedgerPairingRepository {
    private val mutablePairing = MutableStateFlow<LedgerAccountPairing?>(null)

    override val pairing: StateFlow<LedgerAccountPairing?> = mutablePairing.asStateFlow()

    override fun set(pairing: LedgerAccountPairing) = mutablePairing.update { pairing }

    override fun get(): LedgerAccountPairing? = mutablePairing.value

    override fun clear() = mutablePairing.update { null }
}
