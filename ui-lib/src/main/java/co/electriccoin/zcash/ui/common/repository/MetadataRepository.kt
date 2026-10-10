package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MetadataDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.ZecSimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.metadata.SwapMetadataV3
import co.electriccoin.zcash.ui.common.provider.MetadataKeyStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant

@Suppress("TooManyFunctions")
interface MetadataRepository {
    /**
     * Returns whether the change was saved. The write finishes in the repository's own scope even
     * when the caller is cancelled, and a false result has already been logged.
     */
    suspend fun flipTxBookmark(txId: String): Boolean

    /**
     * Returns whether the note was saved; see [flipTxBookmark].
     */
    suspend fun createOrUpdateTxNote(txId: String, note: String): Boolean

    /**
     * Returns whether the note was deleted; see [flipTxBookmark].
     */
    suspend fun deleteTxNote(txId: String): Boolean

    /**
     * A background write: a failure is only logged.
     */
    fun markTxMemoAsRead(txId: String)

    /**
     * Returns whether the swap was saved; see [flipTxBookmark].
     */
    suspend fun markTxAsSwap(
        depositAddress: String,
        provider: String,
        origin: SwapAsset,
        destination: SwapAsset,
        totalFees: Zatoshi,
        totalFeesUsd: BigDecimal,
        amountOutFormatted: BigDecimal,
        mode: SwapMode,
        status: SwapStatus,
    ): Boolean

    /**
     * A background write from swap status polling: a failure is only logged.
     */
    fun updateSwap(
        depositAddress: String,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        mode: SwapMode,
        origin: SwapAsset,
        destination: SwapAsset,
    )

    // fun deleteSwap(depositAddress: String)

    /**
     * A background write: a failure is only logged.
     */
    fun addSwapAssetToHistory(tokenTicker: String, chainTicker: String)

    fun observeTransactionMetadata(transaction: Transaction): Flow<TransactionMetadata>

    fun observeSwapMetadata(): Flow<List<TransactionSwapMetadata>?>

    suspend fun getSwapMetadata(depositAddress: String): TransactionSwapMetadata?

    fun observeLastUsedAssetHistory(): Flow<Set<SimpleSwapAsset>?>

    fun delete()
}

