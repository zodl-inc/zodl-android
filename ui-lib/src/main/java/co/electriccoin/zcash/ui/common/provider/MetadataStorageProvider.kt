package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

interface MetadataStorageProvider {
    /**
     * Every existing file of [key], in [MetadataKey.fileIdentifiers] order, each identifier's own
     * file followed by the files set aside under it by [setAsideUndecodable], oldest first. This
     * order is a contract: a merge breaks timestamp ties in favour of the earlier file, so the
     * canonical file, listed first, wins a tie. Throws when the files cannot be listed, so a
     * listing failure never reads as "no files".
     */
    fun getStorageFiles(key: MetadataKey): List<File>

    fun getOrCreateStorageFile(key: MetadataKey): File

    /**
     * The size of [file] in bytes. Throws [java.nio.file.NoSuchFileException] when it does not
     * exist and another [IOException] when its size cannot be read, never reporting 0 for either.
     */
    fun sizeOf(file: File): Long

    /**
     * Renames [file] to "<name>.undecodable-<epochMillis>", where [getStorageFiles] still finds it,
     * and returns whether the rename landed. Never replaces an existing file.
     */
    fun setAsideUndecodable(file: File): Boolean

    /**
     * Deletes every file of [key]: each identifier's own file and its temporary write file by name,
     * and the files set aside under it. A failure is logged, never thrown, and a failed listing
     * leaves only the set-aside files behind.
     */
    fun deleteStorageFiles(key: MetadataKey)
}

class MetadataStorageProviderImpl(
    private val context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : MetadataStorageProvider {
    /**
     * A key derived in a different preference order can leave data under more than one of [key]'s
     * names; merging them back into the canonical file is the caller's job, not this one's.
     */
    override fun getStorageFiles(key: MetadataKey): List<File> {
        val dir = getOrCreateMetadataDir()
        val dirFiles = dir.listFiles() ?: throw IOException("Failed to list metadata files")
        return key
            .fileIdentifiers()
            .flatMap { identifier ->
                val setAside =
                    dirFiles
                        .filter { it.isFile && it.name.startsWith(identifier + UNDECODABLE_INFIX) }
                        .sortedBy { it.name }
                listOf(File(dir, identifier)).filter { it.isFile } + setAside
            }
    }

    override fun getOrCreateStorageFile(key: MetadataKey): File {
        val file = File(getOrCreateMetadataDir(), key.fileIdentifier())
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    override fun sizeOf(file: File): Long = Files.size(file.toPath())

    /**
     * A name already taken by an earlier set-aside in the same millisecond gets one retry under the
     * next millisecond.
     */
    override fun setAsideUndecodable(file: File): Boolean {
        val millis = currentTimeMillis()
        return moveAside(file, millis) || moveAside(file, millis + 1)
    }

    private fun moveAside(file: File, millis: Long): Boolean =
        try {
            Files.move(file.toPath(), File(file.parentFile, file.name + UNDECODABLE_INFIX + millis).toPath())
            true
        } catch (_: FileAlreadyExistsException) {
            false
        }

    override fun deleteStorageFiles(key: MetadataKey) {
        runCatchingRecoverable {
            val dir = getOrCreateMetadataDir()
            val dirFiles = dir.listFiles()
            if (dirFiles == null) Twig.error { "Failed to list metadata files for deletion" }
            key.fileIdentifiers().forEach { identifier ->
                val setAside =
                    dirFiles.orEmpty().filter { it.isFile && it.name.startsWith(identifier + UNDECODABLE_INFIX) }
                (listOf(File(dir, identifier), File(dir, identifier + METADATA_TEMP_FILE_SUFFIX)) + setAside)
                    .filter { !it.delete() && it.exists() }
                    .forEach { _ -> Twig.error { "Failed to delete a metadata file" } }
            }
        }.onFailure { e -> Twig.error(e) { "Failed to delete metadata files" } }
    }

    private fun getOrCreateMetadataDir(): File {
        val filesDir = context.filesDir
        val addressBookDir = File(filesDir, "metadata")
        if (!addressBookDir.exists()) {
            addressBookDir.mkdir()
        }
        return addressBookDir
    }
}

private const val UNDECODABLE_INFIX = ".undecodable-"

/**
 * The suffix of the temporary file a metadata write lands in before it replaces the target.
 */
internal const val METADATA_TEMP_FILE_SUFFIX = ".tmp"

/**
 * [runCatching] for metadata file work that rethrows an [Error] or a [CancellationException]
 * instead of turning it into a failed [Result].
 */
internal inline fun <T> runCatchingRecoverable(block: () -> T): Result<T> =
    runCatching(block).onFailure { e -> if (e is Error || e is CancellationException) throw e }
