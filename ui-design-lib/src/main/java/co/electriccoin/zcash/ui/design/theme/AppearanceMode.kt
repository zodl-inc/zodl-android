package co.electriccoin.zcash.ui.design.theme

/**
 * The user's chosen light/dark appearance for the app. [SYSTEM] follows the device's setting.
 *
 * Pure black (OLED) is not a value here - it's an independent on/off modifier ([ZcashTheme]'s `isOledEnabled`)
 * layered on top of whichever mode resolves to dark, since it only ever makes sense alongside a dark appearance.
 */
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
}
