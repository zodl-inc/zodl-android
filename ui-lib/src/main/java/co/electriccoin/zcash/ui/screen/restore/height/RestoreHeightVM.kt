package co.electriccoin.zcash.ui.screen.restore.height

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.screen.common.BlockHeightState
import co.electriccoin.zcash.ui.screen.restore.date.RestoreDateArgs
import co.electriccoin.zcash.ui.screen.restore.info.SeedInfo
import co.electriccoin.zcash.ui.screen.restore.tor.RestoreTorArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class RestoreHeightVM(
    private val restoreHeight: RestoreHeight,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val saplingActivationHeight = VersionInfo.NETWORK.saplingActivationHeight.value
    private val blockHeightText = MutableStateFlow(NumberTextFieldInnerState())

    val state: StateFlow<BlockHeightState> =
        blockHeightText
            .map { text ->
                createState(text)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = createState(blockHeightText.value)
            )

    private fun createState(blockHeight: NumberTextFieldInnerState): BlockHeightState {
        val validation = RestoreBDHeightValidator.validate(blockHeight, saplingActivationHeight)

        return BlockHeightState(
            title = stringRes(R.string.restore_title),
            logo = null,
            onBack = ::onBack,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoButtonClick,
                ),
            primaryButton =
                ButtonState(
                    stringRes(R.string.restore_bd_restore_btn),
                    onClick = ::onRestoreClick,
                    isEnabled = validation.isValid,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
            secondaryButton =
                ButtonState(
                    stringRes(R.string.restore_bd_height_btn),
                    onClick = ::onEstimateClick
                ),
            blockHeight =
                NumberTextFieldState(
                    innerState = blockHeight,
                    explicitError = validation.asErrorString(),
                    onValueChange = ::onValueChanged
                )
        )
    }

    private fun onEstimateClick() = navigationRouter.forward(RestoreDateArgs(seed = restoreHeight.seed))

    private fun onRestoreClick() {
        val validation = RestoreBDHeightValidator.validate(blockHeightText.value, saplingActivationHeight)
        val validBlockHeight = validation.blockHeight ?: return

        navigationRouter.forward(
            RestoreTorArgs(
                seed = restoreHeight.seed.trim(),
                blockHeight = validBlockHeight
            )
        )
    }

    private fun onBack() = navigationRouter.back()

    private fun onInfoButtonClick() = navigationRouter.forward(SeedInfo)

    private fun onValueChanged(state: NumberTextFieldInnerState) = blockHeightText.update { state }

    private fun RestoreBDHeightValidation.asErrorString() =
        when (error) {
            null -> null
            RestoreBDHeightValidationError.INVALID_INTEGER -> stringRes(R.string.restore_bd_text_field_error_integer)
            RestoreBDHeightValidationError.BELOW_SAPLING_ACTIVATION ->
                stringRes(
                    R.string.restore_bd_text_field_error_minimum,
                    stringResByNumber(saplingActivationHeight, minDecimals = 0)
                )
        }
}
