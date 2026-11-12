package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.design.component.SendMaxChipState
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * Rules shared by every Max chip (Send, Swap and Pay).
 *
 * A max amount in zatoshi becomes ZEC through the SDK's exact `Zatoshi.toZecExact()` only. The SDK's
 * `convertZatoshiToZec` is a display helper that rounds to 6 decimals, which can land above the max.
 */

/**
 * Floors this value to [scale] decimal places, never rounding up. Max amounts are read back by the insufficient-funds
 * checks, so rounding up (even by half an ulp) would flag a just-tapped Max as over the balance.
 */
fun BigDecimal.floorToScale(scale: Int): BigDecimal = setScale(scale, RoundingMode.FLOOR)

/**
 * The state of a Max chip (Figma "Send Max: Round 2": two states, no spinner).
 *
 * It can be tapped when the account has something to spend and [isExtraEnabled] holds (the screen's own condition,
 * such as a valid recipient or usable prices). While the balances have not been emitted yet it is disabled. A Max
 * request in flight does not change it: the chip stays enabled-looking and the view model's in-flight guard
 * ([co.electriccoin.zcash.ui.common.viewmodel.MaxAmountRequester.isInFlight]) ignores re-taps.
 */
fun maxChipState(
    spendability: Spendability,
    isExtraEnabled: Boolean,
    onClick: () -> Unit,
): SendMaxChipState =
    SendMaxChipState(
        isEnabled = spendability is Spendability.Some && isExtraEnabled,
        onClick = onClick
    )
