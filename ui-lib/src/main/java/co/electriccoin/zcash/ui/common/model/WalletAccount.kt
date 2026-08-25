package co.electriccoin.zcash.ui.common.model

import androidx.annotation.DrawableRes
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

sealed interface WalletAccount : Comparable<WalletAccount> {
    val sdkAccount: Account

    val unified: UnifiedInfo
    val sapling: SaplingInfo?

    // Ironwood shares the same unified address as Orchard (no address of its own), so this is a
    // plain balance, not an address-carrying *Info like sapling/transparent/unified. Note that
    // `unified`'s *balance* folds Orchard + Ironwood together (see observeUnified in
    // AccountDataSource) — callers that need the Orchard-only balance should use
    // GetOrchardBalanceUseCase, not this field or `unified`.
    val ironwoodBalance: WalletBalance
    val transparent: TransparentInfo
    val isSelected: Boolean
    val name: StringResource

    @get:DrawableRes
    val icon: Int

    val hdAccountIndex: Zip32AccountIndex
        get() = sdkAccount.hdAccountIndex!!

    /**
     * Total transparent + total shielded balance.
     */
    val totalBalance: Zatoshi

    /**
     * Total shielded balance including non-spendable.
     */
    val totalShieldedBalance: Zatoshi

    /**
     * Total spendable transparent balance.
     */
    val totalTransparentBalance: Zatoshi

    /**
     * Spendable & available shielded balance. Might be smaller than total shielded balance.
     */
    val spendableShieldedBalance: Zatoshi

    /**
     * Pending shielded Balance.
     */
    val pendingShieldedBalance: Zatoshi

    val isShieldedPending: Boolean
        get() = pendingShieldedBalance > Zatoshi(0)

    @Suppress("MagicNumber")
    val isShieldingAvailable: Boolean
        get() = totalTransparentBalance > Zatoshi(100000L)

    val isAllShielded: Boolean
        get() {
            val isAllShielded = totalBalance == spendableShieldedBalance
            val isAllShieldedWithTransparentDustLeft =
                totalBalance > spendableShieldedBalance &&
                    spendableShieldedBalance == totalShieldedBalance &&
                    totalTransparentBalance > Zatoshi(0) &&
                    !isShieldingAvailable

            return isAllShielded || isAllShieldedWithTransparentDustLeft
        }

    fun canSpend(amount: Zatoshi): Boolean = spendableShieldedBalance >= amount
}

data class ZashiAccount(
    override val sdkAccount: Account,
    override val unified: UnifiedInfo, // TODO [#26]: technical debt, aggregates ORCHARD + IRONWOOD, sync with iOS
    override val sapling: SaplingInfo,
    override val ironwoodBalance: WalletBalance,
    override val transparent: TransparentInfo,
    override val isSelected: Boolean,
) : WalletAccount {
    override val name: StringResource
        get() = stringRes(co.electriccoin.zcash.ui.R.string.accounts_zashi)

    override val icon: Int
        get() = R.drawable.ic_item_zashi

    override val totalBalance: Zatoshi
        get() = unified.balance.total + sapling.balance.total + transparent.balance

    override val totalShieldedBalance: Zatoshi
        get() = unified.balance.total + sapling.balance.total

    override val totalTransparentBalance: Zatoshi
        get() = transparent.balance

    override val spendableShieldedBalance: Zatoshi
        get() = unified.balance.available + sapling.balance.available

    override val pendingShieldedBalance: Zatoshi
        get() {
            val changePendingShieldedBalance =
                unified.balance.changePending + sapling.balance.changePending
            val valuePendingShieldedBalance =
                unified.balance.valuePending + sapling.balance.valuePending
            return changePendingShieldedBalance + valuePendingShieldedBalance
        }

    override fun compareTo(other: WalletAccount) =
        when (other) {
            is KeystoneAccount -> 1
            is ZashiAccount -> 0
        }
}

data class KeystoneAccount(
    override val sdkAccount: Account,
    override val unified: UnifiedInfo,
    override val ironwoodBalance: WalletBalance,
    override val transparent: TransparentInfo,
    override val isSelected: Boolean,
) : WalletAccount {
    override val icon: Int
        get() = R.drawable.ic_item_keystone

    override val name: StringResource
        get() = stringRes(co.electriccoin.zcash.ui.R.string.accounts_keystone)

    override val sapling: SaplingInfo? = null

    override val totalBalance: Zatoshi
        get() = unified.balance.total + transparent.balance

    override val totalShieldedBalance: Zatoshi
        get() = unified.balance.total

    override val totalTransparentBalance: Zatoshi
        get() = transparent.balance

    override val spendableShieldedBalance: Zatoshi
        get() = unified.balance.available

    override val pendingShieldedBalance: Zatoshi
        get() =
            unified.balance.changePending +
                unified.balance.valuePending

    override fun compareTo(other: WalletAccount) =
        when (other) {
            is KeystoneAccount -> 0
            is ZashiAccount -> -1
        }
}

data class UnifiedInfo(
    val address: WalletAddress.Unified,
    val balance: WalletBalance
)

data class TransparentInfo(
    val address: WalletAddress.Transparent,
    val balance: Zatoshi
)

data class SaplingInfo(
    val address: WalletAddress.Sapling,
    val balance: WalletBalance
)

/**
 * The single spendability primitive the whole app validates against, so a typing-time check and the
 * proposal-time check in the SDK can never disagree. A null receiver (no account selected yet) can
 * spend nothing.
 */
fun WalletAccount?.canSpend(amount: Zatoshi): Boolean = this?.canSpend(amount) ?: false

/**
 * The spendable balance a screen may display for a possibly-not-yet-selected account, zero when no
 * account is available.
 */
val WalletAccount?.totalSpendableBalance: Zatoshi
    get() = this?.spendableShieldedBalance ?: Zatoshi(0)
