package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.configuration.api.ConfigurationProvider
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface ConfigurationRepository {
    val configurationFlow: StateFlow<Configuration?>

    /**
     * Returns true if Flexa is available, false otherwise & null if loading.
     */
    val isFlexaAvailable: StateFlow<Boolean?>

    suspend fun isFlexaAvailable(): Boolean
}

class ConfigurationRepositoryImpl(
    androidConfigurationProvider: ConfigurationProvider,
    private val getVersionInfo: GetVersionInfoProvider,
) : ConfigurationRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val configurationFlow: StateFlow<Configuration?> =
        androidConfigurationProvider
            .getConfigurationFlow()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    override val isFlexaAvailable: StateFlow<Boolean?> =
        configurationFlow
            .filterNotNull()
            .map {
                val versionInfo = getVersionInfo()
                isFlexaAvailable(
                    network = versionInfo.network,
                    isEnabledRemotely = ConfigurationEntries.IS_FLEXA_AVAILABLE.getValue(it)
                )
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    override suspend fun isFlexaAvailable(): Boolean = isFlexaAvailable.filterNotNull().first()
}

/**
 * Flexa is a mainnet service. Its API calls go through the OHTTP gateway, which holds the publishable key
 * (see FlexaRepositoryImpl), so every distribution, F-Droid included, offers it without a build-time key.
 */
internal fun isFlexaAvailable(
    network: ZcashNetwork,
    isEnabledRemotely: Boolean
): Boolean = network != ZcashNetwork.Testnet && isEnabledRemotely
