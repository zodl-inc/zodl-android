package co.electriccoin.zcash.ui.design.theme

/**
 * The user's chosen appearance for the app. [SYSTEM] follows the device's light/dark setting and never
 * auto-resolves to [OLED] - pure black is always an explicit choice.
 */
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED,
}

/**
 * Whether [this] forces a dark appearance unconditionally, independent of the system setting. [AppearanceMode.SYSTEM]
 * defers to the platform instead and is not covered here.
 */
val AppearanceMode.forcesDark: Boolean
    get() = this == AppearanceMode.DARK || this == AppearanceMode.OLED
