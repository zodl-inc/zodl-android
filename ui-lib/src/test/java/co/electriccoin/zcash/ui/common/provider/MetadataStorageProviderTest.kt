package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [MetadataStorageProviderImpl] naming, listing and creating an account's metadata files.
 */
class MetadataStorageProviderTest {
    private val filesDir = createTempDirectory("metadata-storage-provider-test").toFile()
    private val metadataDir = File(filesDir, "metadata")
    private val provider: MetadataStorageProvider =
        MetadataStorageProviderImpl(
            context =
                mockk<Context> {
                    every { filesDir } returns this@MetadataStorageProviderTest.filesDir
                }
        )

    @Test
    fun getStorageFilesReturnsNothingWhenNothingExists() {
        val key = metadataKey(0, 1)

        assertEquals(emptyList(), provider.getStorageFiles(key))
    }

    @Test
    fun getStorageFilesReturnsTheCanonicalFileWhenOnlyItExists() {
        val key = metadataKey(0, 1)
        val canonicalFile = metadataFile(key.fileIdentifiers().first(), contents = "canonical")

        assertEquals(listOf(canonicalFile), provider.getStorageFiles(key))
    }

    @Test
    fun getStorageFilesReturnsAFileNamedAfterANonCanonicalKeyToo() {
        val key = metadataKey(0, 1)
        val canonicalFile = metadataFile(key.fileIdentifiers().first(), contents = "canonical")
        val legacyFile = metadataFile(key.fileIdentifiers()[1], contents = "legacy")

        val result = provider.getStorageFiles(key)

        assertEquals(listOf(canonicalFile, legacyFile), result)
    }

    @Test
    fun getStorageFilesSkipsAnIdentifierWithNoFileOnDisk() {
        val key = metadataKey(0, 1)
        val legacyFile = metadataFile(key.fileIdentifiers()[1], contents = "legacy")

        assertEquals(listOf(legacyFile), provider.getStorageFiles(key))
    }

    @Test
    fun getOrCreateStorageFileCreatesTheCanonicalFileWhenNothingExists() {
        val key = metadataKey(0, 1)

        val result = provider.getOrCreateStorageFile(key)

        assertEquals(key.fileIdentifiers().first(), result.name)
        assertTrue(result.exists())
    }

    @Test
    fun getOrCreateStorageFileReturnsTheExistingCanonicalFileWithoutTouchingIt() {
        val key = metadataKey(0, 1)
        val canonicalFile = metadataFile(key.fileIdentifiers().first(), contents = "canonical")

        val result = provider.getOrCreateStorageFile(key)

        assertEquals(canonicalFile, result)
        assertEquals("canonical", result.readText())
    }

    private fun metadataFile(identifier: String, contents: String = ""): File {
        metadataDir.mkdirs()
        return File(metadataDir, identifier).apply { writeText(contents) }
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
