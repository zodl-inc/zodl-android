package com.flexa.core.theme

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.flexa.core.Flexa

@Composable
fun FlexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Flexa.themeConfig.useDynamicColorScheme,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        darkTheme -> {
            val lightColors = lightColorScheme()
            val darkColors = darkColorScheme()
            Flexa.themeConfig.darkColorsScheme?.toComposeColorScheme(darkColors)
                ?: Flexa.themeConfig.lightColorsScheme?.toComposeColorScheme(lightColors)
                ?: darkColors
        }

        else -> {
            val lightColors = lightColorScheme()
            val darkColors = darkColorScheme()
            Flexa.themeConfig.lightColorsScheme?.toComposeColorScheme(lightColors)
                ?: Flexa.themeConfig.darkColorsScheme?.toComposeColorScheme(darkColors)
                ?: lightColors
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme

            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } else {
                window?.apply {
                    statusBarColor = Color.Transparent.toArgb()
                    navigationBarColor = Color.Transparent.toArgb()
                    insetsController?.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
