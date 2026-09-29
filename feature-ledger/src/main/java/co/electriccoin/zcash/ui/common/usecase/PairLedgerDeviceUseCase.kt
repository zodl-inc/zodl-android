package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository

/**
 * Pairs [device]'s first ZIP 32 account and stashes the result for the birthday screens that
 * follow. Returns the account the wallet already holds for that viewing key instead, if any — the
 * caller then shows the "Account Already Added" sheet rather than importing a duplicate. An account
 * the wallet holds without a Ledger binding gets the new pairing's binding stored instead, so it can
 * be signed for again.
 *
 * When the flow pairs an account again ([LedgerRepairTargetRepository] holds its UUID), a Ledger
 * that exports any other viewing key is the wrong Ledger. The wallet holds one Ledger account
 * at most, so a viewing key no Ledger account has while another one exists is the wrong Ledger too;
 * nothing is stashed for import either way.
 */
class PairLedgerDeviceUseCase(
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val ledgerRepairTargetRepository: LedgerRepairTargetRepository,
    private val accountDataSource: AccountDataSource,
    private val ledgerAccountBindingProvider: LedgerAccountBindingProvider,
) {
    suspend operator fun invoke(device: LedgerBluetoothDevice): PairLedgerDeviceResult {
        val pairing = ledgerDeviceDataSource.pair(device)
        val ledgerAccounts = accountDataSource.getAllAccounts().filterIsInstance<LedgerAccount>()
        val existing = ledgerAccounts.firstOrNull { account -> account.sdkAccount.ufvk == pairing.ufvk.encoding }
        val target = ledgerRepairTargetRepository.get()
        return when {
            target != null -> {
                repair(target, existing, pairing)
            }

            existing == null && ledgerAccounts.isNotEmpty() -> {
                ledgerPairingRepository.clear()
                PairLedgerDeviceResult.WrongLedger
            }

            existing == null -> {
                ledgerPairingRepository.set(pairing)
                PairLedgerDeviceResult.Paired
            }

            existing.isBound -> {
                ledgerPairingRepository.clear()
                PairLedgerDeviceResult.AlreadyAdded(existing)
            }

            else -> {
                rebind(existing, pairing)
            }
        }
    }

    private suspend fun repair(
        target: AccountUuid,
        existing: LedgerAccount?,
        pairing: LedgerAccountPairing,
    ): PairLedgerDeviceResult =
        if (existing != null && existing.sdkAccount.accountUuid == target) {
            ledgerRepairTargetRepository.clear()
            if (existing.isBound) {
                ledgerPairingRepository.clear()
                PairLedgerDeviceResult.AlreadyAdded(existing)
            } else {
                rebind(existing, pairing)
            }
        } else {
            ledgerPairingRepository.clear()
            PairLedgerDeviceResult.WrongLedger
        }

    private suspend fun rebind(
        existing: LedgerAccount,
        pairing: LedgerAccountPairing,
    ): PairLedgerDeviceResult {
        ledgerPairingRepository.clear()
        ledgerAccountBindingProvider.save(
            accountUuid = existing.sdkAccount.accountUuid,
            deviceIdentityEncoding = pairing.binding.deviceIdentity.encoding,
            zip32AccountIndex = pairing.binding.zip32AccountIndex.index,
        )
        return PairLedgerDeviceResult.Rebound(existing)
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

    /**
     * The Ledger exported a viewing key other than the account being paired again, or a second
     * Ledger's while the wallet already holds one. Nothing was stored or stashed.
     */
    data object WrongLedger : PairLedgerDeviceResult
}
