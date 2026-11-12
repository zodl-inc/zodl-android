package co.electriccoin.zcash.ui.common.viewmodel

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.presentation
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.MaxSendTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs at most one max-amount request at a time for a screen, in [scope].
 *
 * [onFailed] is called when the max amount cannot be determined, with the reason; screens pass
 * [MaxFeedbackController.onFailed], which picks the too-many-notes sheet or the generic banner (see
 * [MaxSendAmountFailure.presentation]). The use case has already logged why (class name only).
 *
 * A successful request also pins the Max intent's [activeBaseline] and [activeReserve] (see [MaxFillPolicy]). Only
 * Swap and Pay read them, to re-fill on quote refreshes; Send ignores them and keeps the amount fixed at tap.
 */
class MaxAmountRequester(
    private val scope: CoroutineScope,
    private val getMaxSendAmount: GetMaxSendAmountUseCase,
    private val onFailed: (MaxSendAmountFailure) -> Unit,
) {
    private val inFlight = MutableStateFlow(false)

    /** True from the moment a request is accepted until it completes, fails or is cancelled. */
    val isInFlight: StateFlow<Boolean> = inFlight.asStateFlow()

    private val baseline = MutableStateFlow<Zatoshi?>(null)

    /**
     * The amount pinned by the last successful Max tap while the Max intent is active, null otherwise. It is the
     * amount that was filled at tap and is the ceiling for every later refresh. [clearIntent] resets it.
     */
    val activeBaseline: StateFlow<Zatoshi?> = baseline.asStateFlow()

    private val reserve = MutableStateFlow<Zatoshi?>(null)

    /**
     * `spendable at tap - baseline` of the last successful Max tap while the Max intent is active, null otherwise:
     * the real fee of the tap-time proposal plus the buffer. It is what [MaxFillPolicy.netSpendable] subtracts from
     * the spendable balance of a later refresh. Null also when the tap passed no `spendableAtTap`.
     */
    val activeReserve: StateFlow<Zatoshi?> = reserve.asStateFlow()

    private var job: Job? = null

    /**
     * Identifies the current request. A cancelled request may still be unwinding when its successor starts; it must
     * not clear the successor's in-flight flag.
     */
    private var generation = 0

    /**
     * Requests the max amount for [target] and hands it to [onResolved], unless a request is already in flight.
     *
     * The flag is raised before anything is launched, so a second tap in the same frame is ignored.
     *
     * @param spendableAtTap the shielded spendable balance at the moment of the tap, read synchronously by the
     * caller. Swap and Pay pass it so the Max intent can record its reserve; Send keeps the amount fixed and may
     * leave it out.
     * @return whether a request was started
     */
    fun request(
        target: MaxSendTarget,
        spendableAtTap: Zatoshi? = null,
        onResolved: suspend (MaxSendAmount) -> Unit
    ): Boolean {
        if (inFlight.value) return false
        val requestGeneration = ++generation
        inFlight.value = true
        job =
            scope.launch {
                try {
                    val max =
                        try {
                            getMaxSendAmount(target)
                        } catch (_: InsufficientFundsException) {
                            onFailed(MaxSendAmountFailure.OTHER)
                            null
                        } catch (e: MaxSendAmountUnavailableException) {
                            onFailed(e.reason)
                            null
                        }
                    if (max != null) {
                        baseline.value = max.amount
                        reserve.value = spendableAtTap?.let { Zatoshi(maxOf(0L, it.value - max.amount.value)) }
                        onResolved(max)
                    }
                } finally {
                    if (requestGeneration == generation) inFlight.value = false
                }
            }
        return true
    }

    /** Cancels the running request, if any; its result is never delivered. */
    fun cancel() {
        generation++
        job?.cancel()
        job = null
        inFlight.value = false
    }

    /** Ends the Max intent, because the user edited the amount by hand: no more refreshes follow. */
    fun clearIntent() {
        baseline.value = null
        reserve.value = null
    }
}
