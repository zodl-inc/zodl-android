@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.about

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import co.electriccoin.zcash.configuration.api.ConfigurationProvider
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.compose.LocalActivity
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.about.view.About
import co.electriccoin.zcash.ui.screen.support.model.ConfigInfo
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
internal fun AboutScreen() {
    val activity = LocalActivity.current
    val navigationRouter = koinInject<NavigationRouter>()
    val androidConfigurationProvider = koinInject<ConfigurationProvider>()
    val configInfo = ConfigInfo.new(androidConfigurationProvider)
    val versionInfo = VersionInfo.new(activity.applicationContext)
    LaunchedEffect(Unit) { androidConfigurationProvider.hintToRefresh() }
    BackHandler { navigationRouter.back() }
    About(
        onBack = { navigationRouter.back() },
        configInfo = configInfo,
        versionInfo = versionInfo,
        onPrivacyPolicy = { navigationRouter.forward(ExternalUrl(URL)) },
        onTermsOfUse = { navigationRouter.forward(ExternalUrl(URL)) }
    )
}

private const val URL = "https://zodl.com/privacy-policy/"

@Serializable
data object AboutArgs
