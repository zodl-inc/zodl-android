package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository

/**
 * Pairs [device]'s first ZIP 32 account and stashes the result for the birthday screens that
 * follow. Returns the account the wallet already holds for that viewing key instead, if any — the
 * caller then shows the "Account Already Added" sheet rather than importing a duplicate.
 */
class PairLedgerDeviceUseCase(
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val accountDataSource: AccountDataSource,
) {
    suspend operator fun invoke(device: LedgerBluetoothDevice): PairLedgerDeviceResult {
        val pairing = ledgerDeviceDataSource.pair(device)
        val existing =
            accountDataSource
                .getAllAccounts()
                .firstOrNull { account ->
                    account is LedgerAccount && account.sdkAccount.ufvk == pairing.ufvk.encoding
                }
        if (existing != null) {
            ledgerPairingRepository.clear()
            return PairLedgerDeviceResult.AlreadyAdded(existing)
        }
        ledgerPairingRepository.set(pairing)
        return PairLedgerDeviceResult.Paired
    }
}

sealed interface PairLedgerDeviceResult {
    data object Paired : PairLedgerDeviceResult

    data class AlreadyAdded(
        val account: WalletAccount
    ) : PairLedgerDeviceResult
}
