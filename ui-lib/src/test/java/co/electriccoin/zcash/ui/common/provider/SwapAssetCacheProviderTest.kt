package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwapAssetCacheProviderTest {
    @Test
    fun storedMetadataRoundTripsWithoutPricesAndDuplicateWrites() =
        runTest {
            val preferences = RecordingPreferenceProvider()
            val provider = provider(preferences)
            val asset = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")

            provider.store(listOf(asset))
            provider.store(listOf(asset))
            val restored = provider.get().single()

            assertEquals(1, preferences.writeCount)
            assertEquals(asset.assetId, restored.assetId)
            assertEquals(asset.tokenTicker, restored.tokenTicker)
            assertEquals(asset.chainTicker, restored.chainTicker)
            assertEquals(asset.decimals, restored.decimals)
            assertNull(restored.usdPrice)
        }

    @Test
    fun unknownFieldsFromOlderSchemasAreIgnored() =
        runTest {
            val preferences =
                RecordingPreferenceProvider(
                    """
                    [
                        {"assetId":"btc-btc","tokenTicker":"btc","chainTicker":"btc","contractAddress":"old","decimals":8}
                    ]
                    """.trimIndent()
                )

            val restored = provider(preferences).get().single()

            assertEquals("btc-btc", restored.assetId)
            assertEquals("btc", restored.tokenTicker)
            assertEquals(8, restored.decimals)
        }

    private fun provider(preferences: RecordingPreferenceProvider): SwapAssetCacheProviderImpl {
        val standardPreferenceProvider = mockk<StandardPreferenceProvider>()
        coEvery { standardPreferenceProvider() } returns preferences
        return SwapAssetCacheProviderImpl(
            standardPreferenceProvider = standardPreferenceProvider,
            tokenIconProvider = mockk(relaxed = true),
            tokenNameProvider = mockk(relaxed = true),
            blockchainProvider =
                mockk {
                    every { getBlockchain(any()) } answers {
                        SwapAssetTestFixture.blockchain(firstArg())
                    }
                }
        )
    }
}

private class RecordingPreferenceProvider(
    initialValue: String? = null
) : PreferenceProvider {
    private var value = initialValue
    var writeCount = 0
        private set

    override suspend fun hasKey(key: PreferenceKey) = value != null

    override suspend fun putString(key: PreferenceKey, value: String?) {
        this.value = value
        writeCount++
    }

    override suspend fun putStringSet(key: PreferenceKey, value: Set<String>?) = Unit

    override suspend fun putLong(key: PreferenceKey, value: Long?) = Unit

    override suspend fun getLong(key: PreferenceKey): Long? = null

    override suspend fun getString(key: PreferenceKey): String? = value

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? = null

    override fun observe(key: PreferenceKey): Flow<String?> = flowOf(value)

    override suspend fun remove(key: PreferenceKey) {
        value = null
    }

    override suspend fun clearPreferences(): Boolean {
        value = null
        return true
    }
}
