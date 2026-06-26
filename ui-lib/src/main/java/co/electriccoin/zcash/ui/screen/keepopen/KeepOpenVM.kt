package co.electriccoin.zcash.ui.screen.keepopen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProvider
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiDisclaimerState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.KeepOpenState
import co.electriccoin.zcash.ui.screen.connectkeystone.connected.KeystoneConnectedArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeepOpenVM(
    application: Application,
    private val flow: KeepOpenFlow,
    private val isKeepScreenOnDuringRestoreProvider: IsKeepScreenOnDuringRestoreProvider,
    private val navigationRouter: NavigationRouter,
) : AndroidViewModel(application) {
    private val isChecked = MutableStateFlow(true)

    val state =
        isChecked
            .map { createState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(true)
            )

    private fun createState(isChecked: Boolean) =
        when (flow) {
            KeepOpenFlow.RESTORE -> {
                KeepOpenState(
                    description = stringRes(R.string.smartBanner_help_restore_info),
                    subtitle = stringRes(R.string.keepZodlOpenSubtitleRestore),
                    disclaimer = getDisclaimer(R.string.keep_open_restore_warning),
                    checkboxLabel = stringRes(R.string.keepScreenOnRestoring),
                    isChecked = isChecked,
                    onCheckedChange = { onChecked() },
                    button =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.restoreInfo_gotIt),
                            onClick = ::onButtonClick,
                        ),
                )
            }

            KeepOpenFlow.RESYNC -> {
                KeepOpenState(
                    description = stringRes(R.string.keepZodlOpenInstructionsResyncing),
                    subtitle = stringRes(R.string.keepZodlOpenSubtitleResync),
                    disclaimer = getDisclaimer(R.string.keep_open_resync_warning),
                    checkboxLabel = stringRes(R.string.keepScreenOnResyncing),
                    isChecked = isChecked,
                    onCheckedChange = { onChecked() },
                    button =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                            onClick = ::onButtonClick,
                        ),
                )
            }

            KeepOpenFlow.KEYSTONE -> {
                KeepOpenState(
                    description = stringRes(R.string.keepZodlOpenInstructionsHWWallet),
                    subtitle = stringRes(R.string.keepZodlOpenSubtitleHWWallet),
                    disclaimer = getDisclaimer(R.string.keep_open_keystone_warning),
                    checkboxLabel = stringRes(R.string.keepScreenOnSyncing),
                    isChecked = isChecked,
                    onCheckedChange = { onChecked() },
                    button =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                            onClick = ::onButtonClick,
                        ),
                )
            }
        }

    private fun getDisclaimer(value: Int) =
        ZashiDisclaimerState.warning(stringRes(value))

    private fun onChecked() {
        isChecked.update { !it }
    }

    private fun onButtonClick() {
        viewModelScope.launch { isKeepScreenOnDuringRestoreProvider.store(isChecked.value) }
        when (flow) {
            KeepOpenFlow.RESTORE, KeepOpenFlow.RESYNC -> navigationRouter.backToRoot()
            KeepOpenFlow.KEYSTONE -> navigationRouter.forward(KeystoneConnectedArgs)
        }
    }
}
