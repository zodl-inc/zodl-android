package co.electriccoin.zcash.ui.common.provider

import android.system.Os
import android.system.OsConstants
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataEncryptor
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface MetadataProvider {
    /**
     * Replaces [file]'s content atomically: a failure or a crash leaves the previous content
     * intact. Throws when the write did not land.
     */
    fun writeMetadataToFile(
        file: File,
        metadata: MetadataV3,
        metadataKey: MetadataKey
    )

    fun readMetadataFromFile(
        file: File,
        addressBookKey: MetadataKey
    ): MetadataV3

    /**
     * Flushes the directory holding [file] to disk, so a rename into it survives a crash. Throws
     * when the flush did not land.
     */
    fun syncDirectoryOf(file: File)
}

/**
 * Flushes a directory to disk; a seam so JVM tests never reach [Os].
 */
fun interface DirectorySync {
    fun sync(directory: File)
}

class OsDirectorySync : DirectorySync {
    override fun sync(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}

class MetadataProviderImpl(
    private val metadataEncryptor: MetadataEncryptor,
    private val directorySync: DirectorySync
) : MetadataProvider {
    override fun writeMetadataToFile(
        file: File,
        metadata: MetadataV3,
        metadataKey: MetadataKey
    ) {
        val tempFile = File(file.parentFile, file.name + METADATA_TEMP_FILE_SUFFIX)
        runCatching {
            FileOutputStream(tempFile).use { fileStream ->
                val stream = fileStream.buffered()
                metadataEncryptor.encrypt(
                    key = metadataKey,
                    outputStream = stream,
                    data = metadata
                )
                stream.flush()
                fileStream.fd.sync()
            }
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.onFailure {
            tempFile.delete()
        }.getOrThrow()
    }

    override fun readMetadataFromFile(
        file: File,
        addressBookKey: MetadataKey
    ): MetadataV3 =
        file.inputStream().use { stream ->
            metadataEncryptor.decrypt(
                key = addressBookKey,
                inputStream = stream
            )
        }

    override fun syncDirectoryOf(file: File) = directorySync.sync(checkNotNull(file.absoluteFile.parentFile))
}
