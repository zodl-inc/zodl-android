package co.electriccoin.zcash.ui.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class OldHomeViewModel(
    private val standardPreferenceProvider: StandardPreferenceProvider,
    appearanceModeStorageProvider: AppearanceModeStorageProvider,
    isOledEnabledStorageProvider: IsOledEnabledStorageProvider,
) : ViewModel() {
    /**
     * A flow of whether background sync is enabled
     */
    val isBackgroundSyncEnabled: StateFlow<Boolean?> =
        booleanStateFlow(StandardPreferenceKeys.IS_BACKGROUND_SYNC_ENABLED)

    /**
     * A flow of the wallet balances visibility.
     */
    val isHideBalances: StateFlow<Boolean?> = booleanStateFlow(StandardPreferenceKeys.IS_HIDE_BALANCES)

    /**
     * A flow of the user's chosen [AppearanceMode]. A never-chosen preference resolves to [AppearanceMode.SYSTEM].
     */
    val appearanceMode: StateFlow<AppearanceMode> =
        appearanceModeStorageProvider
            .observe()
            .map { it ?: AppearanceMode.SYSTEM }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                AppearanceMode.SYSTEM
            )

    /**
     * A flow of whether pure black (OLED) should be used whenever [appearanceMode] resolves to dark.
     */
    val isOledEnabled: StateFlow<Boolean> =
        isOledEnabledStorageProvider
            .observe()
            .map { it == true }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                false
            )

    private fun booleanStateFlow(default: BooleanPreferenceDefault): StateFlow<Boolean?> =
        flow<Boolean?> {
            emitAll(default.observe(standardPreferenceProvider()))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            null
        )
}
