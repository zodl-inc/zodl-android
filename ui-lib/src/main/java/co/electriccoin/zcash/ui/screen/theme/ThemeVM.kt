package co.electriccoin.zcash.ui.screen.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * The app-wide appearance the root [co.electriccoin.zcash.ui.design.theme.ZcashTheme] renders with. Scoped to
 * the activity, since the theme wraps the whole content tree rather than any single screen.
 */
internal class ThemeVM(
    appearanceModeStorageProvider: AppearanceModeStorageProvider,
    isOledEnabledStorageProvider: IsOledEnabledStorageProvider,
) : ViewModel() {
    /**
     * Both stored theme values as one emission, so [appearanceMode], [isOledEnabled] and [isThemeResolved]
     * all derive from a single upstream read and can never disagree about whether it has landed yet. Null
     * until that read completes.
     *
     * The read is bounded in both directions, because the splash screen is held until it produces something:
     * a throwing preference store falls back to [DEFAULT_THEME_APPEARANCE] instead of killing the coroutine,
     * and a store that simply never answers is given [THEME_READ_TIMEOUT] before the same fallback is
     * emitted. A genuine value arriving afterwards still replaces the fallback.
     *
     * That fallback has a sharp edge: once `.catch` emits [DEFAULT_THEME_APPEARANCE], the combined flow
     * completes, so if only one of the two stores throws, the other store's still-healthy value is discarded
     * for the rest of the session too, with no retry, instead of being kept on its own. This is accepted
     * because this flow exists only to release the splash gate, not to serve as a durable source of truth,
     * and `ThemeVMTest` pins it: a stored [AppearanceMode.DARK] alongside a throwing OLED store still
     * resolves to [AppearanceMode.SYSTEM].
     */
    private val theme: StateFlow<ThemeAppearance?> =
        channelFlow {
            val fallback =
                launch {
                    delay(THEME_READ_TIMEOUT)
                    Twig.error { "Stored app theme not read in time, falling back to the default appearance" }
                    send(DEFAULT_THEME_APPEARANCE)
                }

            combine(
                appearanceModeStorageProvider.observe(),
                isOledEnabledStorageProvider.observe()
            ) { appearanceMode, isOledEnabled ->
                ThemeAppearance(
                    appearanceMode = appearanceMode ?: AppearanceMode.SYSTEM,
                    isOledEnabled = isOledEnabled == true
                )
            }.catch { error ->
                Twig.error(error) { "Unable to read the stored app theme, falling back to the default appearance" }
                emit(DEFAULT_THEME_APPEARANCE)
            }.collect {
                fallback.cancel()
                send(it)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            null
        )

    /**
     * A flow of the user's chosen [AppearanceMode]. A never-chosen preference resolves to [AppearanceMode.SYSTEM].
     */
    val appearanceMode: StateFlow<AppearanceMode> =
        theme
            .map { it?.appearanceMode ?: AppearanceMode.SYSTEM }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                AppearanceMode.SYSTEM
            )

    /**
     * A flow of whether pure black (OLED) should be used whenever [appearanceMode] resolves to dark.
     */
    val isOledEnabled: StateFlow<Boolean> =
        theme
            .map { it?.isOledEnabled == true }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                false
            )

    /**
     * Whether [appearanceMode] and [isOledEnabled] carry the stored values instead of their seeded defaults.
     * The splash screen is held while this is false, so the first composed frame already renders the user's
     * own appearance rather than flashing System/Classic Dark first. Shared eagerly because that gate reads
     * the value without subscribing to it. Guaranteed to turn true within [THEME_READ_TIMEOUT] even when the
     * preference store never answers, so a broken store costs the user the wrong theme rather than the app.
     */
    val isThemeResolved: StateFlow<Boolean> =
        theme
            .map { it != null }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                false
            )
}

private data class ThemeAppearance(
    val appearanceMode: AppearanceMode,
    val isOledEnabled: Boolean,
)

/**
 * How long the stored theme read is given before the splash screen is released with the default appearance.
 * Short enough not to read as a hang, long enough for a healthy preference store to win the race.
 */
internal val THEME_READ_TIMEOUT = 2.seconds

private val DEFAULT_THEME_APPEARANCE =
    ThemeAppearance(
        appearanceMode = AppearanceMode.SYSTEM,
        isOledEnabled = false
    )
