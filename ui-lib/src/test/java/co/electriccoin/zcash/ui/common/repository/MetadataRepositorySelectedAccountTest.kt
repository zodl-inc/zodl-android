package co.electriccoin.zcash.ui.common.repository

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MetadataDataSource
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.metadata.AccountAppearanceMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataSimpleSwapAssetV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import co.electriccoin.zcash.ui.common.provider.MetadataKeyStorageProvider
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Instant
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [MetadataRepositoryImpl]'s selected-account readers: the swap history and the swap list follow the
 * selection to the newly selected account's entry in the shared map, while an account's colour
 * follows that account alone, whichever account is selected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetadataRepositorySelectedAccountTest {
    private val first = account<ZodlAccount>(FIRST_UUID)
    private val second = account<ViewOnlyAccount>(SECOND_UUID)
    private val firstKey = mockk<MetadataKey>()
    private val secondKey = mockk<MetadataKey>()
    private val selection = MutableStateFlow<WalletAccount?>(first)
    private val firstMetadata =
        MutableStateFlow<MetadataV3?>(metadata(history = setOf(FIRST_ASSET), swap = FIRST_SWAP, color = FIRST_COLOR))
    private val secondMetadata =
        MutableStateFlow<MetadataV3?>(metadata(history = setOf(SECOND_ASSET), swap = SECOND_SWAP, color = SECOND_COLOR))

    private val assets = Collections.synchronizedMap(mutableMapOf<String, SimpleSwapAsset>())

    private val repository =
        MetadataRepositoryImpl(
            accountDataSource =
                mockk<AccountDataSource> {
                    every { selectedAccount } returns selection
                    every { allAccounts } returns MutableStateFlow(listOf(first, second))
                },
            metadataDataSource =
                mockk<MetadataDataSource>(relaxed = true) {
                    every { observe(firstKey) } returns firstMetadata
                    every { observe(secondKey) } returns secondMetadata
                },
            metadataKeyStorageProvider =
                mockk<MetadataKeyStorageProvider> {
                    coEvery { get(FIRST_UUID) } returns firstKey
                    coEvery { get(SECOND_UUID) } returns secondKey
                },
            persistableWalletProvider = mockk(),
            simpleSwapAssetProvider =
                mockk<SimpleSwapAssetProvider> {
                    every { get(any(), any()) } answers { asset("${firstArg<String>()}:${secondArg<String>()}") }
                }
        )

    @Test
    fun swapHistoryFollowsTheSelectedAccount() =
        runTest {
            val seen = record(repository.observeLastUsedAssetHistory())
            assertEquals(setOf(asset(FIRST_ASSET)), repository.observeLastUsedAssetHistory().first { it != null })
            repository.metadata.first { FIRST_UUID in it && SECOND_UUID in it }

            selection.value = second

            assertEquals(setOf(asset(SECOND_ASSET)), repository.observeLastUsedAssetHistory().first())
            assertEquals(setOf(asset(SECOND_ASSET)), seen.last())
        }

    @Test
    fun swapMetadataOfTheNewSelectionReplacesTheOld() =
        runTest {
            val seen = record(repository.observeSwapMetadata())
            assertEquals(listOf(FIRST_SWAP), repository.observeSwapMetadata().first { it != null }?.depositAddresses())
            repository.metadata.first { FIRST_UUID in it && SECOND_UUID in it }

            selection.value = second

            assertEquals(listOf(SECOND_SWAP), repository.observeSwapMetadata().first()?.depositAddresses())
            assertEquals(listOf(SECOND_SWAP), seen.last()?.depositAddresses())
        }

    @Test
    fun observeColorFollowsOnlyItsAccount() =
        runTest {
            val seen = record(repository.observeColor(SECOND_UUID))
            repository.observeColor(SECOND_UUID).first { it != null }

            selection.value = second
            selection.value = first
            firstMetadata.value = metadata(history = emptySet(), swap = FIRST_SWAP, color = OTHER_COLOR)
            repository.metadata.first { it[FIRST_UUID]?.accountMetadata?.appearance?.color == Color(OTHER_COLOR) }

            assertEquals(listOf(Color(SECOND_COLOR)), seen.filterNotNull())

            secondMetadata.value = metadata(history = emptySet(), swap = SECOND_SWAP, color = OTHER_COLOR)
            repository.observeColor(SECOND_UUID).first { it == Color(OTHER_COLOR) }
            assertEquals(Color(OTHER_COLOR), repository.observeColor(SECOND_UUID).first())
        }

    private fun <T> TestScope.record(flow: Flow<T>): List<T> {
        val seen = Collections.synchronizedList(mutableListOf<T>())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { seen += it } }
        return seen
    }

    private fun asset(id: String): SimpleSwapAsset = assets.getOrPut(id) { mockk(name = id) }

    private fun List<TransactionSwapMetadata>.depositAddresses() = map { it.depositAddress }

    private inline fun <reified T : WalletAccount> account(uuid: AccountUuid) =
        mockk<T> { every { sdkAccount } returns Account.new(uuid) }

    private fun metadata(
        history: Set<String>,
        swap: String,
        color: Long
    ) = MetadataV3(
        lastUpdated = Instant.ofEpochSecond(1),
        accountMetadata =
            AccountMetadataV3(
                bookmarked = emptyList(),
                read = emptyList(),
                annotations = emptyList(),
                swaps = SwapsMetadataV3(swapIds = listOf(swap(swap)), lastUsedAssetHistory = history),
                appearance = AccountAppearanceMetadataV3(color = Color(color), lastUpdated = Instant.ofEpochSecond(1))
            )
    )

    private fun swap(depositAddress: String) =
        SwapMetadataV3(
            depositAddress = depositAddress,
            provider = "near",
            totalFees = Zatoshi(0),
            totalUSDFeesInternal = BigDecimal.ZERO,
            lastUpdated = Instant.ofEpochSecond(1),
            fromAsset = MetadataSimpleSwapAssetV3(token = "zec", chain = "zec"),
            toAsset = MetadataSimpleSwapAssetV3(token = "usdc", chain = "near"),
            exactInput = true,
            status = null,
            amountOutFormatted = null
        )

    private companion object {
        val FIRST_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
        val SECOND_UUID: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })
        const val FIRST_ASSET = "btc:btc"
        const val SECOND_ASSET = "eth:eth"
        const val FIRST_SWAP = "deposit-first"
        const val SECOND_SWAP = "deposit-second"
        const val FIRST_COLOR = 0xFF0088FF
        const val SECOND_COLOR = 0xFFBE38F3
        const val OTHER_COLOR = 0xFF22AA44
    }
}
