package co.electriccoin.zcash.ui.screen.exportvk.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.exportvk.ExportVKArgs
import co.electriccoin.zcash.ui.screen.exportvk.detail.VKDetailArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class ExportVKConfirmVM(
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val checked = MutableStateFlow(setOf<Int>())

    val state =
        checked
            .map(::createState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun createState(checked: Set<Int>) =
        ExportVKConfirmState(
            checkboxes =
                CONSENT_TITLES.mapIndexed { index, title ->
                    CheckboxState(
                        title = stringRes(title),
                        isChecked = index in checked,
                        onClick = { onCheckboxClick(index) }
                    )
                },
            cancelButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
                    onClick = ::onBack
                ),
            exportButton =
                ButtonState(
                    text = stringRes(R.string.exportViewingKey_confirm_export),
                    isEnabled = checked.size == CONSENT_TITLES.size,
                    onClick = ::onExportClick
                ),
            onBack = ::onBack
        )

    private fun onCheckboxClick(index: Int) =
        checked.update { current -> if (index in current) current - index else current + index }

    private fun onExportClick() = navigationRouter.replaceFrom(ExportVKArgs::class, VKDetailArgs(VKType.FULL))

    private fun onBack() = navigationRouter.back()
}

private val CONSENT_TITLES =
    listOf(
        R.string.exportViewingKey_confirm_check_reveals,
        R.string.exportViewingKey_confirm_check_trust,
        R.string.exportViewingKey_confirm_check_undone,
    )
