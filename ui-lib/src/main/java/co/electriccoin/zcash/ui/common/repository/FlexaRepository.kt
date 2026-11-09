package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.FLEXA_OHTTP_PUBLISHABLE_KEY
import co.electriccoin.zcash.ui.common.provider.FlexaOhttpInterceptor
import co.electriccoin.zcash.ui.common.provider.FlexaOhttpTransportImpl
import co.electriccoin.zcash.ui.common.provider.HttpClientProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.usecase.ObserveServerConnectionModeUseCase
import com.flexa.core.Flexa
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.AvailableAsset
import com.flexa.core.shared.CustodyModel
import com.flexa.core.shared.FlexaClientConfiguration
import com.flexa.core.theme.FlexaTheme
import com.flexa.identity.buildIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

interface FlexaRepository {
    fun init()

    suspend fun disconnect()
}

class FlexaRepositoryImpl(
    private val application: Application,
    private val configurationRepository: ConfigurationRepository,
    private val accountDataSource: AccountDataSource,
    httpClientProvider: HttpClientProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider,
    private val observeServerConnectionMode: ObserveServerConnectionModeUseCase,
) : FlexaRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val ohttpInterceptor = FlexaOhttpInterceptor(FlexaOhttpTransportImpl(httpClientProvider))

    /**
     * Whether Flexa's images and web views may connect to Flexa directly: only on a public connection with Tor
     * off. False until both settings are known, so nothing goes direct before then.
     */
    @Volatile
    internal var directConnectionsAllowed: Boolean = false
        private set

    override fun init() {
        scope.launch {
            if (!configurationRepository.isFlexaAvailable()) return@launch
            launch { trackDirectConnections() }
            Flexa.init(getFlexaClientConfiguration())
            Twig.info { "Flexa initialized" }

            accountDataSource.zashiAccount
                .mapNotNull { account ->
                    val total = account?.totalShieldedBalance
                    val available = account?.spendableShieldedBalance
                    if (total != null && available != null) total to available else null
                }.collect { (total, available) ->
                    val totalZec = total.convertZatoshiToZec().toDouble()
                    val availableZec = available.convertZatoshiToZec().toDouble()
                    Flexa.updateAssetAccounts(
                        arrayListOf(
                            createFlexaAccount(
                                total = totalZec,
                                available = availableZec,
                            ),
                        ),
                    )
                    Twig.info { "Flexa updated by total:$totalZec available:$availableZec" }
                }
        }
    }

    internal suspend fun trackDirectConnections() {
        combine(isTorEnabledStorageProvider.observe(), observeServerConnectionMode()) { tor, mode ->
            tor != true && mode != ServerConnectionMode.PRIVATE
        }.collect {
            directConnectionsAllowed = it
        }
    }

    override suspend fun disconnect() {
        if (configurationRepository.isFlexaAvailable()) {
            Flexa.buildIdentity().build().disconnect()
        }
    }

    /**
     * The SDK gets the gateway's placeholder key and sends its API calls through OHTTP: the gateway swaps
     * the placeholder for the real key, so no build carries it. Its web views (merchant directory, how to
     * pay, legal pages) and images are not loaded with these OkHttp clients and would go to Flexa directly:
     * on a private connection or with Tor on ([directConnectionsAllowed] false) images are not downloaded
     * and a web view loads only after the user agrees to a disclosure (`directConnectionsAllowed` of the
     * configuration).
     */
    internal fun getFlexaClientConfiguration() =
        FlexaClientConfiguration(
            context = application,
            publishableKey = FLEXA_OHTTP_PUBLISHABLE_KEY,
            theme =
                FlexaTheme(
                    useDynamicColorScheme = true,
                ),
            assetAccounts = arrayListOf(createFlexaAccount(DEFAULT_BALANCE, DEFAULT_BALANCE)),
            webViewThemeConfig =
                "{\n" +
                    "    \"android\": {\n" +
                    "        \"light\": {\n" +
                    "            \"backgroundColor\": \"#100e29\",\n" +
                    "            \"sortTextColor\": \"#ed7f60\",\n" +
                    "            \"titleColor\": \"#ffffff\",\n" +
                    "            \"cardColor\": \"#2a254e\",\n" +
                    "            \"borderRadius\": \"15px\",\n" +
                    "            \"textColor\": \"#ffffff\"\n" +
                    "        },\n" +
                    "        \"dark\": {\n" +
                    "            \"backgroundColor\": \"#100e29\",\n" +
                    "            \"sortTextColor\": \"#ed7f60\",\n" +
                    "            \"titleColor\": \"#ffffff\",\n" +
                    "            \"cardColor\": \"#2a254e\",\n" +
                    "            \"borderRadius\": \"15px\",\n" +
                    "            \"textColor\": \"#ffffff\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}",
            okHttpClientCustomizer = { it.addInterceptor(ohttpInterceptor) },
            directConnectionsAllowed = { directConnectionsAllowed },
        )

    private fun createFlexaAccount(
        total: Double,
        available: Double,
    ) = AssetAccount(
        displayName = "",
        icon = "android.resource://${application.packageName}/drawable/ic_flexa_logo",
        availableAssets =
            listOf(
                AvailableAsset(
                    assetId = "bip122:00040fe8ec8471911baa1db1266ea15d/slip44:133",
                    balance = total,
                    balanceAvailable = available,
                    symbol = "ZEC",
                    icon = "android.resource://${application.packageName}/drawable/ic_token_zec",
                ),
            ),
        custodyModel = CustodyModel.LOCAL,
        assetAccountHash = UUID.randomUUID().toString().toSha256(),
    )

    private fun String.toSha256() =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .fold("") { str, value -> str + "%02x".format(value) }
}

private const val DEFAULT_BALANCE = .0
