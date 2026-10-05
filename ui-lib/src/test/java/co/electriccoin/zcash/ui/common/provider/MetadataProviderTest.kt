package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataEncryptor
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [MetadataProviderImpl] replacing a metadata file atomically, so a failed write never leaves a
 * truncated file behind.
 */
class MetadataProviderTest {
    private val dir = createTempDirectory("metadata-provider-test").toFile()
    private val file = File(dir, "zashi-metadata-test")
    private val key =
        MetadataKey(listOf(SecretBytes.copyFrom(ByteArray(SEED_SIZE), InsecureSecretKeyAccess.get())))
    private val metadata =
        MetadataV3(
            lastUpdated = Instant.ofEpochSecond(1),
            accountMetadata =
                AccountMetadataV3(
                    bookmarked = emptyList(),
                    read = emptyList(),
                    annotations = emptyList(),
                    swaps = SwapsMetadataV3(swapIds = emptyList(), lastUsedAssetHistory = emptySet())
                )
        )

    @Test
    fun aSuccessfulWriteReplacesTheContentAndLeavesNoTemporaryFile() {
        file.writeText("previous")

        provider(writes = "next").writeMetadataToFile(file, metadata, key)

        assertEquals("next", file.readText())
        assertEquals(listOf(file.name), dir.list()?.toList())
    }

    @Test
    fun aFailedWriteThrowsAndLeavesThePreviousContentIntact() {
        file.writeText("previous")

        assertFailsWith<IOException> {
            provider(writes = "partial", thenFails = true).writeMetadataToFile(file, metadata, key)
        }

        assertEquals("previous", file.readText())
        assertEquals(listOf(file.name), dir.list()?.toList())
    }

    private fun provider(writes: String, thenFails: Boolean = false) =
        MetadataProviderImpl(
            metadataEncryptor =
                mockk<MetadataEncryptor> {
                    every { encrypt(any(), any(), any()) } answers {
                        val stream = secondArg<OutputStream>()
                        stream.write(writes.toByteArray())
                        stream.flush()
                        if (thenFails) throw IOException("write failed")
                    }
                }
        )

    private companion object {
        const val SEED_SIZE = 32
    }
}
