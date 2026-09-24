package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.AnnotationMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import co.electriccoin.zcash.ui.common.provider.MetadataProvider
import co.electriccoin.zcash.ui.common.provider.MetadataStorageProvider
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.util.SecretBytes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A hardware-wallet account can have more than one metadata file on disk (MOB-2039): these
 * verify that [MetadataDataSourceImpl] merges every existing file's contents back into the
 * canonical one instead of silently reading only whichever one the canonical name happens to
 * point at.
 */
class MetadataDataSourceTest {
    private val key = metadataKey(0, 1)
    private val canonicalFile = mockk<File>(relaxed = true)
    private val legacyFile = mockk<File>(relaxed = true)
    private val metadataStorageProvider =
        mockk<MetadataStorageProvider> {
            every { getOrCreateStorageFile(key) } returns canonicalFile
        }
    private val metadataProvider = mockk<MetadataProvider>(relaxed = true)

    @Test
    fun anEmptyCanonicalFileMergedWithAPopulatedLegacyFileKeepsTheLegacyEntries() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                emptyMetadata(Instant.ofEpochSecond(1))
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                annotatedMetadata(txId = "tx1", content = "legacy note", lastUpdated = Instant.ofEpochSecond(2))

            val result = dataSource().observe(key).first { it != null }

            assertEquals(listOf("tx1"), result?.accountMetadata?.annotations?.map { it.txId })
            assertEquals(
                "legacy note",
                result
                    ?.accountMetadata
                    ?.annotations
                    ?.first()
                    ?.content
            )
        }

    @Test
    fun theNewerEntryWinsOnAnOverlappingTxId() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                annotatedMetadata(txId = "tx1", content = "old note", lastUpdated = Instant.ofEpochSecond(1))
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                annotatedMetadata(txId = "tx1", content = "new note", lastUpdated = Instant.ofEpochSecond(2))

            val result = dataSource().observe(key).first { it != null }

            assertEquals(listOf("new note"), result?.accountMetadata?.annotations?.map { it.content })
        }

    @Test
    fun onlyTheCanonicalFileRemainsAfterAMerge() =
        runTest {
            every { metadataStorageProvider.getStorageFiles(key) } returns listOf(canonicalFile, legacyFile)
            every { metadataProvider.readMetadataFromFile(canonicalFile, key) } returns
                emptyMetadata(Instant.ofEpochSecond(1))
            every { metadataProvider.readMetadataFromFile(legacyFile, key) } returns
                annotatedMetadata(txId = "tx1", content = "legacy note", lastUpdated = Instant.ofEpochSecond(2))

            dataSource().observe(key).first { it != null }

            verify(exactly = 1) { metadataProvider.writeMetadataToFile(eq(canonicalFile), any(), eq(key)) }
            verify(exactly = 1) { legacyFile.delete() }
            verify(exactly = 0) { canonicalFile.delete() }
        }

    private fun dataSource() =
        MetadataDataSourceImpl(
            metadataStorageProvider = metadataStorageProvider,
            metadataProvider = metadataProvider,
            simpleSwapAssetProvider = mockk<SimpleSwapAssetProvider>()
        )

    private fun emptyMetadata(lastUpdated: Instant) =
        MetadataV3(
            lastUpdated = lastUpdated,
            accountMetadata =
                AccountMetadataV3(
                    bookmarked = emptyList(),
                    read = emptyList(),
                    annotations = emptyList(),
                    swaps = SwapsMetadataV3(swapIds = emptyList(), lastUsedAssetHistory = emptySet())
                )
        )

    private fun annotatedMetadata(txId: String, content: String, lastUpdated: Instant) =
        emptyMetadata(lastUpdated).let { metadata ->
            metadata.copy(
                accountMetadata =
                    metadata.accountMetadata.copy(
                        annotations =
                            listOf(AnnotationMetadataV3(txId = txId, content = content, lastUpdated = lastUpdated))
                    )
            )
        }

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
