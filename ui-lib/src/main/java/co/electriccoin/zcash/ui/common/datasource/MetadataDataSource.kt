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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant

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
                metadataStorageProvider.getStorageFile(key)?.delete()
                metadataUpdatePipeline.emit(key to null)
            }
        }

    private suspend fun getMetadataInternal(key: MetadataKey): MetadataV3 {
        fun readLocalFileToMetadata(key: MetadataKey): MetadataV3? {
            val encryptedFile =
                runCatching { metadataStorageProvider.getStorageFile(key) }.getOrNull()
                    ?: return null

            return runCatching {
                metadataProvider.readMetadataFromFile(encryptedFile, key)
            }.onFailure { e -> Twig.warn(e) { "Failed to decrypt metadata" } }.getOrNull()
        }

        return withContext(Dispatchers.IO) {
            var new: MetadataV3? = readLocalFileToMetadata(key)
            if (new == null) {
                new =
                    MetadataV3(
                        lastUpdated = Instant.now(),
                        accountMetadata = defaultAccountMetadata(),
                    )
                writeToLocalStorage(new, key)
            }
            new
        }
    }

    private suspend fun writeToLocalStorage(metadata: MetadataV3, key: MetadataKey) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = metadataStorageProvider.getOrCreateStorageFile(key)
                metadataProvider.writeMetadataToFile(file, metadata, key)
            }.onFailure { e -> Twig.warn(e) { "Failed to write address book" } }
        }
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
        val metadata = getMetadataInternal(key)

        val accountMetadata = metadata.accountMetadata

        val updatedMetadata =
            metadata.copy(
                lastUpdated = Instant.now(),
                accountMetadata = transform(accountMetadata)
            )

        writeToLocalStorage(updatedMetadata, key)

        metadataUpdatePipeline.emit(key to updatedMetadata)
    }

    private fun Set<String>.toSimpleAssetSet() =
        this
            .map {
                val data = it.split(":")
                simpleSwapAssetProvider.get(data[0], data[1])
            }.toSet()
}

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

private const val MAX_SWAP_ASSETS_IN_HISTORY = 10
