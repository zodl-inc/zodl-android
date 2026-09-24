package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import java.io.File

interface MetadataStorageProvider {
    fun getStorageFiles(key: MetadataKey): List<File>

    fun getOrCreateStorageFile(key: MetadataKey): File
}

class MetadataStorageProviderImpl(
    private val context: Context
) : MetadataStorageProvider {
    /**
     * Every existing file named after one of [key]'s identifiers, in [MetadataKey.fileIdentifiers]
     * order. A key derived in a different preference order can leave data under more than one of
     * these names; merging them back into the canonical file is the caller's job, not this one's.
     */
    override fun getStorageFiles(key: MetadataKey): List<File> {
        val dir = getOrCreateMetadataDir()
        return key
            .fileIdentifiers()
            .map { File(dir, it) }
            .filter { it.exists() && it.isFile }
    }

    override fun getOrCreateStorageFile(key: MetadataKey): File {
        val file = File(getOrCreateMetadataDir(), key.fileIdentifier())
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
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
