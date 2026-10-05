package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.AnnotationMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.BookmarkMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataSimpleSwapAssetV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import co.electriccoin.zcash.ui.common.provider.MetadataProvider
import co.electriccoin.zcash.ui.common.provider.MetadataStorageProvider
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.provider.runCatchingRecoverable
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.NoSuchFileException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Every update returns whether the change was written to disk. A false result means the edit was
 * not saved, because the storage is read-only for this read or the write failed.
 */
interface MetadataDataSource {
    fun observe(key: MetadataKey): Flow<MetadataV3?>

    suspend fun flipTxAsBookmarked(txId: String, key: MetadataKey): Boolean

    suspend fun createOrUpdateTxNote(
        txId: String,
        note: String,
        key: MetadataKey
    ): Boolean

    suspend fun deleteTxNote(txId: String, key: MetadataKey): Boolean

    suspend fun markTxMemoAsRead(txId: String, key: MetadataKey): Boolean

    suspend fun markTxAsSwap(
        depositAddress: String,
        provider: String,
        origin: SimpleSwapAsset,
        destination: SimpleSwapAsset,
        totalFees: Zatoshi,
        totalFeesUsd: BigDecimal,
        mode: SwapMode,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        key: MetadataKey
    ): Boolean

    suspend fun updateSwap(
        depositAddress: String,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        mode: SwapMode,
        origin: SimpleSwapAsset,
        destination: SimpleSwapAsset,
        key: MetadataKey
    ): Boolean

    // suspend fun deleteSwap(depositAddress: String, key: MetadataKey)

    suspend fun addSwapAssetToHistory(
        tokenTicker: String,
        chainTicker: String,
        key: MetadataKey
    ): Boolean

    suspend fun delete(key: MetadataKey)
}

/**
 * Reads and writes an account's metadata files, merging every file of a key into the canonical
 * one. Merged files are deleted only after the write and the directory flush land; after a failed
 * flush they stay and are skipped for the rest of the process. Undecodable non-canonical and
 * set-aside files are skipped, not decrypted again, while their path, size, modification time and
 * the key list are unchanged, and retried in a new process. The canonical file is never skipped.
 */
