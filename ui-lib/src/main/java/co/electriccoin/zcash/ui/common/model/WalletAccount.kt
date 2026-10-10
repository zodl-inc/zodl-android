package co.electriccoin.zcash.ui.common.model

import androidx.annotation.DrawableRes
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

sealed interface WalletAccount : Comparable<WalletAccount> {
    val sdkAccount: Account

    val unifiedAddress: String
    val transparentAddress: String
    val saplingAddress: String?

    /**
     * TODO [#26]: technical debt, aggregates ORCHARD + IRONWOOD, sync with iOS
     *
     * Folds Orchard + Ironwood together. Callers that need the Orchard-only balance should use
     * [orchardBalance] or GetOrchardBalanceUseCase, not this field. Null while either contributing
     * balance has not loaded yet — both pools come from the same `AccountBalance` entry, so their
     * nullity coincides.
     */
    val unifiedBalance: WalletBalance?
        get() = orchardBalance?.let { orchard -> ironwoodBalance?.let { ironwood -> orchard + ironwood } }

    /**
     * The Orchard-only balance, null while the balance snapshot has not loaded yet.
     */
    val orchardBalance: WalletBalance?

    /**
     * Null for hardware-wallet accounts (Keystone, Ledger), and while the balance snapshot has not
     * loaded yet.
     */
    val saplingBalance: WalletBalance?

    /**
     * Ironwood shares the same unified address as Orchard (no address of its own). Null while the
     * balance snapshot has not loaded yet.
     */
    val ironwoodBalance: WalletBalance?
    val transparentBalance: Zatoshi?
    val isSelected: Boolean

    val name: StringResource

    @get:DrawableRes
    val icon: Int

    /**
     * The account's ZIP 32 index, or null when it is not known, as for a Ledger account without a
     * stored binding.
     */
    val hdAccountIndex: Zip32AccountIndex?
        get() = sdkAccount.hdAccountIndex

    /**
     * Total transparent + total shielded balance. Null while any contributing balance has not
     * loaded yet.
     */
    val totalBalance: Zatoshi?

    /**
     * Total shielded balance including non-spendable. Null while any contributing balance has not
     * loaded yet.
     */
    val totalShieldedBalance: Zatoshi?

    /**
     * Total spendable transparent balance. Null while the transparent balance has not loaded yet.
     */
    val totalTransparentBalance: Zatoshi?

    /**
     * Spendable & available shielded balance. Might be smaller than total shielded balance. Null
     * while any contributing balance has not loaded yet.
     */
    val spendableShieldedBalance: Zatoshi?

    /**
     * Pending shielded Balance. Null while any contributing balance has not loaded yet.
     */
    val pendingShieldedBalance: Zatoshi?

    val isShieldedPending: Boolean?
        get() = pendingShieldedBalance?.let { it > Zatoshi(0) }

    @Suppress("MagicNumber")
    val isShieldingAvailable: Boolean?
        get() = totalTransparentBalance?.let { it > Zatoshi(100000L) }

    val isAllShielded: Boolean?
        get() {
            val totalBalance = totalBalance ?: return null
            val spendableShieldedBalance = spendableShieldedBalance ?: return null
            val totalShieldedBalance = totalShieldedBalance ?: return null
            val totalTransparentBalance = totalTransparentBalance ?: return null
            val isShieldingAvailable = isShieldingAvailable ?: return null

            val isAllShielded = totalBalance == spendableShieldedBalance
            val isAllShieldedWithTransparentDustLeft =
                totalBalance > spendableShieldedBalance &&
                    spendableShieldedBalance == totalShieldedBalance &&
                    totalTransparentBalance > Zatoshi(0) &&
                    !isShieldingAvailable

            return isAllShielded || isAllShieldedWithTransparentDustLeft
        }

    /**
     * Whether [amount] can currently be spent from this account. Null while the spendable shielded
     * balance has not loaded yet — not enough information to answer, never coerced to true or
     * false.
     */
    fun canSpend(amount: Zatoshi): Boolean? = spendableShieldedBalance?.let { it >= amount }

