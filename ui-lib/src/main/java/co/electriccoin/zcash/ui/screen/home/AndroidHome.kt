@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.di.koinActivityViewModel
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarVM
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetArgs
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetVM
import co.electriccoin.zcash.ui.screen.transactionhistory.widget.ActivityWidgetVM
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun AndroidHome() {
    val topAppBarVM = koinActivityViewModel<ZashiTopAppBarVM>()
    val balanceWidgetVM =
        koinViewModel<BalanceWidgetVM> {
            parametersOf(
                BalanceWidgetArgs(
                    isBalanceButtonEnabled = false,
                    isExchangeRateButtonEnabled = true,
                    showDust = false,
                    isBalanceBreakdownEnabled = true,
                )
            )
        }
    val homeVM = koinViewModel<HomeVM>()
    val activityWidgetVM = koinViewModel<ActivityWidgetVM>()
    val appBarState by topAppBarVM.state.collectAsStateWithLifecycle()
    val balanceState by balanceWidgetVM.state.collectAsStateWithLifecycle()
    val state by homeVM.state.collectAsStateWithLifecycle()
    homeVM.uiLifecyclePipeline.collectAsStateWithLifecycle()
    val transactionWidgetState by activityWidgetVM.state.collectAsStateWithLifecycle()

    state?.let {
        HomeView(
            appBarState = appBarState,
            balanceWidgetState = balanceState,
            state = it,
            transactionWidgetState = transactionWidgetState
        )
    }
}

@Serializable
object HomeArgs
