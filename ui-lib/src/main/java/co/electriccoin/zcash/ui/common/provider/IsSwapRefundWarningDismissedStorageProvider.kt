package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.SwapMode

/**
 * One "Don't show this message again" flag per surface that raises the sub-$300 refund warning, so
 * dismissing it on Swap still warns the first time a small CrossPay payment is made, and vice
 * versa. The three [SwapMode]s map one to one onto the three surfaces: [SwapMode.EXACT_INPUT] is
 * Swap from ZEC, [SwapMode.FLEX_INPUT] Swap into ZEC and [SwapMode.EXACT_OUTPUT] CrossPay.
 *
 * The flags live in standard preferences, which
 * [co.electriccoin.zcash.ui.screen.deletewallet.ResetZashiUseCase] wipes via `clearPreferences()`,
 * so a wallet reset re-arms every surface - the same clear-on-reset behaviour as iOS.
 */
interface IsSwapRefundWarningDismissedStorageProvider {
    suspend fun get(mode: SwapMode): Boolean

    suspend fun store(mode: SwapMode, value: Boolean)
}

class IsSwapRefundWarningDismissedStorageProviderImpl(
    private val preferenceHolder: StandardPreferenceProvider,
) : IsSwapRefundWarningDismissedStorageProvider {
    private val defaults: Map<SwapMode, BooleanPreferenceDefault> =
        mapOf(
            SwapMode.EXACT_INPUT to surfaceDefault("swap_from_zec"),
            SwapMode.FLEX_INPUT to surfaceDefault("swap_into_zec"),
            SwapMode.EXACT_OUTPUT to surfaceDefault("crosspay")
        )

    override suspend fun get(mode: SwapMode): Boolean = defaults.getValue(mode).getValue(preferenceHolder())

    override suspend fun store(mode: SwapMode, value: Boolean) {
        defaults.getValue(mode).putValue(preferenceHolder(), value)
    }
}

private fun surfaceDefault(surface: String) =
    BooleanPreferenceDefault(
        key = PreferenceKey("is_swap_refund_warning_dismissed_$surface"),
        defaultValue = false
    )
