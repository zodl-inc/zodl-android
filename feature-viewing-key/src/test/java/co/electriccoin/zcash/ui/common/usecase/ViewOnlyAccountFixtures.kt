package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.content.res.Configuration
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
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
 * Accounts for the remove and naming tests: a real [ViewOnlyAccount] (so `is` checks and its name
 * behave as in the app) and a Zodl account that must never be renamed or removed.
 */
internal object ViewOnlyAccountFixtures {
    val viewOnlyUuid: AccountUuid = AccountUuid.new(ByteArray(16) { (it + 1).toByte() })

    val zodlUuid: AccountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })

    val viewOnlyKeyId: String = viewOnlyUuid.toStorageKeyId()

    val zodlKeyId: String = zodlUuid.toStorageKeyId()

    /** Stands in for the module's default view-only name resource. */
    const val DEFAULT_NAME_RES = 1

    /**
     * A view-only account named [customName]; a null [customName] shows [defaultName], as an
     * account without a name of its own does.
     */
    fun viewOnly(
        customName: String? = "Zodl Vault",
        isSelected: Boolean = false,
        defaultName: StringResource = stringRes(DEFAULT_NAME_RES),
    ) = ViewOnlyAccount(
        sdkAccount = sdkAccount(viewOnlyUuid),
        unifiedAddress = "u",
        transparentAddress = "t",
        orchardBalance = null,
        saplingBalance = null,
        ironwoodBalance = null,
        transparentBalance = null,
        isSelected = isSelected,
        name = customName?.let { stringRes(it) } ?: defaultName,
        customName = customName,
        icon = 0,
    )

    fun zodl(isSelected: Boolean = false) =
        mockk<ZodlAccount> {
            every { sdkAccount } returns sdkAccount(zodlUuid)
            every { this@mockk.isSelected } returns isSelected
            every { isSpendable } returns true
            every { name } returns stringRes(UiR.string.accounts_zashi)
        }

    /** A use case over [accounts]; null means the accounts are not loaded yet. */
    fun getWalletAccounts(accounts: MutableStateFlow<List<WalletAccount>?>) =
        mockk<GetWalletAccountsUseCase> {
            every { observe() } returns accounts
            every { require() } returns accounts.filterNotNull()
        }

    /**
     * A context that resolves the names the fixtures show, the Zodl account's and the default
     * view-only name, in English. Its locale sits in the deprecated `locale` field, the one read below
     * API 24, which is the level unit tests report.
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

    private val NAMES =
        mapOf(
            UiR.string.accounts_zashi to "Zodl",
            DEFAULT_NAME_RES to "View-only",
        )

    private fun sdkAccount(uuid: AccountUuid) =
        mockk<Account> {
            every { accountUuid } returns uuid
        }
}
