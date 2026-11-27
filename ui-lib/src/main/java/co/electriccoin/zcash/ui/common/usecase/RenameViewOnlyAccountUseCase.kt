package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount

/**
 * Stores a new name for a view-only account in the wallet database, which the account list, the top
 * bar and every other place that shows the account read through the account data source.
 */
class RenameViewOnlyAccountUseCase(
    private val accountDataSource: AccountDataSource,
) {
    /**
     * Saves [name] trimmed and capped at [AccountNameLimit.MAX_LENGTH] code points. A blank name is
     * ignored, so the account never loses its name. The screens refuse a name another account already
     * shows, by [IsAccountNameTakenUseCase], before it gets here.
     */
    suspend operator fun invoke(
        account: ViewOnlyAccount,
        name: String
    ) {
        val normalized = AccountNameLimit.normalize(name)
        if (normalized.isEmpty()) return
        accountDataSource.renameAccount(account, normalized)
    }
}