@Suppress("TooManyFunctions")
class MetadataRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val metadataDataSource: MetadataDataSource,
    private val metadataKeyStorageProvider: MetadataKeyStorageProvider,
    private val persistableWalletProvider: PersistableWalletProvider,
    private val simpleSwapAssetProvider: SimpleSwapAssetProvider
) : MetadataRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val mutex = Mutex()

    @Suppress("TooGenericExceptionCaught")
    @OptIn(ExperimentalCoroutinesApi::class)
    private val metadata =
        accountDataSource
            .selectedAccount
            .distinctUntilChangedBy { it?.sdkAccount?.accountUuid }
            .map { getMetadataKey(it ?: return@map null) }
            .distinctUntilChanged()
            .flatMapLatest { if (it == null) flowOf(null) else metadataDataSource.observe(it) }
            .catch {
                // do nothing
            }.shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(0, 0),
                replay = 1
            )

    override suspend fun flipTxBookmark(txId: String) =
        updateMetadataAndAwait {
            metadataDataSource.flipTxAsBookmarked(txId = txId, key = it)
        }

    override suspend fun createOrUpdateTxNote(txId: String, note: String) =
        updateMetadataAndAwait {
            metadataDataSource.createOrUpdateTxNote(txId = txId, key = it, note = note)
        }

    override suspend fun deleteTxNote(txId: String) =
        updateMetadataAndAwait {
            metadataDataSource.deleteTxNote(txId = txId, key = it)
        }

    override fun markTxMemoAsRead(txId: String) =
        updateMetadata {
            metadataDataSource.markTxMemoAsRead(txId = txId, key = it)
        }

    override suspend fun markTxAsSwap(
        depositAddress: String,
        provider: String,
        origin: SwapAsset,
        destination: SwapAsset,
        totalFees: Zatoshi,
        totalFeesUsd: BigDecimal,
        amountOutFormatted: BigDecimal,
        mode: SwapMode,
        status: SwapStatus,
    ) = updateMetadataAndAwait {
        metadataDataSource.markTxAsSwap(
            depositAddress = depositAddress,
            provider = provider,
            totalFees = totalFees,
            totalFeesUsd = totalFeesUsd,
            amountOutFormatted = amountOutFormatted,
            key = it,
            origin =
                simpleSwapAssetProvider
                    .get(tokenTicker = origin.tokenTicker, chainTicker = origin.chainTicker),
            destination =
                simpleSwapAssetProvider
                    .get(tokenTicker = destination.tokenTicker, chainTicker = destination.chainTicker),
            mode = mode,
            status = status,
        )
    }

    override fun updateSwap(
        depositAddress: String,
        amountOutFormatted: BigDecimal,
        status: SwapStatus,
        mode: SwapMode,
        origin: SwapAsset,
        destination: SwapAsset,
    ) = updateMetadata {
        metadataDataSource.updateSwap(
            depositAddress = depositAddress,
            amountOutFormatted = amountOutFormatted,
            status = status,
            mode = mode,
            origin =
                simpleSwapAssetProvider
                    .get(tokenTicker = origin.tokenTicker, chainTicker = origin.chainTicker),
            destination =
                simpleSwapAssetProvider
                    .get(tokenTicker = destination.tokenTicker, chainTicker = destination.chainTicker),
            key = it
        )
    }

    // override fun deleteSwap(depositAddress: String) =
    //     updateMetadata {
    //         metadataDataSource.deleteSwap(depositAddress = depositAddress, key = it)
    //     }

    override fun addSwapAssetToHistory(tokenTicker: String, chainTicker: String) =
        updateMetadata {
            metadataDataSource.addSwapAssetToHistory(tokenTicker = tokenTicker, chainTicker = chainTicker, key = it)
        }

    override fun observeTransactionMetadata(transaction: Transaction): Flow<TransactionMetadata> {
        val txId = transaction.id.txIdString()
        val depositAddress = transaction.recipient

        return metadata
            .filterNotNull()
            .map { metadata ->
                val accountMetadata = metadata.accountMetadata
                val swapMetadata =
                    if (depositAddress != null) {
                        accountMetadata.swaps.swapIds.find { it.depositAddress == depositAddress }
                    } else {
                        null
                    }
                TransactionMetadata(
                    isBookmarked = accountMetadata.bookmarked.find { it.txId == txId }?.isBookmarked == true,
                    isRead = accountMetadata.read.any { it == txId },
                    note = accountMetadata.annotations.find { it.txId == txId }?.content,
                    swapMetadata = swapMetadata?.toBusinessObject()
                )
            }.distinctUntilChanged()
    }

    private fun SwapMetadataV3.toBusinessObject(): TransactionSwapMetadata {
        val origin = simpleSwapAssetProvider.get(tokenTicker = fromAsset.token, chainTicker = fromAsset.chain)
        return TransactionSwapMetadata(
            depositAddress = depositAddress,
            lastUpdated = lastUpdated,
            origin = origin,
            destination =
                toAsset.let {
                    simpleSwapAssetProvider.get(tokenTicker = it.token, chainTicker = it.chain)
                },
            mode =
                if (origin is ZecSimpleSwapAsset) {
                    when (exactInput) {
                        true -> SwapMode.EXACT_INPUT
                        false -> SwapMode.EXACT_OUTPUT
                        null -> SwapMode.EXACT_INPUT
                    }
                } else {
                    SwapMode.FLEX_INPUT
                },
            status = status ?: SwapStatus.SUCCESS,
            amountOutFormatted = amountOutFormatted ?: BigDecimal(0),
            provider = provider,
            totalFees = totalFees,
            totalFeesUsd = totalFeesUsd,
        )
    }

    override fun observeSwapMetadata(): Flow<List<TransactionSwapMetadata>?> =
        metadata
            .map { metadata ->
                metadata
                    ?.accountMetadata
                    ?.swaps
                    ?.swapIds
                    ?.map { it.toBusinessObject() }
            }.distinctUntilChanged()

    override suspend fun getSwapMetadata(depositAddress: String): TransactionSwapMetadata? =
        metadata
            .filterNotNull()
            .first()
            .accountMetadata
            .swaps
            .swapIds
            .firstOrNull { it.depositAddress == depositAddress }
            ?.toBusinessObject()

    override fun observeLastUsedAssetHistory(): Flow<Set<SimpleSwapAsset>?> =
        metadata
            .map {
                it
                    ?.accountMetadata
                    ?.swaps
                    ?.lastUsedAssetHistory
                    ?.toSimpleAssetSet()
            }.distinctUntilChanged()

    @Suppress("TooGenericExceptionCaught")
    override fun delete() {
        scope.launch {
            mutex.withLock {
                accountDataSource.getAllAccounts().forEach {
                    try {
                        val key = getMetadataKey(it)
                        metadataDataSource.delete(key)
                    } catch (e: Exception) {
                        Twig.error(e) { "Unable to delete Metadata" }
                    }
                }
            }
        }
    }

    private fun updateMetadata(block: suspend (MetadataKey) -> Boolean) {
        scope.launch { updateMetadataLocked(block) }
    }

    private suspend fun updateMetadataAndAwait(block: suspend (MetadataKey) -> Boolean): Boolean =
        scope.async { updateMetadataLocked(block) }.await()

    /**
     * Runs [block] for the selected account's key and returns whether the change was saved,
     * logging a failure at error.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun updateMetadataLocked(block: suspend (MetadataKey) -> Boolean): Boolean =
        mutex.withLock {
            try {
                val selectedAccount = accountDataSource.getSelectedAccount()
                val key = getMetadataKey(selectedAccount)
                block(key).also { isSaved ->
                    if (!isSaved) Twig.error { "Unable to update Metadata, the change was not saved" }
                }
            } catch (e: Exception) {
                Twig.error(e) { "Unable to update Metadata" }
                false
            }
        }

    private suspend fun getMetadataKey(selectedAccount: WalletAccount): MetadataKey {
        val key = metadataKeyStorageProvider.get(selectedAccount.sdkAccount.accountUuid)

        return if (key != null) {
            key
        } else {
            val persistableWallet = persistableWalletProvider.requirePersistableWallet()
            val zashiAccount = accountDataSource.getZashiAccount()
            val newKey =
                MetadataKey.derive(
                    seedPhrase = persistableWallet.seedPhrase,
                    network = persistableWallet.network,
                    zashiAccount = zashiAccount,
                    ufvk =
                        when (selectedAccount) {
                            is KeystoneAccount -> selectedAccount.sdkAccount.ufvk
                            is LedgerAccount -> selectedAccount.sdkAccount.ufvk
                            is ZashiAccount -> null
                        }
                )
            metadataKeyStorageProvider.store(selectedAccount.sdkAccount.accountUuid, newKey)
            newKey
        }
    }

    private fun Set<String>.toSimpleAssetSet() =
        this
            .map {
                val data = it.split(":")
                simpleSwapAssetProvider.get(tokenTicker = data[0], chainTicker = data[1])
            }.toSet()
}

data class TransactionMetadata(
    val isBookmarked: Boolean,
    val isRead: Boolean,
    val note: String?,
    val swapMetadata: TransactionSwapMetadata?
)

data class TransactionSwapMetadata(
    val depositAddress: String,
    val provider: String,
    val totalFees: Zatoshi,
    val totalFeesUsd: BigDecimal,
    val lastUpdated: Instant,
    val origin: SimpleSwapAsset,
    val destination: SimpleSwapAsset,
    val mode: SwapMode,
    val status: SwapStatus,
    val amountOutFormatted: BigDecimal,
)
