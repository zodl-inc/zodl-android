package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository

/**
 * Pairs [device]'s first ZIP 32 account and stashes the result for the birthday screens that
 * follow. Returns the account the wallet already holds for that viewing key instead, if any — the
 * caller then shows the "Account Already Added" sheet rather than importing a duplicate. An account
 * the wallet holds without a Ledger binding gets the new pairing's binding stored instead, so it can
 * be signed for again.
 */
class PairLedgerDeviceUseCase(
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val accountDataSource: AccountDataSource,
    private val ledgerAccountBindingProvider: LedgerAccountBindingProvider,
) {
    suspend operator fun invoke(device: LedgerBluetoothDevice): PairLedgerDeviceResult {
        val pairing = ledgerDeviceDataSource.pair(device)
        val existing =
            accountDataSource
                .getAllAccounts()
                .filterIsInstance<LedgerAccount>()
                .firstOrNull { account -> account.sdkAccount.ufvk == pairing.ufvk.encoding }
        return when {
            existing == null -> {
                ledgerPairingRepository.set(pairing)
                PairLedgerDeviceResult.Paired
            }

            existing.isBound -> {
                ledgerPairingRepository.clear()
                PairLedgerDeviceResult.AlreadyAdded(existing)
            }

            else -> {
                ledgerPairingRepository.clear()
                ledgerAccountBindingProvider.save(
                    accountUuid = existing.sdkAccount.accountUuid,
                    deviceIdentityEncoding = pairing.binding.deviceIdentity.encoding,
                    zip32AccountIndex = pairing.binding.zip32AccountIndex.index,
                )
                PairLedgerDeviceResult.Rebound(existing)
            }
        }
    }
}

sealed interface PairLedgerDeviceResult {
    data object Paired : PairLedgerDeviceResult

    data class AlreadyAdded(
        val account: WalletAccount
    ) : PairLedgerDeviceResult

    /**
     * The wallet held the account without a Ledger binding; the pairing's binding is now stored
     * with it.
     */
    data class Rebound(
        val account: WalletAccount
    ) : PairLedgerDeviceResult
}
