package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.content.res.Configuration
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.Locale
import co.electriccoin.zcash.ui.R as UiR

/**
 * Real Zodl and view-only accounts for the rename and Edit Account tests, so `is` checks and names
 * behave as in the app.
 */
internal object EditAccountFixtures {
    val viewOnlyUuid: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })

    val zodlUuid: AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })

    /** The id both hardware-wallet fixtures share. */
    val hardwareUuid: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 2).toByte() })

    val viewOnlyKeyId: String = viewOnlyUuid.toStorageKeyId()

    val zodlKeyId: String = zodlUuid.toStorageKeyId()

    /** A distinct SDK account per [id], so accounts built from different ids never share a uuid. */
    fun sdkAccount(id: Int) = Account.new(AccountUuid.new(ByteArray(16) { (it + id * 16).toByte() }))

    /** The drawable every test view-only account carries as its icon. */
    const val VIEW_ONLY_ICON = 43

    /**
     * A view-only account named [customName]; a null [customName] shows [defaultName], as an
     * account without a name of its own does.
     */
    fun viewOnly(
        customName: String? = "Zodl Vault",
        defaultName: StringResource = stringRes(DEFAULT_NAME_RES),
    ) = ViewOnlyAccount(
        sdkAccount = Account.new(viewOnlyUuid),
        unifiedAddress = "u",
        transparentAddress = "t",
        orchardBalance = null,
        saplingBalance = null,
        ironwoodBalance = null,
        transparentBalance = null,
        isSelected = false,
        name = customName?.let { stringRes(it) } ?: defaultName,
        customName = customName,
        icon = VIEW_ONLY_ICON,
    )

    fun zodl() =
        ZodlAccount(
            sdkAccount = Account.new(zodlUuid),
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    fun keystone(isSelected: Boolean = false) =
        KeystoneAccount(
            sdkAccount = Account.new(hardwareUuid),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    fun ledger(isSelected: Boolean = false) =
        LedgerAccount(
            sdkAccount = Account.new(hardwareUuid),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            deviceIdentity = null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    /** A use case over [accounts]; null means the accounts are not loaded yet. */
    fun getWalletAccounts(accounts: MutableStateFlow<List<WalletAccount>?>) =
        mockk<GetWalletAccountsUseCase> {
            every { observe() } returns accounts
            every { require() } returns accounts.filterNotNull()
        }

    /**
     * A context that resolves the names the fixtures show: the Zodl and hardware accounts' names and
     * the default view-only name, in English. Its locale sits in the deprecated `locale` field, the
     * one read below API 24, which is the level unit tests report.
     */
    @Suppress("DEPRECATION")
    val context: Context =
        mockk {
            every { resources } returns
                mockk { every { configuration } returns Configuration().apply { locale = Locale.US } }
            every { getString(any(), *anyVararg()) } answers { NAMES.getValue(firstArg()) }
        }

    /** The real duplicate-name rule over the names [accounts] show; null means not loaded yet. */
    fun isAccountNameTaken(accounts: StateFlow<List<WalletAccount>?>) =
        IsAccountNameTakenUseCase(
            accountDataSource = mockk<AccountDataSource> { every { allAccounts } returns accounts },
            context = context,
        )

    /** Stands in for the module's default view-only name resource. */
    private const val DEFAULT_NAME_RES = 1

    private val NAMES =
        mapOf(
            UiR.string.accounts_zashi to "Zodl",
            UiR.string.accounts_keystone to "Keystone",
            UiR.string.accounts_ledger to "Ledger",
            DEFAULT_NAME_RES to "View-only",
        )
}
