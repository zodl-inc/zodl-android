package co.electriccoin.zcash.ui.design.theme

import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
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
 * @param appearanceMode The user's chosen light/dark appearance. Defaults to the value provided by an enclosing
 * [ZcashTheme] so that nested screens inherit the user's choice; pass [AppearanceMode.DARK] explicitly to
 * force dark, which is what always-dark screens and the dark compose previews do.
 * @param isOledEnabled Whether pure black should be used whenever the theme resolves to dark. Independent of
 * [appearanceMode] - it applies under [AppearanceMode.DARK] just as much as under [AppearanceMode.SYSTEM]
 * resolving to dark. Defaults to the value provided by an enclosing [ZcashTheme].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZcashTheme(
    balancesAvailable: Boolean = true,
    appearanceMode: AppearanceMode = LocalAppearanceMode.current,
    isOledEnabled: Boolean = LocalOledEnabled.current,
    content: @Composable () -> Unit
) {
    val useDarkMode =
        appearanceMode == AppearanceMode.DARK ||
            (appearanceMode == AppearanceMode.SYSTEM && isSystemInDarkTheme())
    val useOledDark = useDarkMode && isOledEnabled
    val (baseColors, extendedColors, zashiColors) = themePalettes(useDarkMode, useOledDark)

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
                ResolvedConfiguration(useDarkMode) {
                    content()
                }
            }
        }
    }
}

/**
 * Provides [content] with a [android.content.res.Configuration] whose `uiMode` matches the resolved theme.
 *
 * The Compose color locals don't affect resource-qualifier resolution (drawable-night, values-night, ...) -
 * that follows the real `Configuration.uiMode`, which otherwise stays whatever the device's own light/dark
 * setting is, so `-night` assets would be picked while the light palette renders (or vice versa) whenever the
 * chosen appearance diverges from the system setting.
 *
 * Nothing is provided when the ambient configuration already carries the resolved `uiMode`. That covers the
 * common case, and it covers the screenshot tests, where
 * [co.electriccoin.zcash.ui.design.component.Override] has already driven that very `uiMode`
 * into both the configuration and the context - re-wrapping there produced a
 * `ContextThemeWrapper(ContextThemeWrapper(Activity))`. Stacking is no longer fatal in itself, because
 * `LocalContext.componentActivity()` (KoinActivityViewModel) now walks the entire
 * [android.content.ContextWrapper] chain rather than one level; skipping the redundant wrap simply keeps the
 * chain as short as the situation allows. [movableContentOf] preserves the subtree's state across the two
 * branches, the same way that same Override composable does.
 *
 * [ContextThemeWrapper] plus `applyOverrideConfiguration` rather than `Context.createConfigurationContext`:
 * on an activity context the latter returns a context wrapping the activity's *internal* base context, which
 * buries the activity one level deeper for no gain.
 */
@Composable
private fun ResolvedConfiguration(
    useDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    val currentConfiguration = LocalConfiguration.current
    val resolvedConfiguration =
        remember(currentConfiguration, useDarkMode) {
            ConfigurationOverride(
                uiMode = if (useDarkMode) UiMode.Dark else UiMode.Light,
                locale = null
            ).newConfiguration(currentConfiguration)
        }
    val contentSlot = remember { movableContentOf { content() } }

    if (resolvedConfiguration == currentConfiguration) {
        contentSlot()
    } else {
        val ambientContext = LocalContext.current
        val resolvedContext =
            remember(ambientContext, resolvedConfiguration) {
                object : ContextThemeWrapper(ambientContext, null) {
                    init {
                        applyOverrideConfiguration(resolvedConfiguration)
                    }
                }
            }
        CompositionLocalProvider(
            LocalConfiguration provides resolvedConfiguration,
            LocalContext provides resolvedContext
        ) {
            contentSlot()
        }
    }
}

/**
 * Resolves the three parallel palette families (Material color scheme, extended colors, Zashi
 * colors) for the light / dark / pure-black-OLED variants in one place, so the selections can
 * never drift apart.
 */
private fun themePalettes(
    useDarkMode: Boolean,
    useOledDark: Boolean,
) = when {
    useOledDark -> Triple(OledColorPalette, OledExtendedColorPalette, OledZashiColorsInternal)
    useDarkMode -> Triple(DarkColorPalette, DarkExtendedColorPalette, DarkZashiColorsInternal)
    else -> Triple(LightColorPalette, LightExtendedColorPalette, LightZashiColorsInternal)
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
    get() = RippleConfiguration(color = LocalContentColor.current, rippleAlpha = SubtleRippleAlpha)

@Suppress("MagicNumber")
private val SubtleRippleAlpha =
    RippleAlpha(
        draggedAlpha = 0.08f,
        focusedAlpha = 0.05f,
        hoveredAlpha = 0.04f,
        pressedAlpha = 0.05f
    )

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
