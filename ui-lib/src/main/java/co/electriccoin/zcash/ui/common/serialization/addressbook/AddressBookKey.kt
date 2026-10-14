package co.electriccoin.zcash.ui.common.serialization.addressbook

import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.serialization.ADDRESS_BOOK_ENCRYPTION_KEY_SIZE
import co.electriccoin.zcash.ui.common.serialization.ADDRESS_BOOK_FILE_IDENTIFIER_SIZE
import co.electriccoin.zcash.ui.common.serialization.ADDRESS_BOOK_SALT_SIZE
import co.electriccoin.zcash.ui.common.serialization.Key
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.aead.ChaCha20Poly1305Key
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.util.SecretBytes

/**
 * The long-term key that can decrypt an account's encrypted address book.
 */
class AddressBookKey(
    val key: SecretBytes
) : Key {
    /**
     * Derives the filename that this key is able to decrypt.
     */
    @OptIn(ExperimentalStdlibApi::class)
    override fun fileIdentifier(): String {
        val access = InsecureSecretKeyAccess.get()
        val fileIdentifier =
            Hkdf.computeHkdf(
                "HMACSHA256",
                key.toByteArray(access),
                null,
                "file_identifier".toByteArray(),
                ADDRESS_BOOK_FILE_IDENTIFIER_SIZE
            )
        return "zashi-address-book-" + fileIdentifier.toHexString()
    }

    /**
     * Derives a one-time address book encryption key.
     *
     * At encryption time, the one-time property MUST be ensured by generating a
     * random 32-byte salt.
     */
    override fun deriveEncryptionKey(salt: ByteArray): ChaCha20Poly1305Key {
        assert(salt.size == ADDRESS_BOOK_SALT_SIZE)
        val access = InsecureSecretKeyAccess.get()
        val subKey =
            Hkdf.computeHkdf(
                "HMACSHA256",
                key.toByteArray(access),
                null,
                salt + "encryption_key".toByteArray(),
                ADDRESS_BOOK_ENCRYPTION_KEY_SIZE
            )
        return ChaCha20Poly1305Key.create(SecretBytes.copyFrom(subKey, access))
    }

    companion object {
        /**
         * Derives the long-term key that can decrypt the given account's encrypted
         * address book.
         *
         * This requires access to the seed phrase. If the app has separate access
         * control requirements for the seed phrase and the address book, this key
         * should be cached in the app's keystore.
         */
        suspend fun derive(
            seedPhrase: SeedPhrase,
            network: ZcashNetwork,
            account: ZashiAccount
        ): AddressBookKey {
            val key =
                DerivationTool.getInstance().deriveArbitraryAccountKey(
                    contextString = "ZashiAddressBookEncryptionV1".toByteArray(),
                    seed = seedPhrase.toByteArray(),
                    network = network,
                    accountIndex = account.hdAccountIndex,
                )
            return AddressBookKey(SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get()))
        }
    }
}
