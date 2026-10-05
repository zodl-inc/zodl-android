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
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
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
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

interface MetadataDataSource {
    fun observe(key: MetadataKey): Flow<MetadataV3?>

    suspend fun flipTxAsBookmarked(txId: String, key: MetadataKey)

    suspend fun createOrUpdateTxNote(
        txId: String,
        note: String,
        key: MetadataKey
    )

    suspend fun deleteTxNote(txId: String, key: MetadataKey)

    suspend fun markTxMemoAsRead(txId: String, key: MetadataKey)

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
    )

    suspend fun updateSwap(
        depositAddress: String,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        mode: SwapMode,
        origin: SimpleSwapAsset,
        destination: SimpleSwapAsset,
        key: MetadataKey
    )

    // suspend fun deleteSwap(depositAddress: String, key: MetadataKey)

    suspend fun addSwapAssetToHistory(
        tokenTicker: String,
        chainTicker: String,
        key: MetadataKey
    )

    suspend fun delete(key: MetadataKey)
}

@Suppress("TooManyFunctions")
class MetadataDataSourceImpl(
    private val metadataStorageProvider: MetadataStorageProvider,
    private val metadataProvider: MetadataProvider,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider,
) : MetadataDataSource {
    private val mutex = Mutex()

    private val metadataUpdatePipeline = MutableSharedFlow<Pair<MetadataKey, MetadataV3?>>()

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
    ) {
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
        withContext(Dispatchers.IO) {
            mutex.withLock {
                metadataStorageProvider.getStorageFiles(key).forEach { it.delete() }
                metadataUpdatePipeline.emit(key to null)
            }
        }

    private suspend fun getMetadataInternal(key: MetadataKey): MetadataV3 = readMetadata(key).metadata

    /**
     * A hardware-wallet account's metadata key can have more than one file on disk: the SDK
     * derives one key per viewing-key item, and a key read back in a different order than it was
     * derived in names a different file. This reads every file that exists under any of [key]'s
     * identifiers, merges the ones that decode into one [MetadataV3], writes the result to the
     * canonical file and, only once that write landed, removes the merged files.
     *
     * A file that does not decode is never deleted and is retried on every read. A canonical file
     * that does not decode is first set aside under a name [MetadataStorageProvider.getStorageFiles]
     * keeps finding, so nothing is written over it. When the files cannot be listed or the
     * canonical file cannot be created, the result is read-only and nothing is written.
     */
    private suspend fun readMetadata(key: MetadataKey): MetadataRead =
        withContext(Dispatchers.IO) {
            val existingFiles =
                runCatching { metadataStorageProvider.getStorageFiles(key) }
                    .onFailure { e -> Twig.warn(e) { "Failed to list metadata files" } }
                    .getOrNull()
            val canonicalFile =
                runCatching { metadataStorageProvider.getOrCreateStorageFile(key) }
                    .onFailure { e -> Twig.warn(e) { "Failed to create metadata file" } }
                    .getOrNull()

            if (existingFiles == null || canonicalFile == null) {
                val readable = existingFiles ?: listOfNotNull(canonicalFile)
                return@withContext MetadataRead(
                    metadata = readable.mapNotNull { readNonEmpty(it, key) }.mergeOrDefault(),
                    isWritable = false
                )
            }

            val canonicalExists = canonicalFile in existingFiles && canonicalFile.length() > 0
            val canonicalMetadata = if (canonicalExists) readWithRetry(canonicalFile, key) else null
            val decodedOthers =
                existingFiles
                    .filterNot { it == canonicalFile }
                    .mapNotNull { file -> readWithRetry(file, key)?.let { file to it } }

            if (canonicalExists && canonicalMetadata == null && !setAsideUndecodable(canonicalFile)) {
                return@withContext MetadataRead(
                    metadata = decodedOthers.map { it.second }.mergeOrDefault(),
                    isWritable = false
                )
            }

            if (decodedOthers.isEmpty()) {
                return@withContext MetadataRead(
                    metadata = canonicalMetadata ?: defaultMetadata(),
                    isWritable = true
                )
            }

            val merged = (listOfNotNull(canonicalMetadata) + decodedOthers.map { it.second }).mergeOrDefault()
            if (writeToLocalStorage(merged, key)) {
                decodedOthers.forEach { (file, _) -> file.delete() }
            }
            MetadataRead(metadata = merged, isWritable = true)
        }

    private fun setAsideUndecodable(file: File): Boolean =
        runCatching { check(metadataStorageProvider.setAsideUndecodable(file)) { "Rename failed" } }
            .onFailure { e -> Twig.warn(e) { "Failed to set the undecodable metadata file aside" } }
            .isSuccess

    private suspend fun readNonEmpty(file: File, key: MetadataKey): MetadataV3? =
        if (file.length() > 0) readWithRetry(file, key) else null

    /**
     * Reads [file], retrying only an [IOException], up to [READ_ATTEMPTS] attempts in total; a
     * decryption or format failure is final at once. Returns null when the file does not decode.
     */
    private suspend fun readWithRetry(file: File, key: MetadataKey): MetadataV3? {
        var attempt = 1
        while (true) {
            val result = runCatching { metadataProvider.readMetadataFromFile(file, key) }
            val error = result.exceptionOrNull() ?: return result.getOrThrow()
            if (error !is IOException || attempt >= READ_ATTEMPTS) {
                Twig.warn(error) { "Failed to decrypt metadata" }
                return null
            }
            attempt++
            delay(READ_RETRY_DELAY)
        }
    }

    /**
     * Returns whether the write landed. A failed write leaves the previous file content intact.
     */
    private suspend fun writeToLocalStorage(metadata: MetadataV3, key: MetadataKey): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
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
    ) = withContext(Dispatchers.IO) {
        val read = readMetadata(key)
        if (!read.isWritable) {
            Twig.warn { "Skipping the metadata update, the metadata storage is unavailable" }
            return@withContext
        }
        val metadata = read.metadata

        val accountMetadata = metadata.accountMetadata

        val updatedMetadata =
            metadata.copy(
                lastUpdated = Instant.now(),
                accountMetadata = transform(accountMetadata)
            )

        writeToLocalStorage(updatedMetadata, key)

        metadataUpdatePipeline.emit(key to updatedMetadata)
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
 * The metadata a read produced and whether it may be written back, which it may not when the
 * storage could not be listed or the canonical file could not be created.
 */
private data class MetadataRead(
    val metadata: MetadataV3,
    val isWritable: Boolean
)

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
 * dropped. A tie keeps this side, which callers pass as the canonical file.
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
 * [SwapsMetadataV3.lastUsedAssetHistory] is an ordered recency list, not a union: it comes whole
 * from the side whose top-level timestamp is newer.
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
