package co.electriccoin.zcash.ui.screen.rebrand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.ExternalUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class RebrandVM(
    private val navigationRouter: NavigationRouter,
    private val walletRepository: WalletRepository,
) : ViewModel() {

    internal val state = MutableStateFlow(
        RebrandState(
            info = ButtonState(
                text = stringRes(R.string.rebrand_button_more),
            ) {
                navigationRouter.forward(ExternalUrl("https://zodl.com/zashi-is-becoming-zodl/"))
            },
            next = ButtonState(
                text = stringRes(R.string.rebrand_button_next),
            ) {
                viewModelScope.launch {
                    walletRepository.acknowledgeRebrand()
                    navigationRouter.back()
                }
            },
        )
    )
}
