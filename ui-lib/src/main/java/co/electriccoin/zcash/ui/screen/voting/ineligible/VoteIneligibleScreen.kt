package co.electriccoin.zcash.ui.screen.voting.ineligible

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.usecase.IneligibilityReason
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteIneligibleScreen(args: VoteIneligibleArgs) {
    val vm = koinViewModel<VoteIneligibleVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteIneligibleView(it)
    }
}

@Serializable
data class VoteIneligibleArgs(
    /** Serialized IneligibilityReason name; defaults to NO_NOTES for backwards compat. */
    val reason: String = IneligibilityReason.NO_NOTES.name,
    val snapshotHeight: Long = 0L,
    /** Eligible weight in zatoshi; used only for BALANCE_TOO_LOW reason. */
    val balanceZatoshi: Long = 0L,
)
