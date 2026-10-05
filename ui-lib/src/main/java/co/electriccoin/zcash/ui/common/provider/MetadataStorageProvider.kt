package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import java.io.File
import java.io.IOException

interface MetadataStorageProvider {
    fun getStorageFiles(key: MetadataKey): List<File>

    fun getOrCreateStorageFile(key: MetadataKey): File

    /**
     * Renames [file] to "<name>.undecodable-<epochMillis>", where [getStorageFiles] still finds it,
     * and returns whether the rename landed.
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
    private val context: Context
) : MetadataStorageProvider {
    /**
     * Every existing file named after one of [key]'s identifiers, in [MetadataKey.fileIdentifiers]
     * order, each followed by the files set aside under that identifier by [setAsideUndecodable].
     * A key derived in a different preference order can leave data under more than one of these
     * names; merging them back into the canonical file is the caller's job, not this one's.
     * Throws when the directory cannot be listed, so a listing failure never reads as "no files".
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

    override fun setAsideUndecodable(file: File): Boolean =
        file.renameTo(File(file.parentFile, file.name + UNDECODABLE_INFIX + System.currentTimeMillis()))

    override fun deleteStorageFiles(key: MetadataKey) {
        runCatching {
            val dir = getOrCreateMetadataDir()
            val dirFiles = dir.listFiles()
            if (dirFiles == null) Twig.error { "Failed to list metadata files for deletion" }
            key.fileIdentifiers().forEach { identifier ->
                val setAside = dirFiles.orEmpty().filter { it.name.startsWith(identifier + UNDECODABLE_INFIX) }
                (listOf(File(dir, identifier), File(dir, identifier + METADATA_TEMP_FILE_SUFFIX)) + setAside)
                    .filter { it.exists() && !it.delete() }
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