@Suppress("TooManyFunctions")
class MetadataDataSourceImpl(
    private val metadataStorageProvider: MetadataStorageProvider,
    private val metadataProvider: MetadataProvider,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MetadataDataSource {
    private val mutex = Mutex()

    private val metadataUpdatePipeline = MutableSharedFlow<Pair<MetadataKey, MetadataV3?>>()

    /**
     * Non-canonical and set-aside files that failed to decode, remembered for the process lifetime:
     * they are skipped, not decrypted again, while their path, size, modification time and the
     * key's file identifiers are unchanged, and retried once any of these changes or in a new
     * process. Such a file is never deleted by a read; deleting the account's metadata still
     * removes it. The canonical file is never skipped.
     */
    private val undecodableFiles = ConcurrentHashMap.newKeySet<FileSignature>()

    /**
     * Non-canonical and set-aside files already merged into the canonical file whose directory
     * flush failed, remembered for the process lifetime on the same terms as [undecodableFiles].
     * They stay on disk and are skipped for the rest of the process, so a read neither merges nor
     * writes them again; a new process merges them again, which is idempotent, and deletes them
     * once the flush lands.
     */
    private val mergedFiles = ConcurrentHashMap.newKeySet<FileSignature>()

    private val isFlushFailureLogged = AtomicBoolean(false)

    override fun observe(key: MetadataKey) =
        flow {
            emit(null)
            mutex.withLock { emit(getMetadataInternal(key)) }
            metadataUpdatePipeline.collect { (newKey, newMetadata) ->
                if (key.bytes.size == newKey.bytes.size &&
                    key.bytes
                        .mapIndexed { index, secretBytes -> secretBytes.equalsSecretBytes(newKey.bytes[index]) }
                        .all { it }
                ) {
                    emit(newMetadata)
                }
            }
        }.distinctUntilChanged()

    override suspend fun flipTxAsBookmarked(txId: String, key: MetadataKey) =
        mutex.withLock {
            updateMetadataBookmark(txId = txId, key = key) {
                it.copy(
                    isBookmarked = !it.isBookmarked,
                    lastUpdated = Instant.now(),
                )
            }
        }

    override suspend fun createOrUpdateTxNote(txId: String, note: String, key: MetadataKey) =
        mutex.withLock {
            updateMetadataAnnotation(
                txId = txId,
                key = key
            ) {
                it.copy(
                    content = note,
                    lastUpdated = Instant.now(),
                )
            }
        }

    override suspend fun deleteTxNote(txId: String, key: MetadataKey) =
        mutex.withLock {
            updateMetadataAnnotation(
                txId = txId,
                key = key
            ) {
                it.copy(
                    content = null,
                    lastUpdated = Instant.now(),
                )
            }
        }

    override suspend fun markTxMemoAsRead(txId: String, key: MetadataKey) =
        mutex.withLock {
            updateMetadata(
                key = key,
                transform = { metadata ->
                    metadata.copy(
                        read = (metadata.read.toSet() + txId).toList()
                    )
                }
            )
        }

    override suspend fun markTxAsSwap(
        depositAddress: String,
        provider: String,
        origin: SimpleSwapAsset,
        destination: SimpleSwapAsset,
        totalFees: Zatoshi,
        totalFeesUsd: BigDecimal,
        mode: SwapMode,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        key: MetadataKey
    ) = mutex.withLock {
        updateMetadata(
            key = key,
            transform = { metadata ->
                metadata.copy(
                    swaps =
                        metadata.swaps.copy(
                            swapIds =
                                metadata.swaps.swapIds
                                    .replaceOrAdd(predicate = { it.depositAddress == depositAddress }) {
                                        SwapMetadataV3(
                                            depositAddress = depositAddress,
                                            lastUpdated = Instant.now(),
                                            totalFees = totalFees,
                                            totalUSDFeesInternal = totalFeesUsd,
                                            provider = provider,
                                            fromAsset =
                                                MetadataSimpleSwapAssetV3(
                                                    token = origin.tokenTicker,
                                                    chain = origin.chainTicker
                                                ),
                                            toAsset =
                                                MetadataSimpleSwapAssetV3(
                                                    token = destination.tokenTicker,
                                                    chain = destination.chainTicker
                                                ),
                                            exactInput = mode == EXACT_INPUT,
                                            status = status,
                                            amountOutFormatted = amountOutFormatted,
                                        )
                                    }
                        ),
                )
            }
        )
    }

    override suspend fun updateSwap(
        depositAddress: String,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        mode: SwapMode,
        origin: SimpleSwapAsset,
        destination: SimpleSwapAsset,
        key: MetadataKey,
    ): Boolean =
        mutex.withLock {
            updateMetadata(
                key = key,
                transform = { metadata ->
                    metadata.copy(
                        swaps =
                            metadata.swaps.copy(
                                swapIds =
                                    metadata.swaps.swapIds
                                        .update(predicate = { it.depositAddress == depositAddress }) {
                                            it.copy(
                                                status = status,
                                                amountOutFormatted = amountOutFormatted,
                                                exactInput = mode == EXACT_INPUT,
                                                fromAsset =
                                                    MetadataSimpleSwapAssetV3(
                                                        token = origin.tokenTicker,
                                                        chain = origin.chainTicker
                                                    ),
                                                toAsset =
                                                    MetadataSimpleSwapAssetV3(
                                                        token = destination.tokenTicker,
                                                        chain = destination.chainTicker
                                                    )
                                            )
                                        }
                            ),
                    )
                }
            )
        }

    // override suspend fun deleteSwap(depositAddress: String, key: MetadataKey) {
    //     updateMetadata(
    //         key = key,
    //         transform = { metadata ->
    //             metadata.copy(
    //                 swaps =
    //                     metadata.swaps.copy(
    //                         swapIds =
    //                             metadata.swaps.swapIds
    //                                 .toMutableList()
    //                                 .apply {
    //                                     removeIf { it.depositAddress == depositAddress }
    //                                 }.toList()
    //                     ),
    //             )
    //         }
    //     )
    // }

    override suspend fun addSwapAssetToHistory(
        tokenTicker: String,
        chainTicker: String,
        key: MetadataKey
    ) = mutex.withLock {
        prependSwapAssetToHistory(
            tokenTicker = tokenTicker,
            chainTicker = chainTicker,
            key = key
        )
    }

    override suspend fun delete(key: MetadataKey) =
        withContext(ioDispatcher) {
            mutex.withLock {
                metadataStorageProvider.deleteStorageFiles(key)
                metadataUpdatePipeline.emit(key to null)
            }
        }

    private suspend fun getMetadataInternal(key: MetadataKey): MetadataV3 = readMetadata(key).metadata

    /**
     * A hardware-wallet account's metadata key can have more than one file on disk: the SDK
     * derives one key per viewing-key item, and a key read back in a different order than it was
     * derived in names a different file. This reads every file that exists under any of [key]'s
     * identifiers, merges the ones that decode into one [MetadataV3] and writes the result to the
     * canonical file. The merged files are deleted only after both the write and the directory
     * flush land; after a failed flush they stay on disk and are skipped for the rest of the
     * process, see [mergedFiles].
     *
     * An empty file carries no data: an empty canonical file counts as absent, and any other empty
     * file is deleted once the read is known to be writable. A file whose size or content cannot be
     * read, or that does not decode, is never deleted. An undecodable non-canonical or set-aside
     * file is skipped, not decrypted again, while its path, size, modification time and the key
     * list are unchanged, and retried in a new process, see [undecodableFiles]. The canonical file
     * is never skipped; one that does not decode is first set aside under a name
     * [MetadataStorageProvider.getStorageFiles] keeps finding, so nothing is written over it.
     *
     * The result is read-only, and nothing with data is written and nothing is deleted, when the
     * files cannot be listed, the canonical file cannot be created, the canonical file does not
     * decode and cannot be set aside, or the canonical file is unreadable: a [SecurityException] on
     * the first attempt, or another [IOException] after every retry. A missing canonical file may
     * still be created empty first, since creating it does not depend on the listing.
     */
    private suspend fun readMetadata(key: MetadataKey): MetadataRead =
        withContext(ioDispatcher) {
            val existingFiles =
                runCatchingRecoverable { metadataStorageProvider.getStorageFiles(key) }
                    .onFailure { e -> Twig.warn(e) { "Failed to list metadata files" } }
                    .getOrNull()
            val canonicalFile =
                runCatchingRecoverable { metadataStorageProvider.getOrCreateStorageFile(key) }
                    .onFailure { e -> Twig.warn(e) { "Failed to create metadata file" } }
                    .getOrNull()

            if (existingFiles == null || canonicalFile == null) {
                return@withContext readOnly(existingFiles ?: listOfNotNull(canonicalFile), key)
            }

            val canonicalRead = readWithRetry(canonicalFile, key, skipCacheFingerprint = null)
            val others = existingFiles.filterNot { it == canonicalFile }
            val keyFingerprint = if (others.isEmpty()) null else key.fingerprint()
            val otherReads = others.map { file -> file to readWithRetry(file, key, keyFingerprint) }
            val decodedOthers =
                otherReads.mapNotNull { (file, read) -> (read as? FileRead.Decoded)?.let { file to it } }

            if (!setAsideIfUndecodable(canonicalRead, canonicalFile)) {
                return@withContext MetadataRead(
                    metadata = decodedOthers.map { it.second.metadata }.mergeOrDefault(),
                    isWritable = false
                )
            }

            otherReads.filter { (_, read) -> read == FileRead.Empty }.forEach { (file, _) -> deleteLogged(file) }

            val canonicalMetadata = canonicalRead.decoded()
            if (decodedOthers.isEmpty()) {
                return@withContext MetadataRead(
                    metadata = canonicalMetadata ?: defaultMetadata(),
                    isWritable = true
                )
            }

            val merged =
                (listOfNotNull(canonicalMetadata) + decodedOthers.map { it.second.metadata }).mergeOrDefault()
            if (writeToLocalStorage(merged, key)) {
                retireMergedFiles(canonicalFile, decodedOthers)
            }
            MetadataRead(metadata = merged, isWritable = true)
        }

    /**
     * Deletes the files merged into [canonicalFile] once the directory flush lands; after a failed
     * flush keeps them and remembers them in [mergedFiles] for the rest of the process.
     */
    private fun retireMergedFiles(canonicalFile: File, merged: List<Pair<File, FileRead.Decoded>>) {
        if (syncDirectoryOf(canonicalFile)) {
            merged.forEach { (file, _) -> deleteLogged(file) }
        } else {
            merged.forEach { (_, read) -> read.signature?.let { mergedFiles += it } }
        }
    }

    private suspend fun readOnly(files: List<File>, key: MetadataKey): MetadataRead {
        val canonicalName = key.fileIdentifier()
        val keyFingerprint = if (files.all { it.name == canonicalName }) null else key.fingerprint()
        return MetadataRead(
            metadata =
                files
                    .mapNotNull { file ->
                        val fingerprint = keyFingerprint.takeIf { file.name != canonicalName }
                        readWithRetry(file, key, fingerprint).decoded()
                    }.mergeOrDefault(),
            isWritable = false
        )
    }

    /**
     * Sets [canonicalFile] aside when [read] found it undecodable, and returns whether the canonical
     * name may now be written: true for a decoded, absent or empty file and for one set aside,
     * false for an unreadable file or a failed set-aside.
     */
    private fun setAsideIfUndecodable(read: FileRead, canonicalFile: File): Boolean =
        when (read) {
            is FileRead.Decoded, FileRead.Absent, FileRead.Empty, FileRead.AlreadyMerged -> true
            FileRead.Undecodable -> setAsideUndecodable(canonicalFile)
            FileRead.Unreadable -> false
        }

    private fun setAsideUndecodable(file: File): Boolean =
        runCatchingRecoverable { check(metadataStorageProvider.setAsideUndecodable(file)) { "Rename failed" } }
            .onFailure { e -> Twig.warn(e) { "Failed to set the undecodable metadata file aside" } }
            .isSuccess

    private fun syncDirectoryOf(file: File): Boolean =
        runCatchingRecoverable { metadataProvider.syncDirectoryOf(file) }
            .onFailure { e ->
                if (isFlushFailureLogged.compareAndSet(false, true)) {
                    Twig.warn(e) { "Failed to flush the metadata directory" }
                }
            }.isSuccess

    private fun deleteLogged(file: File) {
        if (!file.delete() && file.exists()) {
            Twig.warn { "Failed to delete a metadata file" }
        }
    }

    /**
     * Reads [file] up to [READ_ATTEMPTS] attempts in total, retrying only an [IOException] other
     * than [NoSuchFileException]; see [readOnce] for how each failure is classified. A non-null
     * [skipCacheFingerprint] lets [undecodableFiles] and [mergedFiles] skip a file.
     */
    private suspend fun readWithRetry(file: File, key: MetadataKey, skipCacheFingerprint: String?): FileRead {
        var attempt = 1
        var read = readOnce(file, key, skipCacheFingerprint, isLastAttempt = attempt >= READ_ATTEMPTS)
        while (read == null) {
            attempt++
            delay(READ_RETRY_DELAY)
            read = readOnce(file, key, skipCacheFingerprint, isLastAttempt = attempt >= READ_ATTEMPTS)
        }
        return read
    }

    /**
     * One read of [file], sized first so an empty file is never decoded. A [NoSuchFileException]
     * is [FileRead.Absent] at once, a [SecurityException] is [FileRead.Unreadable] at once, any
     * other [IOException] returns null to ask for another attempt and is [FileRead.Unreadable] on
     * the last one, and any other exception is [FileRead.Undecodable]. An [Error] or a
     * [kotlinx.coroutines.CancellationException] is rethrown. A file found in [undecodableFiles] is
     * [FileRead.Undecodable] and one found in [mergedFiles] is [FileRead.AlreadyMerged], neither
     * decrypted nor logged again.
     */
    private fun readOnce(
        file: File,
        key: MetadataKey,
        skipCacheFingerprint: String?,
        isLastAttempt: Boolean
    ): FileRead? {
        var signature: FileSignature? = null
        val result =
            runCatchingRecoverable {
                val size = metadataStorageProvider.sizeOf(file)
                signature =
                    skipCacheFingerprint?.let { fingerprint ->
                        FileSignature(file.path, size, metadataStorageProvider.lastModifiedOf(file), fingerprint)
                    }
                when {
                    size == 0L -> FileRead.Empty
                    signature?.let { it in undecodableFiles } == true -> FileRead.Undecodable
                    signature?.let { it in mergedFiles } == true -> FileRead.AlreadyMerged
                    else -> FileRead.Decoded(metadataProvider.readMetadataFromFile(file, key), signature)
                }
            }
        val error = result.exceptionOrNull() ?: return result.getOrThrow()
        val read = classifyReadFailure(error, isLastAttempt)
        if (read == FileRead.Undecodable) {
            signature?.let { undecodableFiles += it }
        }
        if (read == FileRead.Unreadable || read == FileRead.Undecodable) {
            Twig.warn(error) { "Failed to read metadata" }
        }
        return read
    }

    /**
     * The key's file identifiers in order, already derived from the key material, so the caches
     * tell key lists apart without touching the raw key bytes.
     */
    private fun MetadataKey.fingerprint(): String = fileIdentifiers().joinToString(separator = ",")

    private fun classifyReadFailure(error: Throwable, isLastAttempt: Boolean): FileRead? =
        when (error) {
            is NoSuchFileException -> FileRead.Absent
            is SecurityException -> FileRead.Unreadable
            is IOException -> if (isLastAttempt) FileRead.Unreadable else null
            else -> FileRead.Undecodable
        }

    /**
     * Returns whether the write landed. A failed write leaves the previous file content intact.
     */
    private suspend fun writeToLocalStorage(metadata: MetadataV3, key: MetadataKey): Boolean =
        withContext(ioDispatcher) {
            runCatchingRecoverable {
                val file = metadataStorageProvider.getOrCreateStorageFile(key)
                metadataProvider.writeMetadataToFile(file, metadata, key)
            }.onFailure { e -> Twig.warn(e) { "Failed to write metadata" } }
                .isSuccess
        }

    private suspend fun updateMetadataAnnotation(
        txId: String,
        key: MetadataKey,
        transform: (AnnotationMetadataV3) -> AnnotationMetadataV3
    ) = updateMetadata(
        key = key,
        transform = { metadata ->
            metadata.copy(
                annotations =
                    metadata.annotations
                        .replaceOrAdd(
                            predicate = { it.txId == txId },
                            transform = {
                                val bookmarkMetadata = it ?: defaultAnnotationMetadata(txId)
                                transform(bookmarkMetadata)
                            }
                        )
            )
        }
    )

    private suspend fun updateMetadataBookmark(
        txId: String,
        key: MetadataKey,
        transform: (BookmarkMetadataV3) -> BookmarkMetadataV3
    ) = updateMetadata(
        key = key,
        transform = { metadata ->
            metadata.copy(
                bookmarked =
                    metadata.bookmarked
                        .replaceOrAdd(
                            predicate = { it.txId == txId },
                            transform = {
                                val bookmarkMetadata = it ?: defaultBookmarkMetadata(txId)
                                transform(bookmarkMetadata)
                            }
                        )
            )
        }
    )

    private suspend fun prependSwapAssetToHistory(
        tokenTicker: String,
        chainTicker: String,
        key: MetadataKey
    ) = updateMetadata(key) { metadata ->
        val current = metadata.swaps.lastUsedAssetHistory.toSimpleAssetSet()
        val newAsset = simpleSwapAssetProvider.get(tokenTicker = tokenTicker, chainTicker = chainTicker)

        val newList = current.toMutableList()
        if (newList.contains(newAsset)) newList.remove(newAsset)
        newList.add(0, newAsset)
        val finalSet =
            newList
                .take(MAX_SWAP_ASSETS_IN_HISTORY)
                .map { asset -> "${asset.tokenTicker}:${asset.chainTicker}" }
                .toSet()

        metadata.copy(
            swaps =
                metadata.swaps.copy(
                    lastUsedAssetHistory = finalSet
                )
        )
    }

    private suspend fun updateMetadata(
        key: MetadataKey,
        transform: (AccountMetadataV3) -> AccountMetadataV3
    ): Boolean =
        withContext(ioDispatcher) {
            val read = readMetadata(key)
            if (!read.isWritable) {
                Twig.error { "Skipping the metadata update, the metadata storage is unavailable" }
                return@withContext false
            }
            val metadata = read.metadata

            val accountMetadata = metadata.accountMetadata

            val updatedMetadata =
                metadata.copy(
                    lastUpdated = Instant.now(),
                    accountMetadata = transform(accountMetadata)
                )

            val isWritten = writeToLocalStorage(updatedMetadata, key)
            if (isWritten) {
                metadataUpdatePipeline.emit(key to updatedMetadata)
            } else {
                Twig.error { "Skipping the metadata update, the write failed" }
            }
            isWritten
        }

    private fun List<MetadataV3>.mergeOrDefault(): MetadataV3 =
        if (isEmpty()) defaultMetadata() else reduce { merged, next -> merged.merge(next) }

    private fun defaultMetadata() =
        MetadataV3(
            lastUpdated = Instant.now(),
            accountMetadata = defaultAccountMetadata(),
        )

    private fun Set<String>.toSimpleAssetSet() =
        this
            .map {
                val data = it.split(":")
                simpleSwapAssetProvider.get(data[0], data[1])
            }.toSet()
}

/**
 * A file as [MetadataDataSourceImpl]'s process-lifetime caches remember it: its path, size and
 * modification time, and the file identifiers of the key it was read with, never the key itself.
 */
private data class FileSignature(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val keyFingerprint: String
)

/**
 * The metadata a read produced and whether it may be written back, which it may not when the
 * files could not be listed, the canonical file could not be created, the canonical file does not
 * decode and could not be set aside, or the canonical file stayed unreadable. Merged files are
 * deleted only after the write and the directory flush land; after a failed flush they stay and
 * are skipped for the rest of the process.
 */
private data class MetadataRead(
    val metadata: MetadataV3,
    val isWritable: Boolean
)

/**
 * The outcome of reading one metadata file.
 */
private sealed interface FileRead {
    data class Decoded(
        val metadata: MetadataV3,
        val signature: FileSignature? = null
    ) : FileRead

    /** The file was already merged into the canonical file earlier in this process. */
    data object AlreadyMerged : FileRead

    /** The file does not exist. */
    data object Absent : FileRead

    /** The file exists and is empty, so it carries no data. */
    data object Empty : FileRead

    /** The file was read but does not decrypt or parse. */
    data object Undecodable : FileRead

    /**
     * The file's size or content could not be read: a [SecurityException] on the first attempt,
     * or another [IOException] after every retry.
     */
    data object Unreadable : FileRead
}

private fun FileRead.decoded(): MetadataV3? = (this as? FileRead.Decoded)?.metadata

private fun defaultAccountMetadata() =
    AccountMetadataV3(
        bookmarked = emptyList(),
        read = emptyList(),
        annotations = emptyList(),
        swaps =
            SwapsMetadataV3(
                swapIds = emptyList(),
                lastUsedAssetHistory = emptySet()
            ),
    )

private fun defaultBookmarkMetadata(txId: String) =
    BookmarkMetadataV3(
        txId = txId,
        lastUpdated = Instant.now(),
        isBookmarked = false
    )

private fun defaultAnnotationMetadata(txId: String) =
    AnnotationMetadataV3(
        txId = txId,
        lastUpdated = Instant.now(),
        content = null
    )

private fun <T : Any> List<T>.replaceOrAdd(predicate: (T) -> Boolean, transform: (T?) -> T): List<T> {
    val index = this.indexOfFirst(predicate)
    return if (index != -1) {
        this
            .toMutableList()
            .apply {
                set(index, transform(this[index]))
            }.toList()
    } else {
        this + transform(null)
    }
}

private fun <T : Any> List<T>.update(predicate: (T) -> Boolean, transform: (T) -> T): List<T> {
    val index = this.indexOfFirst(predicate)
    return if (index != -1) {
        this
            .toMutableList()
            .apply {
                set(index, transform(this[index]))
            }.toList()
    } else {
        this
    }
}

/**
 * Merges [other] into this metadata by one rule: the newer timestamp wins and older data is
 * dropped. A tie keeps this side, the accumulator, which starts with the first candidate in
 * listing order: the canonical file when present.
 */
private fun MetadataV3.merge(other: MetadataV3): MetadataV3 =
    MetadataV3(
        lastUpdated = maxOf(lastUpdated, other.lastUpdated),
        accountMetadata =
            accountMetadata.merge(
                other = other.accountMetadata,
                otherIsNewer = other.lastUpdated > lastUpdated
            )
    )

private fun AccountMetadataV3.merge(other: AccountMetadataV3, otherIsNewer: Boolean): AccountMetadataV3 =
    AccountMetadataV3(
        bookmarked = bookmarked.mergeById(other.bookmarked, idOf = { it.txId }, lastUpdatedOf = { it.lastUpdated }),
        read = (read.toSet() + other.read).toList(),
        annotations = annotations.mergeById(other.annotations, idOf = { it.txId }, lastUpdatedOf = { it.lastUpdated }),
        swaps = swaps.merge(other.swaps, otherIsNewer)
    )

/**
 * Swaps compare [SwapMetadataV3.lastUpdated], the time the swap was last marked with
 * [MetadataDataSourceImpl.markTxAsSwap], which [MetadataDataSourceImpl.updateSwap] keeps because it
 * is the activity-list time. [SwapsMetadataV3.lastUsedAssetHistory] is an ordered
 * recency list, not a union: it comes whole from the side whose top-level timestamp is newer.
 */
private fun SwapsMetadataV3.merge(other: SwapsMetadataV3, otherIsNewer: Boolean): SwapsMetadataV3 =
    SwapsMetadataV3(
        swapIds = swapIds.mergeById(other.swapIds, idOf = { it.depositAddress }, lastUpdatedOf = { it.lastUpdated }),
        lastUsedAssetHistory =
            (if (otherIsNewer) other.lastUsedAssetHistory else lastUsedAssetHistory)
                .take(MAX_SWAP_ASSETS_IN_HISTORY)
                .toSet()
    )

/**
 * Unions two entry lists by [idOf], keeping whichever entry's [lastUpdatedOf] is newer whenever
 * the same id appears twice, within one list or across both. A tie keeps the earlier entry, so
 * this list wins over [other].
 */
private fun <T> List<T>.mergeById(
    other: List<T>,
    idOf: (T) -> String,
    lastUpdatedOf: (T) -> Instant
): List<T> {
    val merged = LinkedHashMap<String, T>()
    (this + other).forEach { candidate ->
        val id = idOf(candidate)
        val current = merged[id]
        if (current == null || lastUpdatedOf(candidate) > lastUpdatedOf(current)) {
            merged[id] = candidate
        }
    }
    return merged.values.toList()
}

private const val MAX_SWAP_ASSETS_IN_HISTORY = 10

private const val READ_ATTEMPTS = 3

private val READ_RETRY_DELAY = 75.milliseconds
