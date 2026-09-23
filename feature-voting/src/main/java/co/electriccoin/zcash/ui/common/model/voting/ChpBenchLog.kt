package co.electriccoin.zcash.ui.common.model.voting

import android.util.Log
import co.electriccoin.zcash.voting.BuildConfig

// CHP_BENCH — local-only cross-app timing instrumentation for a one-off benchmark against
// vizor-wallet's CHP implementation. Never intended to land upstream; do not merge this file.
internal fun chpBenchLog(
    step: String,
    roundId: String,
    elapsedMs: Long,
    bundleIndex: Int? = null,
    proposalId: Int? = null
) {
    if (BuildConfig.DEBUG) {
        val bundle = bundleIndex?.toString() ?: "-"
        val proposal = proposalId?.toString() ?: "-"
        Log.i(
            "CHP_BENCH",
            "app=zodl step=$step round=$roundId bundle=$bundle proposal=$proposal elapsedMs=$elapsedMs"
        )
    }
}
