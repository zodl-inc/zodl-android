package co.electriccoin.zcash.ui.common.serialization.metadata

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The filenames a [MetadataKey]'s entries derive, and how they relate to preference order.
 */
class MetadataKeyTest {
    @Test
    fun fileIdentifiersReturnsOneDistinctIdentifierPerKey() {
        val key = metadataKey(0, 1, 2)

        val identifiers = key.fileIdentifiers()

        assertEquals(3, identifiers.size)
        assertEquals(identifiers.toSet().size, identifiers.size)
    }

    @Test
    fun fileIdentifierEqualsTheFirstFileIdentifier() {
        val key = metadataKey(0, 1, 2)

        assertEquals(key.fileIdentifiers().first(), key.fileIdentifier())
    }

    @Test
    fun reorderingTheKeysChangesFileIdentifier() {
        val original = metadataKey(0, 1, 2)
        val reordered = metadataKey(1, 0, 2)

        assertNotEquals(original.fileIdentifier(), reordered.fileIdentifier())
    }

    private fun metadataKey(vararg seeds: Int) =
        MetadataKey(
            bytes =
                seeds.map { seed ->
                    SecretBytes.copyFrom(ByteArray(SEED_SIZE) { (it + seed).toByte() }, InsecureSecretKeyAccess.get())
                }
        )

    private companion object {
        const val SEED_SIZE = 32
    }
}
