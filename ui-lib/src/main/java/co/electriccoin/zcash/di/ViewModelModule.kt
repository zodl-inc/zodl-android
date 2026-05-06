package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarVM
import co.electriccoin.zcash.ui.common.viewmodel.AuthenticationViewModel
import co.electriccoin.zcash.ui.common.viewmodel.OldHomeViewModel
import co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.ScreenTimeoutVM
import co.electriccoin.zcash.ui.screen.accountlist.AccountListVM
import co.electriccoin.zcash.ui.screen.addressbook.AddressBookVM
import co.electriccoin.zcash.ui.screen.addressbook.SelectABRecipientVM
import co.electriccoin.zcash.ui.screen.advancedsettings.AdvancedSettingsVM
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.DebugVM
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.db.DebugDBVM
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetVM
import co.electriccoin.zcash.ui.screen.balances.spendable.SpendableBalanceVM
import co.electriccoin.zcash.ui.screen.chooseserver.ChooseServerVM
import co.electriccoin.zcash.ui.screen.connectkeystone.connect.KeystoneConnectVM
import co.electriccoin.zcash.ui.screen.connectkeystone.date.KeystoneDateVM
import co.electriccoin.zcash.ui.screen.connectkeystone.estimation.KeystoneEstimationVM
import co.electriccoin.zcash.ui.screen.connectkeystone.height.KeystoneHeightVM
import co.electriccoin.zcash.ui.screen.connectkeystone.neworactive.KeystoneNewOrActiveVM
import co.electriccoin.zcash.ui.screen.contact.AddGenericABContactVM
import co.electriccoin.zcash.ui.screen.contact.AddZashiABContactVM
import co.electriccoin.zcash.ui.screen.contact.UpdateGenericABContactVM
import co.electriccoin.zcash.ui.screen.crashreporting.viewmodel.CrashReportingViewModel
import co.electriccoin.zcash.ui.screen.deletewallet.ResetZashiVM
import co.electriccoin.zcash.ui.screen.disconnect.DisconnectVM
import co.electriccoin.zcash.ui.screen.error.ErrorVM
import co.electriccoin.zcash.ui.screen.error.SyncErrorVM
import co.electriccoin.zcash.ui.screen.exchangerate.optin.ExchangeRateOptInVM
import co.electriccoin.zcash.ui.screen.exchangerate.settings.ExchangeRateSettingsVM
import co.electriccoin.zcash.ui.screen.feedback.FeedbackVM
import co.electriccoin.zcash.ui.screen.flexa.FlexaViewModel
import co.electriccoin.zcash.ui.screen.home.HomeVM
import co.electriccoin.zcash.ui.screen.home.backup.WalletBackupDetailViewModel
import co.electriccoin.zcash.ui.screen.home.backup.WalletBackupInfoViewModel
import co.electriccoin.zcash.ui.screen.home.reporting.CrashReportOptInViewModel
import co.electriccoin.zcash.ui.screen.home.restoring.WalletRestoringInfoViewModel
import co.electriccoin.zcash.ui.screen.home.shieldfunds.ShieldFundsInfoVM
import co.electriccoin.zcash.ui.screen.hotfix.enhancement.EnhancementHotfixVM
import co.electriccoin.zcash.ui.screen.hotfix.ephemeral.EphemeralHotfixVM
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsVM
import co.electriccoin.zcash.ui.screen.integrations.IntegrationsVM
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenVM
import co.electriccoin.zcash.ui.screen.more.MoreVM
import co.electriccoin.zcash.ui.screen.pay.PayVM
import co.electriccoin.zcash.ui.screen.qrcode.QrCodeVM
import co.electriccoin.zcash.ui.screen.receive.ReceiveVM
import co.electriccoin.zcash.ui.screen.request.viewmodel.RequestVM
import co.electriccoin.zcash.ui.screen.restore.date.RestoreDateVM
import co.electriccoin.zcash.ui.screen.restore.estimation.RestoreEstimationVM
import co.electriccoin.zcash.ui.screen.restore.height.RestoreHeightVM
import co.electriccoin.zcash.ui.screen.restore.seed.RestoreSeedVM
import co.electriccoin.zcash.ui.screen.restore.tor.RestoreTorVM
import co.electriccoin.zcash.ui.screen.resync.confirm.ResyncConfirmVM
import co.electriccoin.zcash.ui.screen.resync.date.ResyncDateVM
import co.electriccoin.zcash.ui.screen.resync.estimation.ResyncEstimationVM
import co.electriccoin.zcash.ui.screen.resync.height.ResyncHeightVM
import co.electriccoin.zcash.ui.screen.reviewtransaction.ReviewTransactionVM
import co.electriccoin.zcash.ui.screen.scan.ScanGenericAddressVM
import co.electriccoin.zcash.ui.screen.scan.ScanZashiAddressVM
import co.electriccoin.zcash.ui.screen.scan.thirdparty.ThirdPartyScanViewModel
import co.electriccoin.zcash.ui.screen.scankeystone.viewmodel.ScanKeystonePCZTViewModel
import co.electriccoin.zcash.ui.screen.scankeystone.viewmodel.ScanKeystoneSignInRequestViewModel
import co.electriccoin.zcash.ui.screen.selectkeystoneaccount.viewmodel.SelectKeystoneAccountViewModel
import co.electriccoin.zcash.ui.screen.send.SendViewModel
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionVM
import co.electriccoin.zcash.ui.screen.support.viewmodel.SupportViewModel
import co.electriccoin.zcash.ui.screen.swap.SwapVM
import co.electriccoin.zcash.ui.screen.swap.ab.AddSwapABContactVM
import co.electriccoin.zcash.ui.screen.swap.ab.SelectSwapABRecipientVM
import co.electriccoin.zcash.ui.screen.swap.detail.SwapDetailVM
import co.electriccoin.zcash.ui.screen.swap.detail.support.SwapSupportVM
import co.electriccoin.zcash.ui.screen.swap.info.SwapRefundAddressInfoVM
import co.electriccoin.zcash.ui.screen.swap.lock.EphemeralLockVM
import co.electriccoin.zcash.ui.screen.swap.orconfirmation.ORSwapConfirmationVM
import co.electriccoin.zcash.ui.screen.swap.picker.SwapAssetPickerVM
import co.electriccoin.zcash.ui.screen.swap.picker.SwapBlockchainPickerVM
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteVM
import co.electriccoin.zcash.ui.screen.swap.slippage.SwapSlippageVM
import co.electriccoin.zcash.ui.screen.taxexport.TaxExportViewModel
import co.electriccoin.zcash.ui.screen.texunsupported.TEXUnsupportedVM
import co.electriccoin.zcash.ui.screen.tor.optin.TorOptInVM
import co.electriccoin.zcash.ui.screen.tor.settings.TorSettingsVM
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailVM
import co.electriccoin.zcash.ui.screen.transactionfilters.viewmodel.TransactionFiltersVM
import co.electriccoin.zcash.ui.screen.transactionhistory.ActivityHistoryVM
import co.electriccoin.zcash.ui.screen.transactionhistory.widget.ActivityWidgetVM
import co.electriccoin.zcash.ui.screen.transactionnote.viewmodel.TransactionNoteViewModel
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressVM
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingVM
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionVM
import co.electriccoin.zcash.ui.screen.voting.howtovote.VoteHowToVoteVM
import co.electriccoin.zcash.ui.screen.voting.ineligible.VoteIneligibleVM
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailVM
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListVM
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsVM
import co.electriccoin.zcash.ui.screen.voting.scankeystone.viewmodel.ScanKeystoneVotingPCZTViewModel
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingVM
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingVM
import co.electriccoin.zcash.ui.screen.voting.votingerror.VoteConfigErrorVM
import co.electriccoin.zcash.ui.screen.voting.votingerror.VoteErrorVM
import co.electriccoin.zcash.ui.screen.voting.walletsyncing.VoteWalletSyncingVM
import co.electriccoin.zcash.ui.screen.walletbackup.WalletBackupViewModel
import co.electriccoin.zcash.ui.screen.warning.viewmodel.StorageCheckViewModel
import co.electriccoin.zcash.ui.screen.whatsnew.viewmodel.WhatsNewViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule =
    module {
        viewModelOf(::WalletViewModel)
        viewModelOf(::AuthenticationViewModel)
        viewModelOf(::OldHomeViewModel)
        viewModelOf(::StorageCheckViewModel)
        viewModelOf(::RestoreSeedVM)
        viewModelOf(::MoreVM)
        viewModelOf(::AdvancedSettingsVM)
        viewModelOf(::SupportViewModel)
        viewModelOf(::WhatsNewViewModel)
        viewModelOf(::ChooseServerVM)
        viewModelOf(::ReceiveVM)
        viewModelOf(::QrCodeVM)
        viewModelOf(::RequestVM)
        viewModelOf(::ScanZashiAddressVM)
        viewModelOf(::ScanKeystoneSignInRequestViewModel)
        viewModelOf(::ScanKeystonePCZTViewModel)
        viewModelOf(::IntegrationsVM)
        viewModelOf(::FlexaViewModel)
        viewModelOf(::SendViewModel)
        viewModelOf(::WalletBackupViewModel)
        viewModelOf(::FeedbackVM)
        viewModelOf(::SignKeystoneTransactionVM)
        viewModelOf(::AccountListVM)
        viewModelOf(::ZashiTopAppBarVM)
        viewModelOf(::SelectKeystoneAccountViewModel)
        viewModelOf(::ReviewTransactionVM)
        viewModelOf(::TransactionFiltersVM)
        viewModelOf(::TransactionProgressVM)
        viewModelOf(::ActivityWidgetVM)
        viewModelOf(::ActivityHistoryVM)
        viewModelOf(::TransactionDetailVM)
        viewModelOf(::AddressBookVM)
        viewModelOf(::SelectABRecipientVM)
        viewModelOf(::TransactionNoteViewModel)
        viewModelOf(::TaxExportViewModel)
        viewModelOf(::CrashReportingViewModel)
        viewModelOf(::BalanceWidgetVM)
        viewModelOf(::HomeVM)
        viewModelOf(::RestoreHeightVM)
        viewModelOf(::RestoreDateVM)
        viewModelOf(::RestoreEstimationVM)
        viewModelOf(::ResyncConfirmVM)
        viewModelOf(::ResyncDateVM)
        viewModelOf(::ResyncEstimationVM)
        viewModelOf(::ResyncHeightVM)
        viewModelOf(::ShieldFundsInfoVM)
        viewModelOf(::WalletBackupInfoViewModel)
        viewModelOf(::ExchangeRateSettingsVM)
        viewModelOf(::WalletBackupDetailViewModel)
        viewModelOf(::ErrorVM)
        viewModelOf(::SyncErrorVM)
        viewModelOf(::SpendableBalanceVM)
        viewModelOf(::CrashReportOptInViewModel)
        viewModelOf(::WalletRestoringInfoViewModel)
        viewModelOf(::ThirdPartyScanViewModel)
        viewModelOf(::TorSettingsVM)
        viewModelOf(::TorOptInVM)
        viewModelOf(::ExchangeRateOptInVM)
        viewModelOf(::SwapAssetPickerVM)
        viewModelOf(::SwapSlippageVM)
        viewModelOf(::SwapVM)
        viewModelOf(::PayVM)
        viewModelOf(::SwapQuoteVM)
        viewModelOf(::ScanGenericAddressVM)
        viewModelOf(::SelectSwapABRecipientVM)
        viewModelOf(::SwapBlockchainPickerVM)
        viewModelOf(::AddZashiABContactVM)
        viewModelOf(::AddSwapABContactVM)
        viewModelOf(::AddGenericABContactVM)
        viewModelOf(::UpdateGenericABContactVM)
        viewModelOf(::ORSwapConfirmationVM)
        viewModelOf(::SwapDetailVM)
        viewModelOf(::SwapSupportVM)
        viewModelOf(::SwapRefundAddressInfoVM)
        viewModelOf(::ScreenTimeoutVM)
        viewModelOf(::EphemeralHotfixVM)
        viewModelOf(::EnhancementHotfixVM)
        viewModelOf(::EphemeralLockVM)
        viewModelOf(::DebugVM)
        viewModelOf(::DebugDBVM)
        viewModelOf(::TEXUnsupportedVM)
        viewModelOf(::InsufficientFundsVM)
        viewModelOf(::RestoreTorVM)
        viewModelOf(::ResetZashiVM)
        viewModelOf(::DisconnectVM)
        viewModelOf(::VoteCoinholderPollingVM)
        viewModelOf(::VoteHowToVoteVM)
        viewModelOf(::VoteProposalListVM)
        viewModelOf(::VoteProposalDetailVM)
        viewModelOf(::VoteConfirmSubmissionVM)
        viewModelOf(::VoteWalletSyncingVM)
        viewModelOf(::VoteIneligibleVM)
        viewModelOf(::VoteTallyingVM)
        viewModelOf(::VoteResultsVM)
        viewModelOf(::VoteErrorVM)
        viewModelOf(::VoteConfigErrorVM)
        viewModelOf(::SignKeystoneVotingVM)
        viewModelOf(::ScanKeystoneVotingPCZTViewModel)
        viewModelOf(::KeystoneConnectVM)
        viewModelOf(::KeystoneNewOrActiveVM)
        viewModelOf(::KeystoneDateVM)
        viewModelOf(::KeystoneEstimationVM)
        viewModelOf(::KeystoneHeightVM)
        viewModelOf(::KeepOpenVM)
    }
