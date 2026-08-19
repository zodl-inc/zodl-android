package co.electriccoin.zcash.ui.design.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import co.electriccoin.zcash.ui.design.LocalKeyboardManager
import co.electriccoin.zcash.ui.design.rememberKeyboardManager
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.DarkZashiColorsInternal
import co.electriccoin.zcash.ui.design.theme.colors.LightZashiColorsInternal
import co.electriccoin.zcash.ui.design.theme.colors.LocalZashiColors
import co.electriccoin.zcash.ui.design.theme.colors.OledZashiColorsInternal
import co.electriccoin.zcash.ui.design.theme.internal.DarkColorPalette
import co.electriccoin.zcash.ui.design.theme.internal.DarkExtendedColorPalette
import co.electriccoin.zcash.ui.design.theme.internal.ExtendedTypography
import co.electriccoin.zcash.ui.design.theme.internal.LightColorPalette
import co.electriccoin.zcash.ui.design.theme.internal.LightExtendedColorPalette
import co.electriccoin.zcash.ui.design.theme.internal.LocalExtendedColors
import co.electriccoin.zcash.ui.design.theme.internal.LocalExtendedTypography
import co.electriccoin.zcash.ui.design.theme.internal.LocalTypographies
import co.electriccoin.zcash.ui.design.theme.internal.OledColorPalette
import co.electriccoin.zcash.ui.design.theme.internal.OledExtendedColorPalette
import co.electriccoin.zcash.ui.design.theme.internal.PrimaryTypography
import co.electriccoin.zcash.ui.design.theme.internal.Typography
import co.electriccoin.zcash.ui.design.theme.typography.LocalZashiTypography
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypographyInternal

/**
 * Commonly used top level app theme definition
 *
 * @param forceDarkMode Set this to true to force the app to use the dark mode theme, which is helpful, e.g.,
 * for the compose previews.
 * @param isOledDark Set this to true to use the pure black (OLED) variant of the dark theme. Defaults to the value
 * provided by an enclosing [ZcashTheme] so that nested, always-dark screens inherit the user's choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZcashTheme(
    forceDarkMode: Boolean = false,
    balancesAvailable: Boolean = true,
    isOledDark: Boolean = LocalIsOledDark.current,
    content: @Composable () -> Unit
) {
    val useDarkMode = forceDarkMode || isSystemInDarkTheme()
    val useOledDark = useDarkMode && isOledDark
    val baseColors =
        when {
            useOledDark -> OledColorPalette
            useDarkMode -> DarkColorPalette
            else -> LightColorPalette
        }
    val extendedColors =
        when {
            useOledDark -> OledExtendedColorPalette
            useDarkMode -> DarkExtendedColorPalette
            else -> LightExtendedColorPalette
        }
    val zashiColors =
        when {
            useOledDark -> OledZashiColorsInternal
            useDarkMode -> DarkZashiColorsInternal
            else -> LightZashiColorsInternal
        }

    ZcashSystemBarTheme(useDarkMode, useOledDark)

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalZashiColors provides zashiColors,
        LocalZashiTypography provides ZashiTypographyInternal,
        LocalRippleConfiguration provides MaterialRippleConfig,
        LocalBalancesAvailable provides balancesAvailable,
        LocalIsOledDark provides isOledDark,
        LocalKeyboardManager provides rememberKeyboardManager()
    ) {
        ProvideDimens {
            MaterialTheme(
                colorScheme = baseColors,
                typography = PrimaryTypography,
                content = content
            )
        }
    }
}

@Composable
private fun ZcashSystemBarTheme(
    useDarkMode: Boolean,
    useOledDark: Boolean
) {
    val activity = LocalActivity.current
    LaunchedEffect(useDarkMode, useOledDark) {
        if (activity is ComponentActivity) {
            if (useDarkMode) {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle =
                        SystemBarStyle.dark(if (useOledDark) DefaultOledScrim else DefaultDarkScrim)
                )
            } else {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.light(DefaultLightScrim, DefaultDarkScrim)
                )
            }
        }
    }
}

// Use with eg. ZcashTheme.colors.tertiary
object ZcashTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current

    val typography: Typography
        @Composable
        get() = LocalTypographies.current

    val extendedTypography: ExtendedTypography
        @Composable
        get() = LocalExtendedTypography.current

    // TODO [#808]: [Design system] Use Dimens across the app
    // TODO [#808]: https://github.com/Electric-Coin-Company/zashi-android/issues/808
    val dimens: Dimens
        @Composable
        get() = localDimens.current
}

@OptIn(ExperimentalMaterial3Api::class)
private val MaterialRippleConfig: RippleConfiguration
    @Composable
    get() = RippleConfiguration(color = LocalContentColor.current, rippleAlpha = RippleDefaults.RippleAlpha)

@Suppress("MagicNumber")
private val DefaultLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)

@Suppress("MagicNumber")
private val DefaultDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@Suppress("MagicNumber")
private val DefaultOledScrim = Color.argb(0x80, 0x00, 0x00, 0x00)

@Suppress("CompositionLocalAllowlist")
val LocalIsOledDark = staticCompositionLocalOf { false }
