package co.electriccoin.zcash.ui.screen.ironwood

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

private const val GUIDE_URL = "https://support.zodl.com/article/42-moving-your-funds-to-ironwood"

/**
 * Marks the announcement as shown at display time, not at dismissal. Home forwards here from a
 * `WhileSubscribed`-retained state flow derived from [WalletRepository.isIronwoodAnnouncementShown];
 * flipping the flag only on dismissal left that flow's retained value a stale `true` while this
 * screen covered Home, so returning re-triggered the navigation and the announcement appeared
 * twice in a row. Marking on display flips the flag while Home's upstream is still subscribed,
 * and also covers dismissal via system back.
 */
class IronwoodAnnouncementVM(
    private val navigationRouter: NavigationRouter,
    walletRepository: WalletRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            walletRepository.markIronwoodAnnouncementShown()
        }
    }

    val state =
        MutableStateFlow(
            IronwoodAnnouncementState(
                onGuideClick = { navigationRouter.forward(ExternalUrl(GUIDE_URL)) },
                primaryButton =
                    ButtonState(
                        text = stringRes(R.string.ironwood_announcement_primary_button),
                    ) {
                        navigationRouter.back()
                    },
            )
        )
}
