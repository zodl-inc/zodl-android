package co.electriccoin.zcash.ui.screen.restore.estimation

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.screen.common.EstimatedBlockHeightState
import co.electriccoin.zcash.ui.screen.restore.info.SeedInfo
import co.electriccoin.zcash.ui.screen.restore.tor.RestoreTorArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RestoreEstimationVM(
    private val args: RestoreEstimationArgs,
    private val navigationRouter: NavigationRouter,
    private val copyToClipboard: CopyToClipboardUseCase
) : ViewModel() {
    val state: StateFlow<EstimatedBlockHeightState> = MutableStateFlow(createState()).asStateFlow()

    private fun createState() =
        EstimatedBlockHeightState(
            title = stringRes(R.string.root_existingWallet_restore),
            logo = null,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoButtonClick,
                ),
            onBack = ::onBack,
            blockHeightText = stringResByNumber(args.blockHeight, 0),
            copyButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.receive_copy),
                    icon = R.drawable.ic_copy,
                    onClick = ::onCopyClick
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.root_existingWallet_restore),
                    onClick = ::onRestoreClick,
                    hapticFeedbackType = HapticFeedbackType.Confirm
                ),
        )

    private fun onCopyClick() {
        copyToClipboard(
            value = args.blockHeight.toString()
        )
    }

    private fun onRestoreClick() {
        navigationRouter.forward(RestoreTorArgs(seed = args.seed.trim(), blockHeight = args.blockHeight))
    }

    private fun onBack() = navigationRouter.back()

    private fun onInfoButtonClick() = navigationRouter.forward(SeedInfo)
}
