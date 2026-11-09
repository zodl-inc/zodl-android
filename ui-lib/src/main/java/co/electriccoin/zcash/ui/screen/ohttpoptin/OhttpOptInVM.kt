package co.electriccoin.zcash.ui.screen.ohttpoptin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.OptOutOfOhttpUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OhttpOptInVM(
    private val persistServerSelection: PersistServerSelectionUseCase,
    private val optOutOfOhttp: OptOutOfOhttpUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<OhttpOptInState> =
        MutableStateFlow(
            OhttpOptInState(
                title = stringRes(R.string.ohttpOptIn_title),
                body = stringRes(R.string.ohttpOptIn_body),
                items =
                    listOf(
                        OhttpOptInItemState(
                            icon = ImageResource.ByDrawable(R.drawable.ic_ohttp_lock),
                            title = stringRes(R.string.ohttpInfo_item1Title),
                            subtitle = stringRes(R.string.ohttpInfo_item1Body),
                        ),
                        OhttpOptInItemState(
                            icon = ImageResource.ByDrawable(R.drawable.ic_ohttp_shield),
                            title = stringRes(R.string.ohttpInfo_item2Title),
                            subtitle = stringRes(R.string.ohttpInfo_item2Body),
                        ),
                        OhttpOptInItemState(
                            icon = ImageResource.ByDrawable(R.drawable.ic_ohttp_check_circle),
                            title = stringRes(R.string.ohttpInfo_item3Title),
                            subtitle = stringRes(R.string.ohttpInfo_item3Body),
                        ),
                    ),
                notNowText = stringRes(R.string.ohttpOptIn_notNow),
                turnOnText = stringRes(R.string.ohttpOptIn_turnOn),
                onTurnOn = ::onTurnOn,
                onNotNow = ::onNotNow,
                onBack = ::onBack,
            )
        )

    private fun onTurnOn() =
        viewModelScope.launch {
            persistServerSelection.persistPrivate()
            navigationRouter.back()
        }

    /** The only opt-out: the home widget is gone for good. Closing the screen (X / back) keeps it. */
    private fun onNotNow() =
        viewModelScope.launch {
            optOutOfOhttp()
            navigationRouter.back()
        }

    private fun onBack() = navigationRouter.back()
}
