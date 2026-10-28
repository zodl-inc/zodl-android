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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MetadataRepositoryImpl] writing an account's colour under that account's own metadata key,
 * whichever account is selected, and sharing every account's metadata in one map, empty at first. A
 * key that is not stored yet is derived from the account's own viewing key, the Zodl account's
 * without one.
 */
class MetadataRepositoryAccountColorTest {
    private val selectedKey = mockk<MetadataKey>()
    private val viewOnlyKey = mockk<MetadataKey>()
    private val selected = account<ZodlAccount>(SELECTED_UUID)
    private val viewOnly = account<ViewOnlyAccount>(VIEW_ONLY_UUID)
    private val metadataDataSource = mockk<MetadataDataSource>(relaxed = true)
    private val accounts = MutableStateFlow<List<WalletAccount>?>(listOf(selected, viewOnly))

    private val repository = repository()

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun aColorIsWrittenUnderTheAccountsOwnKey() =
        runTest {
            coEvery { metadataDataSource.setAccountColor(any(), any()) } returns true

            assertTrue(repository.setAccountColor(viewOnly, Color(PICKED)))

            coVerify(exactly = 1) { metadataDataSource.setAccountColor(Color(PICKED), viewOnlyKey) }
            coVerify(exactly = 0) { metadataDataSource.setAccountColor(any(), selectedKey) }
        }

    @Test
    fun aFailedWriteReportsFalse() =
        runTest {
            coEvery { metadataDataSource.setAccountColor(any(), any()) } throws IOException()
            assertFalse(repository.setAccountColor(viewOnly, Color(PICKED)))

            coEvery { metadataDataSource.setAccountColor(any(), any()) } returns false
            assertFalse(repository.setAccountColor(viewOnly, Color(PICKED)))
        }

    @Test
    fun theMapIsEmptyFirst() {
        assertEquals(emptyMap(), repository.metadata.value)
    }

    @Test
    fun theMapHoldsEveryAccountsMetadataReadUnderItsOwnKey() =
        runTest {
            every { metadataDataSource.observe(selectedKey) } returns flowOf(null, metadata(null))
            every { metadataDataSource.observe(viewOnlyKey) } returns flowOf(null, metadata(Color(PICKED)))

            assertEquals(
                mapOf(SELECTED_UUID to metadata(null), VIEW_ONLY_UUID to metadata(Color(PICKED))),
                repository.metadata.first { it.size == 2 }
            )
        }

    @Test
    fun anAccountsColorFollowsAWrite() =
        runTest {
            val stored = MutableStateFlow<MetadataV3?>(metadata(null))
            every { metadataDataSource.observe(selectedKey) } returns flowOf(null)
            every { metadataDataSource.observe(viewOnlyKey) } returns stored
            coEvery { metadataDataSource.setAccountColor(any(), viewOnlyKey) } answers {
                stored.value = metadata(Color(PICKED))
                true
            }

            val color = async { repository.observeColor(VIEW_ONLY_UUID).first { it != null } }
            repository.setAccountColor(viewOnly, Color(PICKED))

            assertEquals(Color(PICKED), color.await())
        }

    @Test
    fun aViewOnlyKeyIsDerivedFromTheAccountsViewingKey() =
        runTest {
            val sdkAccount =
                mockk<Account> {
                    every { accountUuid } returns VIEW_ONLY_UUID
                    every { ufvk } returns VIEW_ONLY_UFVK
                }
            val account = mockk<ViewOnlyAccount> { every { this@mockk.sdkAccount } returns sdkAccount }

            assertEquals(listOf(sdkAccount.ufvk), derivedUfvks(account))
        }

    @Test
    fun theZodlKeyIsDerivedWithoutAViewingKey() =
        runTest {
            val sdkAccount =
                mockk<Account> {
                    every { accountUuid } returns SELECTED_UUID
                    every { ufvk } returns VIEW_ONLY_UFVK
                }
            val zodl = mockk<ZodlAccount> { every { this@mockk.sdkAccount } returns sdkAccount }

            assertEquals(listOf<String?>(null), derivedUfvks(zodl))
        }

    /**
     * Writes a colour for [account] through a repository whose key storage holds no key yet, and
     * returns the viewing keys [MetadataKey.derive] was given.
     */
    private suspend fun derivedUfvks(account: WalletAccount): List<String?> {
        val ufvks = mutableListOf<String?>()
        mockkObject(MetadataKey.Companion)
        coEvery { MetadataKey.derive(any(), any(), any(), any()) } coAnswers {
            ufvks += arg<String?>(3)
            mockk()
        }
        coEvery { metadataDataSource.setAccountColor(any(), any()) } returns true
        val repository =
            repository(
                keyStorage =
                    mockk<MetadataKeyStorageProvider>(relaxed = true).also { provider ->
                        coEvery { provider.get(any()) } returns null
                    },
                persistableWalletProvider =
                    mockk<PersistableWalletProvider> {
                        coEvery { requirePersistableWallet() } returns mockk(relaxed = true)
                    },
            )

        assertTrue(repository.setAccountColor(account, Color(PICKED)))
        return ufvks
    }

    /**
     * A repository over [selected] and [viewOnly] whose key storage holds both accounts' keys unless
     * [keyStorage] says otherwise.
     */
    private fun repository(
        keyStorage: MetadataKeyStorageProvider =
            mockk {
                coEvery { get(SELECTED_UUID) } returns selectedKey
                coEvery { get(VIEW_ONLY_UUID) } returns viewOnlyKey
            },
        persistableWalletProvider: PersistableWalletProvider = mockk(),
    ) = MetadataRepositoryImpl(
        accountDataSource =
            mockk<AccountDataSource> {
                every { selectedAccount } returns flowOf(selected)
                every { allAccounts } returns accounts
                coEvery { getSelectedAccount() } returns selected
                coEvery { getZashiAccount() } returns selected
            },
        metadataDataSource = metadataDataSource,
        metadataKeyStorageProvider = keyStorage,
        persistableWalletProvider = persistableWalletProvider,
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
        val SELECTED_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
        val VIEW_ONLY_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
        const val PICKED = 0x80BE38F3
        const val VIEW_ONLY_UFVK = "uview1accountsownviewingkeyfixture"
    }
}
