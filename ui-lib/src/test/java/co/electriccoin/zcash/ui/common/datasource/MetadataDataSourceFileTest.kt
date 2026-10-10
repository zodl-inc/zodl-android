package co.electriccoin.zcash.ui.common.datasource

import android.content.Context
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.AnnotationMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.BookmarkMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import co.electriccoin.zcash.ui.common.provider.MetadataProviderImpl
import co.electriccoin.zcash.ui.common.provider.MetadataStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataEncryptorImpl
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataSerializer
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [MetadataDataSourceImpl] end to end over real files: the real storage provider over a temporary
 * directory, the real provider and the real encryptor, with a hardware-wallet style key that has
 * more than one entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetadataDataSourceFileTest {
    private val filesDir = createTempDirectory("metadata-data-source-file-test").toFile()
    private val metadataDir = File(filesDir, "metadata")
    private val key = metadataKey(0, 1)
    private val canonicalName = key.fileIdentifiers()[0]
    private val legacyName = key.fileIdentifiers()[1]
    private val storageProvider =
        MetadataStorageProviderImpl(
            context =
                mockk<Context> {
                    every { filesDir } returns this@MetadataDataSourceFileTest.filesDir
                }
        )
    private val metadataProvider =
        MetadataProviderImpl(
            metadataEncryptor = MetadataEncryptorImpl(MetadataSerializer()),
            directorySync = {}
        )
    private val swapAssets = mutableMapOf<String, SimpleSwapAsset>()
    private val simpleSwapAssetProvider =
        mockk<SimpleSwapAssetProvider> {
            every { get(any(), any()) } answers { swapAsset(firstArg(), secondArg()) }
        }

    @AfterTest
    fun deleteTempDir() {
        filesDir.deleteRecursively()
    }

    @Test
    fun aLegacyFileUnderALaterKeyIsMergedIntoTheCanonicalFileAndDeleted() =
        runTest {
            write(canonicalName, metadata(lastUpdated = 1, annotations = listOf(note("tx1", "canonical", 1))))
            write(legacyName, metadata(lastUpdated = 2, bookmarked = listOf(bookmark("tx2", 2))), metadataKey(1))

            val result = dataSource().observe(key).first { it != null }

            val expected =
                metadata(
                    lastUpdated = 2,
                    annotations = listOf(note("tx1", "canonical", 1)),
                    bookmarked = listOf(bookmark("tx2", 2))
                )
            assertEquals(expected, result)
            assertEquals(listOf(canonicalName), fileNames())
            assertEquals(expected, read(canonicalName))
            assertEquals(listOf(File(metadataDir, canonicalName)), storageProvider.getStorageFiles(key))
            assertEquals(expected, dataSource().observe(key).first { it != null })
        }

    @Test
    fun anUndecodableCanonicalFileIsSetAsideThenAFreshOneIsWrittenAndTheOldOneMergedBack() =
        runTest {
            val otherKey = metadataKey(9)
            write(canonicalName, metadata(lastUpdated = 1, read = listOf("old")), otherKey)
            val dataSource = dataSource()
            val emitted = collectEmissions(dataSource, key)

            assertEquals(listOf(emptyList()), emitted.map { it?.accountMetadata?.read })
            val setAsideName = fileNames().single()
            assertTrue(setAsideName.startsWith("$canonicalName.undecodable-"))

            dataSource.markTxMemoAsRead("new", key)
            runCurrent()

            assertEquals(listOf(listOf("new")), emitted.drop(1).map { it?.accountMetadata?.read })
            assertEquals(listOf(canonicalName, setAsideName), fileNames())
            assertEquals(listOf("new"), read(canonicalName).accountMetadata.read)

            val keyThatDecodesTheOldFile = metadataKey(0, 1, 9)
            val merged = dataSource.observe(keyThatDecodesTheOldFile).first { it != null }

            assertEquals(setOf("old", "new"), merged?.accountMetadata?.read?.toSet())
            assertEquals(listOf(canonicalName), fileNames())
        }

    @Test
    fun deleteWipesEveryFileOfTheKeyAndEmitsNull() =
        runTest {
            write(canonicalName, metadata(lastUpdated = 1))
            val dataSource = dataSource()
            val emitted = collectEmissions(dataSource, key)
            write(legacyName, metadata(lastUpdated = 1), metadataKey(1))
            File(metadataDir, "$canonicalName.undecodable-5").writeText("aside")
            File(metadataDir, "$canonicalName.tmp").writeText("partial")
            File(metadataDir, "unrelated").writeText("other")

            dataSource.delete(key)
            runCurrent()

            assertEquals(listOf("unrelated"), fileNames())
            assertNull(emitted.last())
        }

    @Test
    fun flippingABookmarkTwiceKeepsAnUnbookmarkedEntry() =
        runTest {
            val dataSource = dataSource()

            dataSource.flipTxAsBookmarked("tx1", key)
            assertEquals(listOf(true), read(canonicalName).accountMetadata.bookmarked.map { it.isBookmarked })

            dataSource.flipTxAsBookmarked("tx1", key)
            val bookmarks = read(canonicalName).accountMetadata.bookmarked
            assertEquals(listOf("tx1" to false), bookmarks.map { it.txId to it.isBookmarked })
        }

    @Test
    fun aNoteIsCreatedUpdatedAndDeletedInPlace() =
        runTest {
            val dataSource = dataSource()

            dataSource.createOrUpdateTxNote("tx1", "first", key)
            dataSource.createOrUpdateTxNote("tx1", "second", key)
            assertEquals(listOf("tx1" to "second"), notes())

            dataSource.deleteTxNote("tx1", key)
            assertEquals(listOf("tx1" to null), notes())
        }

    @Test
    fun markingAMemoAsReadTwiceKeepsOneEntry() =
        runTest {
            val dataSource = dataSource()

            dataSource.markTxMemoAsRead("tx1", key)
            dataSource.markTxMemoAsRead("tx1", key)

            assertEquals(listOf("tx1"), read(canonicalName).accountMetadata.read)
        }

    @Test
    fun aSwapIsAddedReplacedAndUpdatedByDepositAddress() =
        runTest {
            val dataSource = dataSource()

            markSwap(dataSource, provider = "first", mode = SwapMode.EXACT_OUTPUT)
            assertEquals(
                listOf(false),
                storedSwaps().swapIds.map { it.exactInput }
            )
            markSwap(dataSource, provider = "second", mode = SwapMode.EXACT_INPUT)
            val markedAt = storedSwaps().swapIds.single().lastUpdated
            dataSource.updateSwap(
                depositAddress = "deposit",
                amountOutFormatted = BigDecimal.TEN,
                status = SwapStatus.SUCCESS,
                mode = SwapMode.EXACT_OUTPUT,
                origin = swapAsset("zec", "zec"),
                destination = swapAsset("btc", "btc"),
                key = key
            )
            dataSource.updateSwap(
                depositAddress = "unknown",
                amountOutFormatted = BigDecimal.ONE,
                status = SwapStatus.FAILED,
                mode = SwapMode.EXACT_INPUT,
                origin = swapAsset("zec", "zec"),
                destination = swapAsset("btc", "btc"),
                key = key
            )

            val swap = storedSwaps().swapIds.single()
            assertEquals(markedAt, swap.lastUpdated)
            assertEquals("deposit", swap.depositAddress)
            assertEquals("second", swap.provider)
            assertEquals(SwapStatus.SUCCESS, swap.status)
            assertEquals(BigDecimal.TEN, swap.amountOutFormatted)
            assertEquals(false, swap.exactInput)
            assertEquals("btc", swap.toAsset.token)
        }

    @Test
    fun theSwapAssetHistoryPutsTheLatestFirstAndKeepsTen() =
        runTest {
            val dataSource = dataSource()

            (0..10).forEach { dataSource.addSwapAssetToHistory("token$it", "chain", key) }
            dataSource.addSwapAssetToHistory("token5", "chain", key)

            val expected = listOf(5, 10, 9, 8, 7, 6, 4, 3, 2, 1).map { "token$it:chain" }
            assertEquals(expected, storedSwaps().lastUsedAssetHistory.toList())
        }

    @Test
    fun anObserverIgnoresUpdatesForOtherKeys() =
        runTest {
            val sameSizeKey = metadataKey(2, 3)
            val shorterKey = metadataKey(4)
            val dataSource = dataSource()
            val emitted = collectEmissions(dataSource, key)

            dataSource.markTxMemoAsRead("other", sameSizeKey)
            dataSource.markTxMemoAsRead("shorter", shorterKey)
            dataSource.markTxMemoAsRead("own", key)
            runCurrent()

            assertEquals(listOf(emptyList(), listOf("own")), emitted.map { it?.accountMetadata?.read })
        }

    @Test
    fun aFailedReadNeverDeletesTheFile() =
        runTest {
            File(metadataDir.apply { mkdirs() }, legacyName).writeText("not encrypted metadata")

            dataSource().observe(key).first { it != null }

            assertEquals("not encrypted metadata", File(metadataDir, legacyName).readText())
            assertFalse(File(metadataDir, canonicalName).length() > 0)
        }

    private fun TestScope.dataSource() =
        MetadataDataSourceImpl(
            metadataStorageProvider = storageProvider,
            metadataProvider = metadataProvider,
            simpleSwapAssetProvider = simpleSwapAssetProvider,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

    /**
     * Collects everything [dataSource] emits for [key] after the initial null and runs the
     * collector until it waits for updates.
     */
    private fun TestScope.collectEmissions(dataSource: MetadataDataSourceImpl, key: MetadataKey): List<MetadataV3?> {
        val emitted = mutableListOf<MetadataV3?>()
        backgroundScope.launch {
            dataSource.observe(key).drop(1).collect { emitted += it }
        }
        runCurrent()
        return emitted
    }

    private suspend fun markSwap(dataSource: MetadataDataSourceImpl, provider: String, mode: SwapMode) =
        dataSource.markTxAsSwap(
            depositAddress = "deposit",
            provider = provider,
            origin = swapAsset("zec", "zec"),
            destination = swapAsset("usdc", "near"),
            totalFees = Zatoshi(1),
            totalFeesUsd = BigDecimal.ONE,
            mode = mode,
            amountOutFormatted = BigDecimal.ONE,
            status = SwapStatus.PENDING,
            key = key
        )

    private fun storedSwaps() = read(canonicalName).accountMetadata.swaps

    private fun notes() = read(canonicalName).accountMetadata.annotations.map { it.txId to it.content }

    private fun fileNames() = metadataDir.list().orEmpty().sorted()

    private fun write(name: String, metadata: MetadataV3, writeKey: MetadataKey = key) {
        metadataDir.mkdirs()
        metadataProvider.writeMetadataToFile(File(metadataDir, name), metadata, writeKey)
    }

    private fun read(name: String) = metadataProvider.readMetadataFromFile(File(metadataDir, name), key)

    private fun swapAsset(token: String, chain: String) =
        swapAssets.getOrPut("$token:$chain") {
            mockk<SimpleSwapAsset> {
                every { tokenTicker } returns token
                every { chainTicker } returns chain
            }
        }

    private fun metadata(
        lastUpdated: Long,
        bookmarked: List<BookmarkMetadataV3> = emptyList(),
        read: List<String> = emptyList(),
        annotations: List<AnnotationMetadataV3> = emptyList(),
    ) = MetadataV3(
        lastUpdated = Instant.ofEpochSecond(lastUpdated),
        accountMetadata =
            AccountMetadataV3(
                bookmarked = bookmarked,
                read = read,
                annotations = annotations,
                swaps = SwapsMetadataV3(swapIds = emptyList(), lastUsedAssetHistory = emptySet())
            )
    )

    private fun note(txId: String, content: String, lastUpdated: Long) =
        AnnotationMetadataV3(txId = txId, content = content, lastUpdated = Instant.ofEpochSecond(lastUpdated))

    private fun bookmark(txId: String, lastUpdated: Long) =
        BookmarkMetadataV3(txId = txId, lastUpdated = Instant.ofEpochSecond(lastUpdated), isBookmarked = true)

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
