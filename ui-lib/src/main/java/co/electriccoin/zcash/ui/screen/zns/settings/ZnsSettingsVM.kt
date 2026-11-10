package co.electriccoin.zcash.ui.screen.zns.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsZnsNamesEnabledStorageProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ZnsSettingsVM(
    private val isZnsNamesEnabled: IsZnsNamesEnabledStorageProvider,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state =
        isZnsNamesEnabled
            .observe()
            .map { isEnabled ->
                ZnsSettingsState(
                    isEnabled = isEnabled,
                    onSaveClick = ::onSaveClick,
                    onDismiss = ::onBack
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun onBack() = navigationRouter.back()

    private fun onSaveClick(enable: Boolean) =
        viewModelScope.launch {
            isZnsNamesEnabled.store(enable)
            navigationRouter.back()
        }
}
