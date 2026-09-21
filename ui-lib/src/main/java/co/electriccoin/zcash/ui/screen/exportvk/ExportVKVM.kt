package co.electriccoin.zcash.ui.screen.exportvk

import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.usecase.GetVKUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.screen.exportvk.confirm.ExportVKConfirmArgs
import co.electriccoin.zcash.ui.screen.exportvk.detail.VKDetailArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class ExportVKVM(
    getVK: GetVKUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val selected = MutableStateFlow<VKType?>(null)

    val state =
        combine(selected, getVK.observeAvailability()) { selected, availability ->
            createState(selected, availability)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createState(
        selected: VKType?,
        availability: Map<VKType, Boolean>
    ) = ExportVKState(
        description =
            styledStringResource(
                R.string.exportViewingKey_description,
                styledStringResource(
                    R.string.exportViewingKey_description_app,
                    StyledStringStyle(fontWeight = FontWeight.SemiBold)
                )
            ),
        options =
            VKType.entries.map { type ->
                VKOptionState(
                    type = type,
                    title = stringRes(type.titleRes),
                    subtitle = stringRes(type.subtitleRes),
                    isChecked = type == selected,
                    onClick = { onOptionClick(type) }
                )
            },
        continueButton =
            ButtonState(
                text = stringRes(R.string.exportViewingKey_continue),
                isEnabled = selected != null && availability[selected] == true,
                onClick = ::onContinueClick
            ),
        onBack = ::onBack
    )

    private fun onOptionClick(type: VKType) = selected.update { type }

    private fun onContinueClick() {
        when (selected.value) {
            VKType.INCOMING -> navigationRouter.replace(VKDetailArgs(VKType.INCOMING))
            VKType.FULL -> navigationRouter.forward(ExportVKConfirmArgs)
            null -> Unit
        }
    }

    private fun onBack() = navigationRouter.back()
}

private val VKType.titleRes: Int
    get() =
        when (this) {
            VKType.INCOMING -> R.string.exportViewingKey_option_incoming
            VKType.FULL -> R.string.exportViewingKey_option_full
        }

private val VKType.subtitleRes: Int
    get() =
        when (this) {
            VKType.INCOMING -> R.string.exportViewingKey_option_incoming_desc
            VKType.FULL -> R.string.exportViewingKey_option_full_desc
        }
