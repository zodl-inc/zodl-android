package co.electriccoin.zcash.ui.screen.swap.mismatch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.crash.android.GlobalCrashReporter
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchException
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import co.electriccoin.zcash.ui.common.model.swapQuoteMismatchSignal
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapQuoteMismatchScreen(args: SwapQuoteMismatchArgs) {
    val vm = koinViewModel<SwapQuoteMismatchVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapQuoteMismatchView(state)
}

@Serializable
data class SwapQuoteMismatchArgs(
    val provider: String,
    val mode: SwapMode,
    val originTokenTicker: String,
    val originChainTicker: String,
    val destinationTokenTicker: String,
    val destinationChainTicker: String,
    val mismatchType: SwapQuoteMismatchType,
    val depositAddress: String?
)

/**
 * The mismatch sheet's arguments for a rejected quote, or null when the error is not a rejection carrying
 * the report context the repository attaches — the caller then stays on the generic quote-error path.
 *
 * Only [SwapQuoteMismatchException.Reported] carries that context, and the repository builds one for
 * every rejection it stores, so the sheet's arguments are read without a null check. A mismatch that
 * somehow reached here unreported would be a routing bug: it takes the generic path, but is reported
 * through the same sanitized crash-monitoring signal the repository emits rather than passing silently.
 */
internal fun SwapQuoteData.Error.toMismatchArgs(): SwapQuoteMismatchArgs? {
    val mismatch = exception as? SwapQuoteMismatchException.Reported
    if (mismatch == null) {
        (exception as? SwapQuoteMismatchException)?.let {
            GlobalCrashReporter.reportCaughtException(swapQuoteMismatchSignal(it))
        }
        return null
    }
    return SwapQuoteMismatchArgs(
        provider = mismatch.provider,
        mode = mode,
        originTokenTicker = mismatch.originAsset.tokenTicker,
        originChainTicker = mismatch.originAsset.chainTicker,
        destinationTokenTicker = mismatch.destinationAsset.tokenTicker,
        destinationChainTicker = mismatch.destinationAsset.chainTicker,
        mismatchType = mismatch.type,
        depositAddress = mismatch.depositAddress
    )
}
