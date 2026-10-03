package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RedeemGiftScreen(args: RedeemGiftArgs) {
    val vm = koinViewModel<RedeemGiftVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.onBack() }
    RedeemGiftView(state)
}

/**
 * @param linkId the id of the gift card link in
 * [co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore]. The link itself never goes into a route, since
 * routes are saved into the instance state and the link carries a spending secret.
 */
@Serializable
data class RedeemGiftArgs(
    val linkId: String
)
