package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.model.Zatoshi

/**
 * What an account can spend from its shielded pools right now.
 *
 * The transparent-only reason uses [WalletAccount.isShieldingAvailable], the same threshold that offers shielding
 * elsewhere in the app. The reasons do not change what Max or the primary button do (they are all "not now"); they
 * only say why.
 */
sealed interface Spendability {
    /**
     * Whether Review / Get a quote may be used: the shielded spendable balance is known and above zero. Balance driven
     * only: syncing ([Determining]), confirming, transparent-only and empty wallets are all false, and nothing about
     * a recipient or an amount is considered here.
     */
    val allowsPrimaryAction: Boolean
        get() = this is Some

    /**
     * The shielded amount that can be spent now: the amount for [Some], zero for [Nothing], and null while the
     * balances are not known ([Determining]), when a caller must wait. Use `spendability?.shieldedSpendableOrNull()`
     * for an account whose spendability may not exist yet.
     */
    fun shieldedSpendableOrNull(): Zatoshi? =
        when (this) {
            is Some -> amount
            is Nothing -> Zatoshi(0)
            Determining -> null
        }

    /** The balances have not been emitted yet; the only state in which a spinner is appropriate. */
    data object Determining : Spendability

    /** Nothing is spendable from the shielded pools; [reason] says why. */
    data class Nothing(
        val reason: Reason
    ) : Spendability {
        /** Why nothing is spendable. At most one applies; [TransparentOnly] wins over [FundsConfirming]. */
        enum class Reason {
            /** Transparent funds above the shielding threshold: the pill shows 0 and its sheet offers Shield. */
            TransparentOnly,

            /** Shielded funds are pending; the user can only wait. */
            FundsConfirming,

            /** Nothing to wait for and nothing to shield. */
            Empty
        }
    }

    data class Some(
        val amount: Zatoshi
    ) : Spendability
}

val WalletAccount.spendability: Spendability
    get() {
        val spendable = spendableShieldedBalance ?: return Spendability.Determining
        // Both balances arrive together; until the pending one has, the account's balances are not loaded.
        pendingShieldedBalance ?: return Spendability.Determining
        if (spendable > Zatoshi(0)) return Spendability.Some(spendable)
        // The transparent balance arrives with the shielded ones; without it the reason is not known yet.
        val isShieldingAvailable = isShieldingAvailable ?: return Spendability.Determining
        val reason =
            when {
                isShieldingAvailable -> Spendability.Nothing.Reason.TransparentOnly
                isShieldedPending == true -> Spendability.Nothing.Reason.FundsConfirming
                else -> Spendability.Nothing.Reason.Empty
            }
        return Spendability.Nothing(reason)
    }
