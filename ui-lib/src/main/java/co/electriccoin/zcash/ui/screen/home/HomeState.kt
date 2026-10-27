package co.electriccoin.zcash.ui.screen.home

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.error.WalletErrorMessageState

/**
 * @param firstButton Receive.
 * @param secondButton Send, or null while the selected account cannot spend; the button is then
 *        hidden, not disabled.
 * @param thirdButton Pay, or null while the selected account cannot spend.
 * @param fourthButton Swap.
 */
data class HomeState(
    val firstButton: BigIconButtonState,
    val secondButton: BigIconButtonState?,
    val thirdButton: BigIconButtonState?,
    val fourthButton: BigIconButtonState,
    val message: HomeMessageState?,
    val isSyncComplete: Boolean
) {
    companion object {
        val preview: HomeState =
            HomeState(
                firstButton = previewButton(R.drawable.ic_home_receive),
                secondButton = previewButton(R.drawable.ic_home_send),
                thirdButton = previewButton(R.drawable.ic_home_scan),
                fourthButton = previewButton(R.drawable.ic_home_buy),
                message = WalletErrorMessageState(onClick = {}),
                isSyncComplete = true
            )

        val previewCannotSpend: HomeState =
            preview.copy(
                secondButton = null,
                thirdButton = null,
                message = null,
            )

        private fun previewButton(icon: Int) =
            BigIconButtonState(
                text = stringRes("Text"),
                icon = icon,
                onClick = {}
            )
    }
}