    /**
     * This account's [LoadedAccountBalances], bundled once every contributing figure has loaded.
     * Null while any of them has not loaded yet.
     */
    val loadedBalances: LoadedAccountBalances?
        get() =
            LoadedAccountBalances(
                isAllShielded = isAllShielded ?: return null,
                totalBalance = totalBalance ?: return null,
                totalShieldedBalance = totalShieldedBalance ?: return null,
                totalTransparentBalance = totalTransparentBalance ?: return null,
                spendableShieldedBalance = spendableShieldedBalance ?: return null,
                pendingShieldedBalance = pendingShieldedBalance ?: return null,
                isShieldedPending = isShieldedPending ?: return null,
                isShieldingAvailable = isShieldingAvailable ?: return null,
                transparentBalance = transparentBalance ?: return null,
            )
}

/**
 * A [WalletAccount]'s derived balance figures, bundled once they have all loaded so callers work
 * with plain non-null values instead of each re-deriving (and re-null-checking) the same figures
 * individually.
 */
data class LoadedAccountBalances(
    val isAllShielded: Boolean,
    val totalBalance: Zatoshi,
    val totalShieldedBalance: Zatoshi,
    val totalTransparentBalance: Zatoshi,
    val spendableShieldedBalance: Zatoshi,
    val pendingShieldedBalance: Zatoshi,
    val isShieldedPending: Boolean,
    val isShieldingAvailable: Boolean,
    val transparentBalance: Zatoshi,
)

data class ZashiAccount(
    override val sdkAccount: Account,
    override val unifiedAddress: String,
    override val transparentAddress: String,
    override val saplingAddress: String,
    override val orchardBalance: WalletBalance?,
    override val saplingBalance: WalletBalance?,
    override val ironwoodBalance: WalletBalance?,
    override val transparentBalance: Zatoshi?,
    override val isSelected: Boolean,
) : WalletAccount {
    override val name: StringResource
        get() = stringRes(co.electriccoin.zcash.ui.R.string.accounts_zashi)

    override val hdAccountIndex: Zip32AccountIndex
        get() = checkNotNull(sdkAccount.hdAccountIndex)

    override val icon: Int
        get() = R.drawable.ic_item_zashi

    override val totalBalance: Zatoshi?
        get() {
            val unifiedTotal = unifiedBalance?.total ?: return null
            val saplingTotal = saplingBalance?.total ?: return null
            val transparent = transparentBalance ?: return null
            return unifiedTotal + saplingTotal + transparent
        }

    override val totalShieldedBalance: Zatoshi?
        get() {
            val unifiedTotal = unifiedBalance?.total ?: return null
            val saplingTotal = saplingBalance?.total ?: return null
            return unifiedTotal + saplingTotal
        }

    override val totalTransparentBalance: Zatoshi?
        get() = transparentBalance

    override val spendableShieldedBalance: Zatoshi?
        get() {
            val unifiedAvailable = unifiedBalance?.available ?: return null
            val saplingAvailable = saplingBalance?.available ?: return null
            return unifiedAvailable + saplingAvailable
        }

    override val pendingShieldedBalance: Zatoshi?
        get() {
            val unified = unifiedBalance ?: return null
            val sapling = saplingBalance ?: return null
            val changePendingShieldedBalance = unified.changePending + sapling.changePending
            val valuePendingShieldedBalance = unified.valuePending + sapling.valuePending
            return changePendingShieldedBalance + valuePendingShieldedBalance
        }

    override fun compareTo(other: WalletAccount) =
        when (other) {
            is KeystoneAccount -> 1
            is LedgerAccount -> 1
            is ZashiAccount -> 0
        }
}

/**
 * An account whose spend authority lives on a hardware wallet.
 */
sealed interface HWWalletAccount : WalletAccount

data class KeystoneAccount(
    override val sdkAccount: Account,
    override val unifiedAddress: String,
    override val transparentAddress: String,
    override val orchardBalance: WalletBalance?,
    override val ironwoodBalance: WalletBalance?,
    override val transparentBalance: Zatoshi?,
    override val isSelected: Boolean,
) : HWWalletAccount {
    override val icon: Int
        get() = R.drawable.ic_item_keystone

    override val name: StringResource
        get() = stringRes(co.electriccoin.zcash.ui.R.string.accounts_keystone)

    override val saplingAddress: String? = null

    override val saplingBalance: WalletBalance? = null

    override val totalBalance: Zatoshi?
        get() {
            val unifiedTotal = unifiedBalance?.total ?: return null
            val transparent = transparentBalance ?: return null
            return unifiedTotal + transparent
        }

    override val totalShieldedBalance: Zatoshi?
        get() = unifiedBalance?.total

    override val totalTransparentBalance: Zatoshi?
        get() = transparentBalance

    override val spendableShieldedBalance: Zatoshi?
        get() = unifiedBalance?.available

    override val pendingShieldedBalance: Zatoshi?
        get() {
            val unified = unifiedBalance ?: return null
            return unified.changePending + unified.valuePending
        }

    override fun compareTo(other: WalletAccount) =
        when (other) {
            is KeystoneAccount -> 0
            is LedgerAccount -> 1
            is ZashiAccount -> -1
        }
}

