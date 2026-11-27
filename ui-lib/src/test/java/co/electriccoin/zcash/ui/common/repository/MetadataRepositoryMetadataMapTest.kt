package co.electriccoin.zcash.ui.common.repository

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MetadataDataSource
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.metadata.AccountAppearanceMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import co.electriccoin.zcash.ui.common.provider.MetadataKeyStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * [MetadataRepositoryImpl]'s shared map keeping every known account's entry while a change of the
 * account set subscribes to the accounts again, and deriving each account's key once however many
 * readers and writers ask for it at the same time, while a stored key never waits on a derivation. One
 * account's failing read never costs the other accounts their entries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetadataRepositoryMetadataMapTest {
    private val first = account<ZodlAccount>(FIRST_UUID)
    private val second = account<ViewOnlyAccount>(SECOND_UUID)
    private val third = account<ViewOnlyAccount>(THIRD_UUID)
    private val keys = mapOf(FIRST_UUID to mockk<MetadataKey>(), SECOND_UUID to mockk(), THIRD_UUID to mockk())
    private val contents =
        mapOf(
            FIRST_UUID to metadata(Color(FIRST_COLOR)),
            SECOND_UUID to metadata(Color(SECOND_COLOR)),
            THIRD_UUID to metadata(Color(THIRD_COLOR))
        )
    private val accounts = MutableStateFlow<List<WalletAccount>?>(null)

    /** Keys whose read is held after the data source's leading null, as a slow re-read would be. */
    private val heldReads = MutableStateFlow(emptySet<MetadataKey>())

    private val metadataDataSource =
        mockk<MetadataDataSource>(relaxed = true) {
            every { observe(any()) } answers {
                val key = firstArg<MetadataKey>()
                flow {
                    emit(null)
                    heldReads.first { key !in it }
                    emit(contents.getValue(keys.entries.single { it.value == key }.key))
                }
            }
            coEvery { setAccountColor(any(), any()) } returns true
        }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun aGrowingAccountSetKeepsTheKnownEntries() =
        runTest {
            val repository = repository(storedKeys())
            accounts.value = listOf(first, second)
            val seen = record(repository)
            repository.metadata.first { it.size == 2 }

            heldReads.value = setOf(keys.getValue(FIRST_UUID), keys.getValue(SECOND_UUID))
            accounts.value = listOf(first, second, third)

            val grown = repository.metadata.first { THIRD_UUID in it }
            assertEquals(contentsOf(FIRST_UUID, SECOND_UUID, THIRD_UUID), grown)
            assertEveryEmissionAfterTheFirstFullMapHolds(seen, size = 2, FIRST_UUID, SECOND_UUID)
        }

    @Test
    fun aShrinkingAccountSetDropsOnlyTheRemovedEntry() =
        runTest {
            val repository = repository(storedKeys())
            accounts.value = listOf(first, second, third)
            val seen = record(repository)
            repository.metadata.first { it.size == 3 }

            heldReads.value = setOf(keys.getValue(FIRST_UUID), keys.getValue(SECOND_UUID))
            accounts.value = listOf(first, second)

            assertEquals(contentsOf(FIRST_UUID, SECOND_UUID), repository.metadata.first { THIRD_UUID !in it })
            assertEveryEmissionAfterTheFirstFullMapHolds(seen, size = 3, FIRST_UUID, SECOND_UUID)
        }

    @Test
    fun aReaderAndAWriterRacingOnOneKeyDeriveItOnce() =
        runTest {
            val stored = ConcurrentHashMap<AccountUuid, MetadataKey>()
            val stores = ConcurrentHashMap<AccountUuid, AtomicInteger>()
            val derivations = AtomicInteger()
            val keyStorage =
                mockk<MetadataKeyStorageProvider>().also { provider ->
                    coEvery { provider.get(any()) } answers { stored[firstArg<AccountUuid>()] }
                    coEvery { provider.store(any(), any()) } answers {
                        stores.getOrPut(firstArg()) { AtomicInteger() }.incrementAndGet()
                        stored[firstArg()] = secondArg()
                    }
                }
            mockkObject(MetadataKey.Companion)
            coEvery { MetadataKey.derive(any(), any(), any(), any()) } coAnswers {
                derivations.incrementAndGet()
                delay(DERIVATION_MILLIS)
                mockk()
            }
            every { metadataDataSource.observe(any()) } returns flowOf(null, metadata(null))
            val repository = repository(keyStorage)
            accounts.value = listOf(first, second)

            record(repository)
            record(repository)
            repository.setAccountColor(second, Color(SECOND_COLOR))
            repository.metadata.first { it.size == 2 }

            assertEquals(2, derivations.get())
            assertEquals(mapOf(FIRST_UUID to 1, SECOND_UUID to 1), stores.mapValues { it.value.get() })
        }

    @Test
    fun aStoredKeyIsReadWhileAnotherKeyIsBeingDerived() =
        runTest {
            val deriving = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val keyStorage =
                mockk<MetadataKeyStorageProvider>(relaxed = true).also { provider ->
                    coEvery { provider.get(FIRST_UUID) } returns keys.getValue(FIRST_UUID)
                    coEvery { provider.get(SECOND_UUID) } returns null
                }
            mockkObject(MetadataKey.Companion)
            coEvery { MetadataKey.derive(any(), any(), any(), any()) } coAnswers {
                deriving.complete(Unit)
                release.await()
                keys.getValue(SECOND_UUID)
            }
            val repository = repository(keyStorage)
            accounts.value = listOf(first)

            backgroundScope.launch { repository.setAccountColor(second, Color(SECOND_COLOR)) }
            deriving.await()
            record(repository)
            val read =
                withContext(Dispatchers.Default) {
                    withTimeout(LOCK_FREE_READ_TIMEOUT) { repository.metadata.first { FIRST_UUID in it } }
                }
            release.complete(Unit)

            assertEquals(contentsOf(FIRST_UUID), read)
        }

    @Test
    fun oneAccountsFailingReadKeepsEveryOtherEntry() =
        runTest {
            every { metadataDataSource.observe(keys.getValue(SECOND_UUID)) } returns
                flow {
                    emit(null)
                    throw IOException("unreadable")
                }
            val repository = repository(storedKeys())
            accounts.value = listOf(first, second, third)
            record(repository)

            assertEquals(
                contentsOf(FIRST_UUID, THIRD_UUID),
                repository.metadata.first { FIRST_UUID in it && THIRD_UUID in it }
            )
        }

    private fun TestScope.record(repository: MetadataRepositoryImpl): List<Map<AccountUuid, MetadataV3>> {
        val seen = Collections.synchronizedList(mutableListOf<Map<AccountUuid, MetadataV3>>())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.metadata.collect { seen += it } }
        return seen
    }

    /** Every map from the first one of [size] entries on holds [uuids] with their content. */
    private fun assertEveryEmissionAfterTheFirstFullMapHolds(
        seen: List<Map<AccountUuid, MetadataV3>>,
        size: Int,
        vararg uuids: AccountUuid
    ) {
        val emissions = synchronized(seen) { seen.dropWhile { it.size < size } }
        assertTrue(emissions.isNotEmpty())
        emissions.forEach { map ->
            uuids.forEach { uuid -> assertEquals(contents.getValue(uuid), map[uuid], "$map lacks $uuid") }
        }
    }

    private fun contentsOf(vararg uuids: AccountUuid) = uuids.associateWith { contents.getValue(it) }

    private fun storedKeys() =
        mockk<MetadataKeyStorageProvider>().also { provider ->
            coEvery { provider.get(any()) } answers { keys.getValue(firstArg()) }
        }

    private fun repository(keyStorage: MetadataKeyStorageProvider) =
        MetadataRepositoryImpl(
            accountDataSource =
                mockk<AccountDataSource> {
                    every { selectedAccount } returns flowOf(first)
                    every { allAccounts } returns accounts
                    coEvery { getSelectedAccount() } returns first
                    coEvery { getZashiAccount() } returns first
                },
            metadataDataSource = metadataDataSource,
            metadataKeyStorageProvider = keyStorage,
            persistableWalletProvider =
                mockk<PersistableWalletProvider> {
                    coEvery { requirePersistableWallet() } returns mockk(relaxed = true)
                },
            simpleSwapAssetProvider = mockk(relaxed = true)
        )

    private inline fun <reified T : WalletAccount> account(uuid: AccountUuid) =
        mockk<T> { every { sdkAccount } returns Account.new(uuid) }

    private fun metadata(color: Color?) =
        MetadataV3(
            lastUpdated = Instant.ofEpochSecond(1),
            accountMetadata =
                AccountMetadataV3(
                    bookmarked = emptyList(),
                    read = emptyList(),
                    annotations = emptyList(),
                    swaps = SwapsMetadataV3(swapIds = emptyList(), lastUsedAssetHistory = emptySet()),
                    appearance =
                        color?.let { AccountAppearanceMetadataV3(color = it, lastUpdated = Instant.ofEpochSecond(1)) }
                )
        )

    private companion object {
        val FIRST_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
        val SECOND_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
        val THIRD_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 2).toByte() })
        const val FIRST_COLOR = 0xFF0088FF
        const val SECOND_COLOR = 0x80BE38F3
        const val THIRD_COLOR = 0xFF22AA44
        const val DERIVATION_MILLIS = 50L
        val LOCK_FREE_READ_TIMEOUT = 5.seconds
    }
}
