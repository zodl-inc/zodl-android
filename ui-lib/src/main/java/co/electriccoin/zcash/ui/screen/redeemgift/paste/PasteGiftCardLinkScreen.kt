package co.electriccoin.zcash.ui.screen.redeemgift.paste

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasteGiftCardLinkScreen() {
    val vm = koinViewModel<PasteGiftCardLinkVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.onBack() }
    PasteGiftCardLinkView(state)
}

/**
 * The screen where the user pastes or types a gift card link. The link is held only by the screen's view model, never
 * in this route, since routes are saved into the instance state and the link carries a spending secret.
 */
@Serializable
data object PasteGiftCardLinkArgs
