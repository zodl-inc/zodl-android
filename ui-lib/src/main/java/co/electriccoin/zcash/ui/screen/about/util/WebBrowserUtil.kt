package co.electriccoin.zcash.ui.screen.about.util

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import co.electriccoin.zcash.ui.design.theme.colors.BrowserChromeColors

object WebBrowserUtil {
    internal fun startActivity(
        activity: Activity,
        url: String,
        isOledDark: Boolean
    ) {
        val lightParams = colorSchemeParams(BrowserChromeColors.light)
        val darkParams =
            colorSchemeParams(if (isOledDark) BrowserChromeColors.oledDark else BrowserChromeColors.dark)
        val intent =
            CustomTabsIntent
                .Builder()
                .setUrlBarHidingEnabled(true)
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, lightParams)
                .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, darkParams)
                .setDefaultColorSchemeParams(lightParams)
                .build()
        runCatching {
            intent.launchUrl(activity, Uri.parse(url))
        }
    }

    private fun colorSchemeParams(color: Color) =
        CustomTabColorSchemeParams
            .Builder()
            .setToolbarColor(color.toArgb())
            .setNavigationBarColor(color.toArgb())
            .build()
}
