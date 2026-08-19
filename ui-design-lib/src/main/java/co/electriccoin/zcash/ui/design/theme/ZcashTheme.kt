package co.electriccoin.zcash.ui.design.theme

import android.graphics.Color
import android.view.ContextThemeWrapper
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import co.electriccoin.zcash.ui.design.LocalKeyboardManager
import co.electriccoin.zcash.ui.design.component.ConfigurationOverride
import co.electriccoin.zcash.ui.design.component.UiMode
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
 * for the compose previews. The user's [appearanceMode] light/dark choice is ignored while this is true, but
 * [isOledEnabled] still applies.
 * @param appearanceMode The user's chosen light/dark appearance. Defaults to the value provided by an enclosing
 * [ZcashTheme] so that nested, always-dark screens inherit the user's choice.
 * @param isOledEnabled Whether pure black should be used whenever the theme resolves to dark. Independent of
 * [appearanceMode] - it applies under [AppearanceMode.DARK] just as much as under [AppearanceMode.SYSTEM]
 * resolving to dark. Defaults to the value provided by an enclosing [ZcashTheme].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZcashTheme(
    forceDarkMode: Boolean = false,
    balancesAvailable: Boolean = true,
    appearanceMode: AppearanceMode = LocalAppearanceMode.current,
    isOledEnabled: Boolean = LocalOledEnabled.current,
    content: @Composable () -> Unit
) {
    val useDarkMode =
        forceDarkMode ||
            appearanceMode == AppearanceMode.DARK ||
            (appearanceMode == AppearanceMode.SYSTEM && isSystemInDarkTheme())
    val useOledDark = useDarkMode && isOledEnabled
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
        LocalAppearanceMode provides appearanceMode,
        LocalOledEnabled provides isOledEnabled,
        LocalKeyboardManager provides rememberKeyboardManager()
    ) {
        ProvideDimens {
            MaterialTheme(
                colorScheme = baseColors,
                typography = PrimaryTypography,
            ) {
                // Compose color locals above don't affect resource-qualifier resolution (drawable-night,
                // values-night, ...) - that's driven by the real Configuration.uiMode, which otherwise
                // stays whatever the device's actual light/dark setting is. Force it to match the resolved
                // theme so -night assets aren't picked while rendering the light palette (or vice versa)
                // whenever appearanceMode/forceDarkMode diverges from the system setting.
                //
                // Deliberately NOT the ConfigurationOverride composable (which wraps LocalContext via
                // Context.createConfigurationContext) - on an Activity context that returns a context
                // wrapping the Activity's *internal* base context, one level deeper than
                // ContextWrapper.baseContext expects, which breaks LocalContext.componentActivity()'s
                // single-level unwrap (KoinActivityViewModel.kt) for every screen below. ContextThemeWrapper
                // + applyOverrideConfiguration keeps baseContext pointing at the Activity itself, matching
                // the same safe pattern Override.kt already uses to wrap this same content root for tests.
                val currentConfiguration = LocalConfiguration.current
                val resolvedConfiguration =
                    remember(currentConfiguration, useDarkMode) {
                        ConfigurationOverride(
                            uiMode = if (useDarkMode) UiMode.Dark else UiMode.Light,
                            locale = null
                        ).newConfiguration(currentConfiguration)
                    }
                val activityContext = LocalContext.current
                val resolvedContext =
                    remember(activityContext, resolvedConfiguration) {
                        object : ContextThemeWrapper(activityContext, null) {
                            init {
                                applyOverrideConfiguration(resolvedConfiguration)
                            }
                        }
                    }
                CompositionLocalProvider(
                    LocalConfiguration provides resolvedConfiguration,
                    LocalContext provides resolvedContext
                ) {
                    content()
                }
            }
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
val LocalAppearanceMode = staticCompositionLocalOf { AppearanceMode.SYSTEM }

@Suppress("CompositionLocalAllowlist")
val LocalOledEnabled = staticCompositionLocalOf { false }
