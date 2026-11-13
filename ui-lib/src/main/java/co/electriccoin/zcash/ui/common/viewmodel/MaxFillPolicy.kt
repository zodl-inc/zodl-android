package co.electriccoin.zcash.ui.common.viewmodel

import cash.z.ecc.android.sdk.model.Zatoshi

/**
 * How a Swap or Pay Max fill follows quote refreshes while the Max intent is active.
 *
 * At the Max tap, [MaxAmountRequester.activeBaseline] pins `baseline = spendable at tap - buffer`. On every quote or
 * price refresh the fill is recomputed from the baseline and what is spendable now (net of the reserve captured at
 * the tap, like the baseline), so it can fall (funds were spent or locked) and rise back, but never above the
 * baseline: funds that become spendable after the tap are not picked up. A manual edit calls
 * [MaxAmountRequester.clearIntent] and refreshes stop. Send does not refresh; it keeps the amount fixed at tap.
 */
object MaxFillPolicy {
    /**
     * The shielded spendable balance as comparable with the baseline: [currentSpendable] minus [reserveAtTap],
     * floored at zero.
     *
     * [reserveAtTap] is `spendable at tap - baseline` ([MaxAmountRequester.activeReserve]), measured when the Max
     * tap was resolved. It therefore equals the real fee of the tap-time proposal (5000 zat per action, so it grows
     * with the number of notes spent) plus the buffer, not a single marginal fee. The baseline is net of that
     * reserve, so the balance it is compared with must be net of it too; with the gross balance a drop to just above
     * the baseline would keep the fill at the baseline, and the quote or proposal would then fail for fees.
     */
    fun netSpendable(
        currentSpendable: Zatoshi,
        reserveAtTap: Zatoshi
    ): Zatoshi = Zatoshi(maxOf(0L, currentSpendable.value - reserveAtTap.value))

    /**
     * @param netSpendable what is spendable now, net of the fee and the buffer (see [netSpendable]); the baseline is
     * net of them already, so the two are compared like for like
     * @param adjust applies the quote's ZEC-side fees and limits to a candidate amount (screen-specific)
     * @return `adjust(min(baseline, netSpendable))`, never above [baseline]
     */
    fun refresh(
        baseline: Zatoshi,
        netSpendable: Zatoshi,
        adjust: (Zatoshi) -> Zatoshi
    ): Zatoshi {
        // Zatoshi is never negative, so only the ceiling needs enforcing
        return minOf(baseline, adjust(minOf(baseline, netSpendable)))
    }
}
