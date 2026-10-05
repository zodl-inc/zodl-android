package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.every
import io.mockk.mockk
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.NoSuchFileException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MetadataStorageProviderImpl] naming, listing, creating, setting aside and deleting an account's
 * metadata files.
 */
class MetadataStorageProviderTest {
    private val filesDir = createTempDirectory("metadata-storage-provider-test").toFile()
    private val metadataDir = File(filesDir, "metadata")
    private val provider: MetadataStorageProvider =
        MetadataStorageProviderImpl(
            context =
                mockk<Context> {
                    every { filesDir } returns this@MetadataStorageProviderTest.filesDir
                },
            currentTimeMillis = { NOW }
        )

    @AfterTest
    fun deleteTempDir() {
        metadataDir.setReadable(true)
        filesDir.deleteRecursively()
    }

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

    @Test
    fun getStorageFilesReturnsFilesSetAsideUnderEveryIdentifierAfterTheirOwnFile() {
        val key = metadataKey(0, 1)
        val (canonicalName, legacyName) = key.fileIdentifiers()
        val canonicalFile = metadataFile(canonicalName, contents = "canonical")
        val canonicalAside = metadataFile("$canonicalName.undecodable-1", contents = "aside")
        val legacyAside = metadataFile("$legacyName.undecodable-2", contents = "aside")
        metadataFile("unrelated.undecodable-3", contents = "other")

        assertEquals(listOf(canonicalFile, canonicalAside, legacyAside), provider.getStorageFiles(key))
    }

    @Test
    fun getStorageFilesThrowsWhenTheDirectoryCannotBeListed() {
        filesDir.mkdirs()
        metadataDir.writeText("not a directory")

        assertFailsWith<IOException> { provider.getStorageFiles(metadataKey(0, 1)) }
    }

    @Test
    fun setAsideUndecodableRenamesTheFileWhereTheListingStillFindsIt() {
        val key = metadataKey(0, 1)
        val canonicalFile = metadataFile(key.fileIdentifier(), contents = "canonical")

        assertTrue(provider.setAsideUndecodable(canonicalFile))

        assertFalse(canonicalFile.exists())
        val setAside = provider.getStorageFiles(key).single()
        assertTrue(setAside.name.startsWith(key.fileIdentifier() + ".undecodable-"))
        assertEquals("canonical", setAside.readText())
    }

    @Test
    fun setAsideUndecodableRetriesUnderTheNextMillisecondWhenTheNameIsTaken() {
        val key = metadataKey(0, 1)
        val canonicalFile = metadataFile(key.fileIdentifier(), contents = "canonical")
        val earlier = metadataFile("${key.fileIdentifier()}.undecodable-$NOW", contents = "earlier")

        assertTrue(provider.setAsideUndecodable(canonicalFile))

        assertFalse(canonicalFile.exists())
        assertEquals("earlier", earlier.readText())
        assertEquals("canonical", File(metadataDir, "${key.fileIdentifier()}.undecodable-${NOW + 1}").readText())
    }

    @Test
    fun setAsideUndecodableNeverReplacesAnExistingFile() {
        val key = metadataKey(0, 1)
        val canonicalFile = metadataFile(key.fileIdentifier(), contents = "canonical")
        val first = metadataFile("${key.fileIdentifier()}.undecodable-$NOW", contents = "first")
        val second = metadataFile("${key.fileIdentifier()}.undecodable-${NOW + 1}", contents = "second")

        assertFalse(provider.setAsideUndecodable(canonicalFile))

        assertEquals("canonical", canonicalFile.readText())
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test
    fun sizeOfReportsAnEmptyFileAsZero() {
        val file = metadataFile(metadataKey(0, 1).fileIdentifier())

        assertEquals(0L, provider.sizeOf(file))
    }

    @Test
    fun sizeOfThrowsForAMissingFileInsteadOfReportingZero() {
        assertFailsWith<NoSuchFileException> { provider.sizeOf(File(metadataDir, "missing")) }
    }

    @Test
    fun deleteStorageFilesRemovesEveryFileOfTheKeyAndNothingElse() {
        val key = metadataKey(0, 1)
        val (canonicalName, legacyName) = key.fileIdentifiers()
        val keyFiles =
            listOf(
                metadataFile(canonicalName, contents = "canonical"),
                metadataFile("$canonicalName.tmp", contents = "partial"),
                metadataFile("$canonicalName.undecodable-1", contents = "aside"),
                metadataFile(legacyName, contents = "legacy"),
                metadataFile("$legacyName.tmp", contents = "partial"),
                metadataFile("$legacyName.undecodable-2", contents = "aside"),
            )
        val unrelated = metadataFile("unrelated", contents = "other")

        provider.deleteStorageFiles(key)

        assertEquals(emptyList(), keyFiles.filter { it.exists() })
        assertTrue(unrelated.exists())
    }

    @Test
    fun deleteStorageFilesStillRemovesFilesByNameWhenListingFails() {
        val key = metadataKey(0, 1)
        val (canonicalName, legacyName) = key.fileIdentifiers()
        val namedFiles =
            listOf(
                metadataFile(canonicalName, contents = "canonical"),
                metadataFile("$canonicalName.tmp", contents = "partial"),
                metadataFile(legacyName, contents = "legacy"),
            )
        try {
            assumeTrue(metadataDir.setReadable(false) && metadataDir.listFiles() == null)

            provider.deleteStorageFiles(key)
        } finally {
            metadataDir.setReadable(true)
        }

        assertEquals(emptyList(), namedFiles.filter { it.exists() })
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
        const val NOW = 1_000L
    }
}
