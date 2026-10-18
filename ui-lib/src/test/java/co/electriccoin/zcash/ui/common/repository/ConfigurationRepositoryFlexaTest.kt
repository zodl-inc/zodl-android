package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.configuration.api.ConfigurationProvider
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.configuration.model.map.StringConfiguration
import co.electriccoin.zcash.ui.common.model.DistributionDimension
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Flexa needs no build-time key: every distribution offers it on mainnet unless the remote switch is off. */
class ConfigurationRepositoryFlexaTest {
    @Test
    fun aMainnetStoreBuildOffersFlexaWithoutAKey() =
        runTest {
            assertTrue(repository(ZcashNetwork.Mainnet, DistributionDimension.STORE).isFlexaAvailable())
        }

    @Test
    fun aTestnetBuildDoesNotOfferFlexa() =
        runTest {
            assertFalse(repository(ZcashNetwork.Testnet, DistributionDimension.STORE).isFlexaAvailable())
        }

    @Test
    fun theRemoteSwitchTurnsFlexaOff() =
        runTest {
            val off = persistentMapOf("is_flexa_available" to "false")
            assertFalse(repository(ZcashNetwork.Mainnet, DistributionDimension.STORE, off).isFlexaAvailable())
        }

    @Test
    fun theFDroidBuildOffersFlexa() =
        runTest {
            assertTrue(repository(ZcashNetwork.Mainnet, DistributionDimension.FOSS).isFlexaAvailable())
        }

    @Test
    fun theFDroidTestnetBuildDoesNotOfferFlexa() =
        runTest {
            assertFalse(repository(ZcashNetwork.Testnet, DistributionDimension.FOSS).isFlexaAvailable())
        }

    private fun repository(
        network: ZcashNetwork,
        distribution: DistributionDimension,
        remote: Map<String, String> = emptyMap(),
    ): ConfigurationRepository {
        val versionInfo =
            mockk<VersionInfo> {
                every { this@mockk.network } returns network
                every { this@mockk.distribution } returns distribution
            }
        val getVersionInfo = mockk<GetVersionInfoProvider> { every { this@mockk.invoke() } returns versionInfo }
        val provider =
            object : ConfigurationProvider {
                private val configuration = StringConfiguration(persistentMapOf<String, String>().putAll(remote), null)

                override fun peekConfiguration(): Configuration = configuration

                override fun getConfigurationFlow(): Flow<Configuration> = flowOf(configuration)

                override fun hintToRefresh() = Unit
            }
        return ConfigurationRepositoryImpl(provider, getVersionInfo)
    }
}
