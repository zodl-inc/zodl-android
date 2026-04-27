package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns the raw 64-byte BIP39 seed for the currently persisted wallet.
 * Used by the voting backend for delegation signing.
 *
 * Caller is responsible for zeroing the returned array after use.
 */
class GetWalletSeedBytesUseCase(
    private val persistableWalletProvider: PersistableWalletProvider,
) {
    suspend operator fun invoke(): ByteArray =
        withContext(Dispatchers.IO) {
            val wallet = persistableWalletProvider.requirePersistableWallet()
            Mnemonics.MnemonicCode(wallet.seedPhrase.joinToString()).toSeed()
        }
}
