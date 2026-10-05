package co.electriccoin.zcash.ui.common.serialization.metadata

import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.serialization.METADATA_ENCRYPTION_KEY_SIZE
import co.electriccoin.zcash.ui.common.serialization.METADATA_FILE_IDENTIFIER_SIZE
import co.electriccoin.zcash.ui.common.serialization.METADATA_SALT_SIZE
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.aead.ChaCha20Poly1305Key
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.util.SecretBytes

/**
 * The long-term key that can decrypt an account's encrypted metadata: notes, bookmarks, read
 * memos and swaps.
 */
class MetadataKey(
    val bytes: List<SecretBytes>
) {
    /**
     * Derives the filename that the canonical (first) key is able to decrypt.
     */
    fun fileIdentifier(): String = fileIdentifiers().first()

    /**
     * Derives the filename each entry of [bytes] is able to decrypt, in the same preference
     * order as [bytes]. Data found under a non-canonical (non-first) entry's identifier should
     * be migrated to [fileIdentifier].
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun fileIdentifiers(): List<String> {
        val access = InsecureSecretKeyAccess.get()
        return bytes.map { secretBytes ->
            val fileIdentifier =
                Hkdf.computeHkdf(
                    "HMACSHA256",
                    secretBytes.toByteArray(access),
                    null,
                    "file_identifier".toByteArray(),
                    METADATA_FILE_IDENTIFIER_SIZE
                )
            "zashi-metadata-" + fileIdentifier.toHexString()
        }
    }

    fun deriveEncryptionKey(salt: ByteArray): ChaCha20Poly1305Key =
        deriveKey(
            salt = salt,
            bytes = bytes.first()
        )

    fun deriveDecryptionKeys(salt: ByteArray): List<ChaCha20Poly1305Key> =
        bytes.map { secretBytes ->
            deriveKey(
                salt = salt,
                bytes = secretBytes
            )
        }

    private fun deriveKey(
        salt: ByteArray,
        bytes: SecretBytes
    ): ChaCha20Poly1305Key {
        assert(salt.size == METADATA_SALT_SIZE)
        val access = InsecureSecretKeyAccess.get()
        val subKey =
            Hkdf.computeHkdf(
                "HMACSHA256",
                bytes.toByteArray(access),
                null,
                salt + "metadata_key".toByteArray(),
                METADATA_ENCRYPTION_KEY_SIZE
            )
        return ChaCha20Poly1305Key.create(SecretBytes.copyFrom(subKey, access))
    }

    companion object {
        suspend fun derive(
            seedPhrase: SeedPhrase,
            network: ZcashNetwork,
            zashiAccount: ZashiAccount,
            ufvk: String?
        ): MetadataKey {
            val key =
                DerivationTool
                    .getInstance()
                    .deriveAccountMetadataKey(
                        seed = seedPhrase.toByteArray(),
                        network = network,
                        accountIndex = zashiAccount.hdAccountIndex,
                    ).derivePrivateUseMetadataKey(
                        ufvk = ufvk,
                        network = network,
                        privateUseSubject = "metadata".toByteArray()
                    )
            return MetadataKey(
                bytes = key.map { SecretBytes.copyFrom(it, InsecureSecretKeyAccess.get()) }
            )
        }
    }
}
