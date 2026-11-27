package co.electriccoin.zcash.ui.preference

import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.IntegerPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.model.OnboardingState

object StandardPreferenceKeys {
    /**
     * State defining whether the user has completed any of the onboarding wallet states.
     */
    val ONBOARDING_STATE =
        IntegerPreferenceDefault(
            PreferenceKey("onboarding_state"),
            OnboardingState.NONE.toNumber()
        )

    val IS_BACKGROUND_SYNC_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_background_sync_enabled"), true)

    /**
     * Screens or flows protected by required authentication
     */
    val IS_APP_ACCESS_AUTHENTICATION =
        BooleanPreferenceDefault(
            PreferenceKey("IS_APP_ACCESS_AUTHENTICATION"),
            true
        )

    val IS_HIDE_BALANCES =
        BooleanPreferenceDefault(
            PreferenceKey("IS_HIDE_BALANCES"),
            false
        )
}
