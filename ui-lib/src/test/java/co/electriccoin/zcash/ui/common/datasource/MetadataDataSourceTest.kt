package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
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
import co.electriccoin.zcash.ui.common.serialization.metadata.DecryptionException
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.NoSuchFileException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A hardware-wallet account can have more than one metadata file on disk (MOB-2039): these
 * verify that [MetadataDataSourceImpl] merges every existing file's contents back into the
 * canonical one instead of silently reading only whichever one the canonical name happens to
 * point at, that the merge resolves every conflict by the newer timestamp, and that no file is
 * ever lost to a failed write, an undecodable read or a failed listing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class MetadataDataSourceTest {
    private val key = metadataKey(0, 1)
    private val canonicalFile = mockk<File>(relaxed = true)
    private val legacyFile = mockk<File>(relaxed = true)
    private val metadataStorageProvider =
        mockk<MetadataStorageProvider> {
            every { getOrCreateStorageFile(key) } returns canonicalFile
            every { sizeOf(any()) } returns 1L
        }
    private val metadataProvider = mockk<MetadataProvider>(relaxed = true)

    @Test
    fun anEmptyCanonicalFileMergedWithAPopulatedLegacyFileKeepsTheLegacyEntries() =
        runTest {
            val result =
                merge(
                    canonical = metadata(lastUpdated = 1),
                    legacy = metadata(lastUpdated = 2, annotations = listOf(annotation("tx1", "legacy note", 2)))
                )

            assertEquals(listOf(annotation("tx1", "legacy note", 2)), result.accountMetadata.annotations)
        }

    @Test
    fun theNewerEntryWinsOnAnOverlappingTxId() =
        runTest {
            val result =
                merge(
                    canonical = metadata(lastUpdated = 1, annotations = listOf(annotation("tx1", "old note", 1))),
                    legacy = metadata(lastUpdated = 2, annotations = listOf(annotation("tx1", "new note", 2)))
                )

            assertEquals(listOf("new note"), result.accountMetadata.annotations.map { it.content })
        }

    @Test
    fun onlyTheCanonicalFileRemainsAfterAMerge() =
        runTest {
            val written = slot<MetadataV3>()
            every { metadataProvider.writeMetadataToFile(canonicalFile, capture(written), key) } just Runs

            val result =
                merge(
                    canonical = metadata(lastUpdated = 1, read = listOf("r1")),
                    legacy = metadata(lastUpdated = 2, annotations = listOf(annotation("tx1", "legacy note", 2)))
                )

            val expected =
                metadata(
                    lastUpdated = 2,
                    read = listOf("r1"),
                    annotations = listOf(annotation("tx1", "legacy note", 2))
                )
            assertEquals(expected, written.captured)
            assertEquals(expected, result)
            verify(exactly = 1) { metadataProvider.writeMetadataToFile(canonicalFile, any(), key) }
            verify(exactly = 1) { legacyFile.delete() }
            verify(exactly = 0) { canonicalFile.delete() }
        }

    @Test
    fun bookmarksResolveNewestWinsIncludingAnUnbookmarkTombstone() =
        runTest {
            val result =
                merge(
                    canonical =
                        metadata(
                            lastUpdated = 1,
                            bookmarked = listOf(bookmark("tx1", true, 1), bookmark("tx2", true, 5))
                        ),
                    legacy =
                        metadata(
                            lastUpdated = 2,
                            bookmarked = listOf(bookmark("tx1", false, 2), bookmark("tx2", false, 3))
                        )
                )

            assertEquals(
                listOf(bookmark("tx1", false, 2), bookmark("tx2", true, 5)),
                result.accountMetadata.bookmarked
            )
        }

    @Test
    fun notesResolveNewestWinsIncludingADeleteTombstoneWithoutConcatenating() =
        runTest {
            val result =
                merge(
                    canonical =
                        metadata(
                            lastUpdated = 1,
                            annotations = listOf(annotation("tx1", "deleted later", 1), annotation("tx2", "kept", 5))
                        ),
                    legacy =
                        metadata(
                            lastUpdated = 2,
                            annotations = listOf(annotation("tx1", null, 2), annotation("tx2", "stale", 3))
                        )
                )

            assertEquals(
                listOf(annotation("tx1", null, 2), annotation("tx2", "kept", 5)),
                result.accountMetadata.annotations
            )
        }

    @Test
    fun readEntriesAreUnioned() =
        runTest {
            val result =
                merge(
                    canonical = metadata(lastUpdated = 1, read = listOf("r1", "r2")),
                    legacy = metadata(lastUpdated = 2, read = listOf("r2", "r3"))
                )

            assertEquals(setOf("r1", "r2", "r3"), result.accountMetadata.read.toSet())
            assertEquals(3, result.accountMetadata.read.size)
        }

    @Test
    fun swapIdsResolveNewestWins() =
        runTest {
            val result =
                merge(
                    canonical =
                        metadata(
                            lastUpdated = 1,
                            swapIds = listOf(swap("s1", "canonical", 1), swap("s2", "canonical", 5))
                        ),
                    legacy =
                        metadata(
                            lastUpdated = 2,
                            swapIds = listOf(swap("s1", "legacy", 2), swap("s2", "legacy", 3))
                        )
                )

            assertEquals(
                listOf(swap("s1", "legacy", 2), swap("s2", "canonical", 5)),
                result.accountMetadata.swaps.swapIds
            )
        }

    @Test
    fun assetHistoryComesWholeFromTheNewerFileCappedAtTenInOrder() =
        runTest {
            val legacyHistory = (11 downTo 0).map { "token$it:chain" }.toSet()
            val result =
                merge(
                    canonical = metadata(lastUpdated = 1, history = setOf("old:chain", "token3:chain")),
                    legacy = metadata(lastUpdated = 2, history = legacyHistory)
                )

            assertEquals(legacyHistory.take(10), result.history())
        }

    @Test
    fun assetHistoryOfAnOlderLegacyFileIsDropped() =
        runTest {
            val result =
                merge(
                    canonical = metadata(lastUpdated = 2, history = setOf("a:chain", "b:chain")),
                    legacy = metadata(lastUpdated = 1, history = setOf("c:chain", "a:chain"))
                )

            assertEquals(listOf("a:chain", "b:chain"), result.history())
        }

    @Test
    fun tiesKeepTheCanonicalSide() =
        runTest {
            val result =
                merge(
                    canonical =
                        metadata(
                            lastUpdated = 3,
                            bookmarked = listOf(bookmark("tx1", true, 3)),
                            annotations = listOf(annotation("tx1", "canonical", 3)),
                            swapIds = listOf(swap("s1", "canonical", 3)),
                            history = setOf("canonical:chain")
                        ),
                    legacy =
                        metadata(
                            lastUpdated = 3,
                            bookmarked = listOf(bookmark("tx1", false, 3)),
                            annotations = listOf(annotation("tx1", "legacy", 3)),
                            swapIds = listOf(swap("s1", "legacy", 3)),
                            history = setOf("legacy:chain")
                        )
                )

            assertEquals(
                metadata(
                    lastUpdated = 3,
                    bookmarked = listOf(bookmark("tx1", true, 3)),
                    annotations = listOf(annotation("tx1", "canonical", 3)),
                    swapIds = listOf(swap("s1", "canonical", 3)),
                    history = setOf("canonical:chain")
                ),
                result
            )
        }

    @Test
    fun duplicateIdsWithinOneFileResolveNewestWins() =
        runTest {
            val result =
                merge(
                    canonical =
                        metadata(
                            lastUpdated = 1,
                            bookmarked = listOf(bookmark("tx1", true, 5), bookmark("tx1", false, 1)),
                            annotations = listOf(annotation("tx1", "newer", 5), annotation("tx1", "older", 1)),
                            swapIds = listOf(swap("s1", "newer", 5), swap("s1", "older", 1))
                        ),
                    legacy = metadata(lastUpdated = 2)
                )

            assertEquals(listOf(bookmark("tx1", true, 5)), result.accountMetadata.bookmarked)
            assertEquals(listOf(annotation("tx1", "newer", 5)), result.accountMetadata.annotations)
            assertEquals(listOf(swap("s1", "newer", 5)), result.accountMetadata.swaps.swapIds)
        }

    @Test
    fun theMergedTopLevelTimestampIsTheNewerOne() =
        runTest {
            val result = merge(canonical = metadata(lastUpdated = 7), legacy = metadata(lastUpdated = 4))

            assertEquals(Instant.ofEpochSecond(7), result.lastUpdated)
        }

    @Test
    fun aFailedMergeWriteDeletesNothing() =
        runTest {
            every { metadataProvider.writeMetadataToFile(any(), any(), any()) } throws IOException("disk full")

            val result =
                merge(canonical = metadata(lastUpdated = 1), legacy = metadata(lastUpdated = 2, read = listOf("r1")))

            assertEquals(metadata(lastUpdated = 2, read = listOf("r1")), result)
            verify(exactly = 1) { metadataProvider.writeMetadataToFile(canonicalFile, any(), key) }
            verify(exactly = 0) { legacyFile.delete() }
            verify(exactly = 0) { canonicalFile.delete() }
        }

    @Test
    fun anUndecodableLegacyFileIsKeptAndDoesNotTriggerARewrite() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } throws DecryptionException()

            val result = observe()

            assertEquals(metadata(lastUpdated = 1), result)
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { legacyFile.delete() }
        }

    @Test
    fun aLegacyFileThatLaterDecodesIsMergedThenDeleted() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } throws
                DecryptionException() andThen metadata(lastUpdated = 2, read = listOf("r1"))

            observe()
            verify(exactly = 0) { legacyFile.delete() }

            val result = observe()

            assertEquals(listOf("r1"), result.accountMetadata.read)
            verify(exactly = 1) { metadataProvider.writeMetadataToFile(canonicalFile, result, key) }
            verify(exactly = 1) { legacyFile.delete() }
        }

    @Test
    fun anUndecodableCanonicalFileIsSetAsideBeforeAnyWriteAndLaterMergedBack() =
        runTest {
            val setAsideFile = mockk<File>(relaxed = true)
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.setAsideUndecodable(canonicalFile) } returns true
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws DecryptionException()
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(lastUpdated = 2, read = listOf("r2"))

            observe()

            verifyOrder {
                metadataStorageProvider.setAsideUndecodable(canonicalFile)
                metadataProvider.writeMetadataToFile(canonicalFile, any(), key)
            }
            verify(exactly = 0) { canonicalFile.delete() }
            verify(exactly = 1) { legacyFile.delete() }

            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, setAsideFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                metadata(lastUpdated = 2, read = listOf("r2"))
            every { metadataProvider.readMetadataFromFile(setAsideFile, key) } returns
                metadata(lastUpdated = 1, read = listOf("r1"))

            val result = observe()

            assertEquals(setOf("r1", "r2"), result.accountMetadata.read.toSet())
            verify(exactly = 1) { setAsideFile.delete() }
        }

    @Test
    fun anUndecodableCanonicalFileThatCannotBeSetAsideIsNeverWrittenOver() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.setAsideUndecodable(canonicalFile) } returns false
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws DecryptionException()
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns metadata(lastUpdated = 2)

            val dataSource = dataSource()
            dataSource.observe(key).first { it != null }
            dataSource.markTxMemoAsRead("tx1", key)

            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { legacyFile.delete() }
        }

    @Test
    fun aZeroByteCanonicalFileCountsAsAbsent() =
        runTest {
            every { metadataStorageProvider.sizeOf(canonicalFile) } returns 0L
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(lastUpdated = 2, read = listOf("r1"))

            val result = observe()

            assertEquals(listOf("r1"), result.accountMetadata.read)
            verify(exactly = 0) { metadataProvider.readMetadataFromFile(canonicalFile, any()) }
            verify(exactly = 0) { metadataStorageProvider.setAsideUndecodable(any()) }
            verify(exactly = 1) { metadataProvider.writeMetadataToFile(canonicalFile, result, key) }
            verify(exactly = 1) { legacyFile.delete() }
        }

    @Test
    fun anIOExceptionIsRetriedUpToThreeAttempts() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws
                IOException() andThenThrows IOException() andThen metadata(lastUpdated = 1, read = listOf("r1"))

            val result = observe()

            assertEquals(listOf("r1"), result.accountMetadata.read)
            verify(exactly = 3) { metadataProvider.readMetadataFromFile(canonicalFile, key) }
            verify(exactly = 0) { metadataStorageProvider.setAsideUndecodable(any()) }
        }

    @Test
    fun aPersistentIOExceptionOnTheCanonicalFileIsNeitherSetAsideNorWrittenOver() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.setAsideUndecodable(canonicalFile) } returns true
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws IOException()
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(lastUpdated = 2, read = listOf("r1"))

            val dataSource = dataSource()
            val result = checkNotNull(dataSource.observe(key).first { it != null })
            dataSource.markTxMemoAsRead("tx1", key)

            assertEquals(metadata(lastUpdated = 2, read = listOf("r1")), result)
            verify(exactly = 6) { metadataProvider.readMetadataFromFile(canonicalFile, key) }
            verify(exactly = 0) { metadataStorageProvider.setAsideUndecodable(any()) }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { legacyFile.delete() }
        }

    @Test
    fun aDecryptionFailureIsNotRetried() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            every { metadataStorageProvider.setAsideUndecodable(canonicalFile) } returns true
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws DecryptionException()

            observe()

            verify(exactly = 1) { metadataProvider.readMetadataFromFile(canonicalFile, key) }
        }

    @Test
    fun aListingFailureWritesNothing() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } throws IOException()
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                metadata(lastUpdated = 1, read = listOf("r1"))

            val dataSource = dataSource()
            val result = checkNotNull(dataSource.observe(key).first { it != null })
            dataSource.markTxMemoAsRead("tx2", key)

            assertEquals(listOf("r1"), result.accountMetadata.read)
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { canonicalFile.delete() }
        }

    @Test
    fun aCanonicalFileThatCannotBeCreatedDoesNotEndTheObserveFlow() =
        runTest {
            every { metadataStorageProvider.getOrCreateStorageFile(key) } throws IOException()
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(legacyFile)
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(lastUpdated = 2, read = listOf("r1"))

            val result = observe()

            assertEquals(listOf("r1"), result.accountMetadata.read)
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { legacyFile.delete() }
        }

    @Test
    fun aSetAsideFileHoldingTheNewestEntryWinsAThreeFileMerge() =
        runTest {
            val setAsideFile = mockk<File>(relaxed = true)
            every { metadataStorageProvider.getStorageFiles(key) } returns
                listOf(canonicalFile, setAsideFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                metadata(lastUpdated = 1, annotations = listOf(annotation("tx1", "canonical", 1)))
            every { metadataProvider.readMetadataFromFile(setAsideFile, key) } returns
                metadata(lastUpdated = 3, annotations = listOf(annotation("tx1", "set aside", 3)))
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(lastUpdated = 2, annotations = listOf(annotation("tx1", "legacy", 2)))

            val result = observe()

            assertEquals(
                metadata(lastUpdated = 3, annotations = listOf(annotation("tx1", "set aside", 3))),
                result
            )
            verify(exactly = 1) { metadataProvider.writeMetadataToFile(canonicalFile, result, key) }
            verify(exactly = 1) { setAsideFile.delete() }
            verify(exactly = 1) { legacyFile.delete() }
        }

    @Test
    fun aTieBetweenASetAsideAndALegacyFileKeepsTheFirstInListingOrder() =
        runTest {
            val setAsideFile = mockk<File>(relaxed = true)
            every { metadataStorageProvider.sizeOf(canonicalFile) } returns 0L
            every { metadataStorageProvider.getStorageFiles(key) } returns
                listOf(canonicalFile, setAsideFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(setAsideFile, key) } returns
                metadata(
                    lastUpdated = 2,
                    annotations = listOf(annotation("tx1", "set aside", 2)),
                    history = setOf("aside:chain")
                )
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(
                    lastUpdated = 2,
                    annotations = listOf(annotation("tx1", "legacy", 2)),
                    history = setOf("legacy:chain")
                )

            val result = observe()

            assertEquals(listOf(annotation("tx1", "set aside", 2)), result.accountMetadata.annotations)
            assertEquals(listOf("aside:chain"), result.history())
        }

    @Test
    fun updateSwapKeepsTheSwapEntryTimestamp() =
        runTest {
            val written = slot<MetadataV3>()
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                metadata(lastUpdated = 1, swapIds = listOf(swap("s1", "near", 5)))
            every { metadataProvider.writeMetadataToFile(canonicalFile, capture(written), key) } just Runs

            dataSource().updateSwap(
                depositAddress = "s1",
                amountOutFormatted = BigDecimal.TEN,
                status = SwapStatus.SUCCESS,
                mode = SwapMode.EXACT_INPUT,
                origin = swapAsset(),
                destination = swapAsset(),
                key = key
            )

            val updated =
                written.captured.accountMetadata.swaps.swapIds
                    .single()
            assertEquals(SwapStatus.SUCCESS, updated.status)
            assertEquals(Instant.ofEpochSecond(5), updated.lastUpdated)
        }

    @Test
    fun aFailedUpdateWriteEmitsNothing() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)
            val dataSource = dataSource()
            val emitted = collectEmissions(dataSource)

            every { metadataProvider.writeMetadataToFile(any(), any(), any()) } throws IOException("disk full")
            dataSource.markTxMemoAsRead("failed", key)
            every { metadataProvider.writeMetadataToFile(any(), any(), any()) } just Runs
            dataSource.markTxMemoAsRead("after", key)
            runCurrent()

            assertEquals(listOf(emptyList(), listOf("after")), emitted.map { it.accountMetadata.read })
        }

    @Test
    fun aReadOnlyUpdateEmitsNothing() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)
            val dataSource = dataSource()
            val emitted = collectEmissions(dataSource)

            every { metadataStorageProvider.getStorageFiles(key) } throws IOException()
            dataSource.markTxMemoAsRead("failed", key)
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            dataSource.markTxMemoAsRead("after", key)
            runCurrent()

            assertEquals(listOf(emptyList(), listOf("after")), emitted.map { it.accountMetadata.read })
        }

    @Test
    fun emptyNonCanonicalAndSetAsideFilesAreDeletedWithoutARewrite() =
        runTest {
            val emptyLegacyFile = mockk<File>(relaxed = true)
            val emptySetAsideFile = mockk<File>(relaxed = true)
            every { metadataStorageProvider.sizeOf(emptyLegacyFile) } returns 0L
            every { metadataStorageProvider.sizeOf(emptySetAsideFile) } returns 0L
            every { metadataStorageProvider.getStorageFiles(key) } returns
                listOf(canonicalFile, emptySetAsideFile, emptyLegacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)

            val result = observe()

            assertEquals(metadata(lastUpdated = 1), result)
            verify(exactly = 1) { emptyLegacyFile.delete() }
            verify(exactly = 1) { emptySetAsideFile.delete() }
            verify(exactly = 0) { metadataProvider.readMetadataFromFile(emptyLegacyFile, any()) }
            verify(exactly = 0) { metadataProvider.readMetadataFromFile(emptySetAsideFile, any()) }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
        }

    @Test
    fun aFailedDirectoryFlushDeletesNothing() =
        runTest {
            every { metadataProvider.syncDirectoryOf(any()) } throws IOException()

            merge(canonical = metadata(lastUpdated = 1), legacy = metadata(lastUpdated = 2, read = listOf("r1")))

            verifyOrder {
                metadataProvider.writeMetadataToFile(canonicalFile, any(), key)
                metadataProvider.syncDirectoryOf(canonicalFile)
            }
            verify(exactly = 0) { legacyFile.delete() }
        }

    @Test
    fun deleteRemovesEveryFileThroughTheStorageProvider() =
        runTest {
            every { metadataStorageProvider.deleteStorageFiles(key) } just Runs
            every { metadataStorageProvider.getStorageFiles(key) } throws IOException()

            dataSource().delete(key)

            verify(exactly = 1) { metadataStorageProvider.deleteStorageFiles(key) }
        }

    @Test
    fun emptyNonCanonicalFilesAreKeptOnAReadOnlyRead() =
        runTest {
            val emptyLegacyFile = mockk<File>(relaxed = true)
            every { metadataStorageProvider.sizeOf(emptyLegacyFile) } returns 0L
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, emptyLegacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws IOException()

            observe()

            verify(exactly = 0) { emptyLegacyFile.delete() }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
        }

    @Test
    fun aMissingLegacyFileCountsAsAbsent() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.sizeOf(legacyFile) } throws NoSuchFileException("legacy")
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)

            val result = observe()

            assertEquals(metadata(lastUpdated = 1), result)
            verify(exactly = 1) { metadataStorageProvider.sizeOf(legacyFile) }
            verify(exactly = 0) { metadataProvider.readMetadataFromFile(legacyFile, any()) }
            verify(exactly = 0) { legacyFile.delete() }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
        }

    @Test
    fun aLegacyFileWhoseSizeCannotBeReadIsRetriedAndKept() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.sizeOf(legacyFile) } throws IOException()
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns metadata(lastUpdated = 1)

            observe()

            verify(exactly = 3) { metadataStorageProvider.sizeOf(legacyFile) }
            verify(exactly = 0) { metadataProvider.readMetadataFromFile(legacyFile, any()) }
            verify(exactly = 0) { legacyFile.delete() }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
        }

    @Test
    fun aCanonicalFileWhoseSizeCannotBeReadMakesTheReadReadOnly() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.sizeOf(canonicalFile) } throws IOException()
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                metadata(lastUpdated = 2, read = listOf("r1"))

            val result = observe()

            assertEquals(listOf("r1"), result.accountMetadata.read)
            verify(exactly = 0) { metadataStorageProvider.setAsideUndecodable(any()) }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { legacyFile.delete() }
        }

    @Test
    fun anErrorWhileReadingIsRethrown() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile)
            every { metadataStorageProvider.setAsideUndecodable(canonicalFile) } returns true
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws OutOfMemoryError()

            assertFailsWith<OutOfMemoryError> { observe() }
            verify(exactly = 0) { metadataStorageProvider.setAsideUndecodable(any()) }
        }

    @Test
    fun aSecurityExceptionMakesTheReadReadOnlyWithoutSettingTheFileAside() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataStorageProvider.setAsideUndecodable(canonicalFile) } returns true
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } throws SecurityException()
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns metadata(lastUpdated = 2)

            observe()

            verify(exactly = 1) { metadataProvider.readMetadataFromFile(canonicalFile, key) }
            verify(exactly = 0) { metadataStorageProvider.setAsideUndecodable(any()) }
            verify(exactly = 0) { metadataProvider.writeMetadataToFile(any(), any(), any()) }
            verify(exactly = 0) { legacyFile.delete() }
        }

    private suspend fun TestScope.observe(): MetadataV3 = checkNotNull(dataSource().observe(key).first { it != null })

    /**
     * Starts collecting [dataSource]'s non-null emissions and runs the collector until it waits for
     * updates, so every later update is either recorded or provably never emitted.
     */
    private fun TestScope.collectEmissions(dataSource: MetadataDataSourceImpl): List<MetadataV3> {
        val emitted = mutableListOf<MetadataV3>()
        backgroundScope.launch {
            dataSource.observe(key).filterNotNull().collect { emitted += it }
        }
        runCurrent()
        return emitted
    }

    private fun swapAsset() =
        mockk<SimpleSwapAsset> {
            every { tokenTicker } returns "zec"
            every { chainTicker } returns "zec"
        }

    private suspend fun TestScope.merge(canonical: MetadataV3, legacy: MetadataV3): MetadataV3 {
        every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
        every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns canonical
        every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns legacy
        return observe()
    }

    private fun MetadataV3.history() = accountMetadata.swaps.lastUsedAssetHistory.toList()

    private fun TestScope.dataSource() =
        MetadataDataSourceImpl(
            metadataStorageProvider = metadataStorageProvider,
            metadataProvider = metadataProvider,
            simpleSwapAssetProvider = mockk<SimpleSwapAssetProvider>(),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

    @Suppress("LongParameterList")
    private fun metadata(
        lastUpdated: Long,
        bookmarked: List<BookmarkMetadataV3> = emptyList(),
        read: List<String> = emptyList(),
        annotations: List<AnnotationMetadataV3> = emptyList(),
        swapIds: List<SwapMetadataV3> = emptyList(),
        history: Set<String> = emptySet()
    ) = MetadataV3(
        lastUpdated = Instant.ofEpochSecond(lastUpdated),
        accountMetadata =
            AccountMetadataV3(
                bookmarked = bookmarked,
                read = read,
                annotations = annotations,
                swaps = SwapsMetadataV3(swapIds = swapIds, lastUsedAssetHistory = history)
            )
    )

    private fun bookmark(txId: String, isBookmarked: Boolean, lastUpdated: Long) =
        BookmarkMetadataV3(txId = txId, lastUpdated = Instant.ofEpochSecond(lastUpdated), isBookmarked = isBookmarked)

    private fun annotation(txId: String, content: String?, lastUpdated: Long) =
        AnnotationMetadataV3(txId = txId, content = content, lastUpdated = Instant.ofEpochSecond(lastUpdated))

    private fun swap(depositAddress: String, provider: String, lastUpdated: Long) =
        SwapMetadataV3(
            depositAddress = depositAddress,
            provider = provider,
            totalFees = Zatoshi(0),
            totalUSDFeesInternal = BigDecimal.ZERO,
            lastUpdated = Instant.ofEpochSecond(lastUpdated),
            fromAsset = MetadataSimpleSwapAssetV3(token = "zec", chain = "zec"),
            toAsset = MetadataSimpleSwapAssetV3(token = "usdc", chain = "near"),
            exactInput = true,
            status = null,
            amountOutFormatted = null
        )

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
