package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetWalletSeedBytesUseCase(
    private val persistableWalletProvider: PersistableWalletProvider
) {
    suspend operator fun invoke(): ByteArray =
        withContext(Dispatchers.IO) {
            val persistableWallet = persistableWalletProvider.requirePersistableWallet()
            Mnemonics.MnemonicCode(persistableWallet.seedPhrase.joinToString()).toSeed()
        }
}
