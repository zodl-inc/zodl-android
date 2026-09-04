package co.electriccoin.zcash.ui.design.theme.colors

import androidx.compose.ui.graphics.Color

/**
 * The primary surface color of each theme, exposed for consumers that live outside of composition and therefore cannot
 * read [ZashiColors] - namely the chrome of the in-app Custom Tabs browser.
 */
object BrowserChromeColors {
    val light: Color = LightZashiColorsInternal.Surfaces.bgPrimary

    val dark: Color = DarkZashiColorsInternal.Surfaces.bgPrimary

    val oledDark: Color = OledZashiColorsInternal.Surfaces.bgPrimary
}
