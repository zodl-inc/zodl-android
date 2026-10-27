package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.design.util.getString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * The rule that keeps account names unique in the app. The wallet database allows two accounts to
 * share a name, so the app refuses a name another account already shows, the Zodl account's and the
 * hardware accounts' included. Names are compared in their saved form, [AccountNameLimit.normalize],
 * ignoring case. The account being renamed, `excludingAccountKeyId`, never counts, so keeping its
 * own name is never taken. A name that is blank once normalized is never taken; it is refused as
 * blank instead.
 */
class IsAccountNameTakenUseCase(
    private val accountDataSource: AccountDataSource,
    private val context: Context,
) {
    /**
     * Each [name] together with whether it is taken, emitted again when the name or another
     * account's name changes, so the answer always belongs to the name it is paired with.
     */
    fun observe(
        name: Flow<String>,
        excludingAccountKeyId: String? = null
    ): Flow<NameCheck> =
        combine(name, observeNames()) { name, names ->
            NameCheck(name = name, isTaken = isTaken(name, names, excludingAccountKeyId))
        }.distinctUntilChanged()

    /** Whether [name] is taken right now, once the accounts have loaded. */
    suspend operator fun invoke(
        name: String,
        excludingAccountKeyId: String? = null
    ): Boolean = isTaken(name, observeNames().first(), excludingAccountKeyId)

    /** The name every account shows, keyed by its storage key id, emitted again only when one changes. */
    private fun observeNames(): Flow<Map<String, String>> =
        accountDataSource.allAccounts
            .filterNotNull()
            .map { accounts ->
                accounts.associate { it.sdkAccount.accountUuid.toStorageKeyId() to it.name.getString(context) }
            }.distinctUntilChanged()

    private fun isTaken(
        name: String,
        namesByAccount: Map<String, String>,
        excludingAccountKeyId: String?
    ): Boolean {
        val candidate = comparable(name)
        if (candidate.isEmpty()) return false
        return namesByAccount.any { (account, existing) ->
            account != excludingAccountKeyId && comparable(existing) == candidate
        }
    }

    private fun comparable(name: String) = AccountNameLimit.normalize(name).lowercase(Locale.ROOT)
}

/** A typed [name] and whether another account already shows it. */
data class NameCheck(
    val name: String,
    val isTaken: Boolean,
)
