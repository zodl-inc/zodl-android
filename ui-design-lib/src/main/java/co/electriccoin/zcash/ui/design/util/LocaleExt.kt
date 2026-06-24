package co.electriccoin.zcash.ui.design.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

@Composable
fun rememberDesiredFormatLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.getPreferredLocale() }
}

fun Configuration.getPreferredLocale(): Locale {
    val locales = ConfigurationCompat.getLocales(this)
    return locales.getFirstMatch(arrayOf("en", "es", "pt")) ?: locales.get(0) ?: Locale.US
}