/**
 * An account whose spend authority lives on a Ledger hardware wallet, paired over Bluetooth LE.
 *
 * Like [KeystoneAccount] it has no Sapling address or balance. Unlike every other account its
 * SDK-side [Account.hdAccountIndex] is null — the device never reveals its seed fingerprint — so
 * the ZIP 32 account index comes from the binding the app persisted at pairing time.
 *
 * @param deviceIdentity The paired device's identity encoding, or null when no binding is stored.
 *        Privacy-sensitive: it is linkable to the account's first transparent address, so it must
 *        never be logged or printed.
 * @param zip32AccountIndex The account's ZIP 32 index on the paired device, or null when no
 *        binding is stored. Never defaulted to zero — index 0 is a real account, not a stand-in
 *        for "unknown", and deriving against the wrong one would produce the wrong signature.
 */
data class LedgerAccount(
    override val sdkAccount: Account,
    override val unifiedAddress: String,
    override val transparentAddress: String,
    override val orchardBalance: WalletBalance?,
    override val ironwoodBalance: WalletBalance?,
    override val transparentBalance: Zatoshi?,
    override val isSelected: Boolean,
    val deviceIdentity: String?,
    val zip32AccountIndex: Zip32AccountIndex?,
) : HWWalletAccount {
    /**
     * Whether the binding needed to sign with this account is stored. False means the account
     * still displays and receives, but nothing can derive against it.
     */
    val isBound: Boolean
        get() = deviceIdentity != null && zip32AccountIndex != null

    override val icon: Int
        get() = R.drawable.ic_item_ledger

    override val name: StringResource
        get() = stringRes(co.electriccoin.zcash.ui.R.string.accounts_ledger)

    override val saplingAddress: String? = null

    override val saplingBalance: WalletBalance? = null

    override val hdAccountIndex: Zip32AccountIndex?
        get() = zip32AccountIndex

    override val totalBalance: Zatoshi?
        get() {
            val unifiedTotal = unifiedBalance?.total ?: return null
            val transparent = transparentBalance ?: return null
            return unifiedTotal + transparent
        }

    override val totalShieldedBalance: Zatoshi?
        get() = unifiedBalance?.total

    override val totalTransparentBalance: Zatoshi?
        get() = transparentBalance

    override val spendableShieldedBalance: Zatoshi?
        get() = unifiedBalance?.available

    override val pendingShieldedBalance: Zatoshi?
        get() {
            val unified = unifiedBalance ?: return null
            return unified.changePending + unified.valuePending
        }

    override fun compareTo(other: WalletAccount) =
        when (other) {
            is KeystoneAccount -> -1
            is LedgerAccount -> 0
            is ZashiAccount -> -1
        }

    /**
     * Overridden to redact the device identity; the addresses are not part of the string, so
     * nothing else needs redacting.
     */
    override fun toString() =
        "LedgerAccount(sdkAccount=$sdkAccount, deviceIdentity=***, " +
            "zip32AccountIndex=$zip32AccountIndex, isSelected=$isSelected)"
}

/**
 * Folds two balances together, e.g. to derive [WalletAccount.unifiedBalance] from Orchard +
 * Ironwood. Includes [WalletBalance.locked] — unlike a naive total-only sum, the folded value
 * doesn't silently drop it.
 */
private operator fun WalletBalance.plus(other: WalletBalance) =
    WalletBalance(
        available = available + other.available,
        changePending = changePending + other.changePending,
        valuePending = valuePending + other.valuePending,
        locked = locked + other.locked,
    )

/**
 * The single spendability primitive the whole app validates against, so a typing-time check and the
 * proposal-time check in the SDK can never disagree. A null receiver (no account selected yet)
 * definitively can spend nothing; a present account whose spendable balance has not loaded yet
 * answers null — not yet known, never coerced to a definitive answer.
 */
fun WalletAccount?.canSpend(amount: Zatoshi): Boolean? = if (this == null) false else canSpend(amount)
