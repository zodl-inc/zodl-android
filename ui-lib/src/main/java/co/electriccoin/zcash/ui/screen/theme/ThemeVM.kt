package co.electriccoin.zcash.ui.screen.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
     */
    private val theme: StateFlow<ThemeAppearance?> =
        combine(
            appearanceModeStorageProvider.observe(),
            isOledEnabledStorageProvider.observe()
        ) { appearanceMode, isOledEnabled ->
            ThemeAppearance(
                appearanceMode = appearanceMode ?: AppearanceMode.SYSTEM,
                isOledEnabled = isOledEnabled == true
            )
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
     * the value without subscribing to it.
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
