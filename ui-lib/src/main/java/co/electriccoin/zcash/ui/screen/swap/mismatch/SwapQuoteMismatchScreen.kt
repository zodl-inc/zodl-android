package co.electriccoin.zcash.ui.screen.swap.mismatch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchException
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
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
 */
internal fun SwapQuoteData.Error.toMismatchArgs(): SwapQuoteMismatchArgs? {
    val mismatch = exception as? SwapQuoteMismatchException ?: return null
    val provider = mismatch.provider
    val originAsset = mismatch.originAsset
    val destinationAsset = mismatch.destinationAsset
    return if (provider != null && originAsset != null && destinationAsset != null) {
        SwapQuoteMismatchArgs(
            provider = provider,
            mode = mode,
            originTokenTicker = originAsset.tokenTicker,
            originChainTicker = originAsset.chainTicker,
            destinationTokenTicker = destinationAsset.tokenTicker,
            destinationChainTicker = destinationAsset.chainTicker,
            mismatchType = mismatch.type,
            depositAddress = mismatch.depositAddress
        )
    } else {
        null
    }
}
