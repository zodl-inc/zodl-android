package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerOperationUnsupportedException
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteKeystoneStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteStorageProvider
import co.electriccoin.zcash.ui.common.voting.VotingSettingsEntry
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase

/**
 * Routes to the coinholder-voting entry point, branching on whether the currently selected
 * wallet (Zodl or Keystone) has already seen the "How to vote" explainer for that wallet type -
 * first-time users land on the explainer, returning users go straight to the poll.
 *
 * Extracted from MoreVM's private onVotingClick() (MOB-1805) so the same branch-and-navigate
 * logic can be reused by the Coinholder Polling home-widget prompt (HomeVM) without duplicating
 * it a second time.
 */
class NavigateToVotingUseCase(
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val hasSeenHowToVote: HasSeenHowToVoteStorageProvider,
    private val hasSeenHowToVoteKeystone: HasSeenHowToVoteKeystoneStorageProvider,
    private val votingSettingsEntry: VotingSettingsEntry,
    private val navigateToError: NavigateToErrorUseCase,
) {
    suspend operator fun invoke() {
        val selectedAccount = getSelectedWalletAccount()
        if (selectedAccount is LedgerAccount) {
            navigateToError(ErrorArgs.General(LedgerOperationUnsupportedException()))
            return
        }
        val isKeystone = selectedAccount is KeystoneAccount
        val hasSeenHowToVoteForCurrentWallet =
            if (isKeystone) {
                hasSeenHowToVoteKeystone.get()
            } else {
                hasSeenHowToVote.get()
            }

        if (hasSeenHowToVoteForCurrentWallet) {
            votingSettingsEntry.navigateToCoinholderPolling()
        } else {
            votingSettingsEntry.navigateToHowToVote()
        }
    }
}
