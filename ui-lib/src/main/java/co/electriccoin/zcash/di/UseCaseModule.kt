package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.mapper.SwapSupportMapper
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.usecase.ApplyTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.ApplyTransactionFulltextFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.AuthorizeVotingSubmissionUseCase
import co.electriccoin.zcash.ui.common.usecase.CancelProposalFlowUseCase
import co.electriccoin.zcash.ui.common.usecase.CancelSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.ConfirmResyncUseCase
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateFlexaTransactionUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateIncreaseEphemeralGapLimitProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateKeystoneProposalPCZTEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateOrUpdateTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateVotingKeystonePcztEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.DeleteABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.DeleteTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.DeriveKeystoneAccountUnifiedAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.DisconnectUseCase
import co.electriccoin.zcash.ui.common.usecase.EnsureSwapAssetsLoadedUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.ExportTaxUseCase
import co.electriccoin.zcash.ui.common.usecase.FilterSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.FilterSwapBlockchainsUseCase
import co.electriccoin.zcash.ui.common.usecase.FixEnhancementUseCase
import co.electriccoin.zcash.ui.common.usecase.FixEphemeralAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.FlipTransactionBookmarkUseCase
import co.electriccoin.zcash.ui.common.usecase.GetABContactByIdUseCase
import co.electriccoin.zcash.ui.common.usecase.GetABContactsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetABSwapContactsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetActivitiesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAutomaticEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetConfigurationUseCase
import co.electriccoin.zcash.ui.common.usecase.GetExchangeRateUseCase
import co.electriccoin.zcash.ui.common.usecase.GetFilteredActivitiesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetFlexaStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetHomeMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.GetKeystoneStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPersistableWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPreselectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.GetReloadableSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.GetResyncDataFromHeightUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSupportUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTotalSpendableBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionDetailByIdUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionMetadataUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletRestoringStateUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletSeedBytesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetZashiAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsABContactHintVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.IsEphemeralAddressLockedUseCase
import co.electriccoin.zcash.ui.common.usecase.IsRestoreSuccessDialogVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.IsScreenTimeoutDisabledDuringRestoreUseCase
import co.electriccoin.zcash.ui.common.usecase.IsServerAutomaticUseCase
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.MarkTxMemoAsReadUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToAddressBookUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToExportPrivateDataUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToNearPayUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToReceiveUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRequestShieldedUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRequestZecUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToResetWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectFiatCurrencyUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectSwapBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSendUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapQuoteIfAvailableUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToTaxExportUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToWalletBackupUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveABContactPickedUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveClearSendUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveContactByAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveTransactionSubmitStateUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveZashiAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.OnAddressScannedUseCase
import co.electriccoin.zcash.ui.common.usecase.OnUserSavedWalletBackupUseCase
import co.electriccoin.zcash.ui.common.usecase.OnZip321ScannedUseCase
import co.electriccoin.zcash.ui.common.usecase.OptInExchangeRateAndTorUseCase
import co.electriccoin.zcash.ui.common.usecase.OptInExchangeRateUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystonePCZTUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneSignInRequestUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseKeystoneUrToZashiAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseVotingKeystonePCZTUseCase
import co.electriccoin.zcash.ui.common.usecase.PersistServerSelectionUseCase
import co.electriccoin.zcash.ui.common.usecase.PrefillSendUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.common.usecase.ProcessSwapTransactionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.RemindWalletBackupLaterUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.RescanBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.RescanQrUseCase
import co.electriccoin.zcash.ui.common.usecase.ResetTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.RestoreWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.ResyncErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveORSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SendSupportEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SendTransactionAgainUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareImageUseCase
import co.electriccoin.zcash.ui.common.usecase.SharePCZTUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsFromMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsUseCase
import co.electriccoin.zcash.ui.common.usecase.ShowErrorUseCase
import co.electriccoin.zcash.ui.common.usecase.SkipRemainingKeystoneBundlesUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitIncreaseEphemeralGapLimitUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitKSProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitVotesUseCase
import co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateSwapActivityMetadataUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateGenericABContactNameUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateSeedUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateSwapABContactAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateZashiABContactAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewTransactionDetailAfterSuccessfulProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewTransactionsAfterSuccessfulProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.WalletBackupMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.WalletBackupMessageUseCaseImpl
import co.electriccoin.zcash.ui.common.usecase.Zip321BuildUriUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.db.ExecuteDebugDBQueryUseCase
import co.electriccoin.zcash.ui.screen.deletewallet.ResetZashiUseCase
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val useCaseModule =
    module {
        factoryOf(::ObserveFastestServersUseCase)
        factoryOf(::GetSelectedEndpointUseCase)
        factoryOf(::RefreshFastestServersUseCase)
        factoryOf(::RefreshActiveVotingSessionUseCase)
        factory { RefreshVotingRoundsUseCase(get(), get()) }
        factoryOf(::GetAllVotingRoundsUseCase)
        factoryOf(::ResolveVotingRoundSessionUseCase)
        factoryOf(::PrepareVotingRoundUseCase)
        factoryOf(::AuthorizeVotingSubmissionUseCase)
        factoryOf(::SkipRemainingKeystoneBundlesUseCase)
        factoryOf(::SubmitVotesUseCase)
        factoryOf(::TrackVotingSharesUseCase)
        factoryOf(::PersistServerSelectionUseCase)
        factoryOf(::GetConfigurationUseCase)
        factoryOf(::RescanBlockchainUseCase)
        factoryOf(::ValidateZashiABContactAddressUseCase)
        factoryOf(::ValidateGenericABContactNameUseCase)
        factoryOf(::SaveABContactUseCase)
        factoryOf(::UpdateABContactUseCase)
        factoryOf(::DeleteABContactUseCase)
        factoryOf(::GetABContactByIdUseCase)
        factoryOf(::ObserveContactByAddressUseCase)
        singleOf(::ObserveABContactPickedUseCase)
        factoryOf(::CopyToClipboardUseCase)
        factoryOf(::ShareImageUseCase)
        factoryOf(::Zip321BuildUriUseCase)
        factoryOf(::Zip321ParseUriValidationUseCase)
        factoryOf(::GetPersistableWalletUseCase)
        factoryOf(::GetSupportUseCase)
        factoryOf(::GetWalletSeedBytesUseCase)
        factoryOf(::ErrorMapperUseCase)
        factoryOf(::ResyncErrorMapperUseCase)
        factoryOf(::SendEmailUseCase)
        factoryOf(::SendSupportEmailUseCase)
        factoryOf(::ShowErrorUseCase)
        factoryOf(::GetWalletAccountsUseCase)
        factoryOf(::SelectWalletAccountUseCase)
        factoryOf(::ObserveSelectedWalletAccountUseCase)
        factoryOf(::ObserveZashiAccountUseCase)
        factoryOf(::GetZashiAccountUseCase)
        factoryOf(::CreateKeystoneAccountUseCase)
        factoryOf(::DeriveKeystoneAccountUnifiedAddressUseCase)
        factoryOf(::ParseKeystoneUrToZashiAccountsUseCase)
        factoryOf(::GetExchangeRateUseCase)
        factoryOf(::GetSelectedWalletAccountUseCase)
        singleOf(::ObserveClearSendUseCase)
        singleOf(::PrefillSendUseCase)
        factoryOf(::GetTransactionsUseCase)
        factoryOf(::GetFilteredActivitiesUseCase)
        factoryOf(::CreateProposalUseCase)
        factoryOf(::OnZip321ScannedUseCase)
        factoryOf(::OnAddressScannedUseCase)
        factoryOf(::ParseKeystonePCZTUseCase)
        factoryOf(::ParseVotingKeystonePCZTUseCase)
        singleOf(::SubmitKSProposalUseCase)
        factoryOf(::ParseKeystoneSignInRequestUseCase)
        factoryOf(::CancelProposalFlowUseCase)
        factoryOf(::ObserveProposalUseCase)
        factoryOf(::SharePCZTUseCase)
        factoryOf(::CreateKeystoneProposalPCZTEncoderUseCase)
        factoryOf(::CreateVotingKeystonePcztEncoderUseCase)
        factoryOf(::ViewTransactionsAfterSuccessfulProposalUseCase)
        factoryOf(::ViewTransactionDetailAfterSuccessfulProposalUseCase)
        factoryOf(::ObserveTransactionSubmitStateUseCase)
        factoryOf(::GetProposalUseCase)
        singleOf(::SubmitProposalUseCase)
        singleOf(::ProcessSwapTransactionUseCase)
        factoryOf(::GetWalletRestoringStateUseCase)
        factoryOf(::ApplyTransactionFiltersUseCase)
        factoryOf(::ResetTransactionFiltersUseCase)
        factoryOf(::ApplyTransactionFulltextFiltersUseCase)
        factoryOf(::GetTransactionFiltersUseCase)
        factoryOf(::GetTransactionDetailByIdUseCase)
        factoryOf(::SendTransactionAgainUseCase)
        factoryOf(::GetABContactsUseCase)
        factoryOf(::GetABSwapContactsUseCase)
        factoryOf(::NavigateToAddressBookUseCase)
        factoryOf(::NavigateToSelectRecipientUseCase)
        factoryOf(::GetTransactionMetadataUseCase)
        factoryOf(::FlipTransactionBookmarkUseCase)
        factoryOf(::DeleteTransactionNoteUseCase)
        factoryOf(::DisconnectUseCase)
        factoryOf(::CreateOrUpdateTransactionNoteUseCase)
        factoryOf(::MarkTxMemoAsReadUseCase)
        factoryOf(::ExportTaxUseCase)
        factoryOf(::NavigateToTaxExportUseCase)
        factoryOf(::CreateFlexaTransactionUseCase)
        factoryOf(::IsRestoreSuccessDialogVisibleUseCase)
        factoryOf(::ValidateSeedUseCase)
        factoryOf(::RestoreWalletUseCase)
        factoryOf(::NavigateToWalletBackupUseCase)
        factoryOf(::GetKeystoneStatusUseCase)
        factoryOf(::GetFlexaStatusUseCase)
        factoryOf(::GetHomeMessageUseCase)
        factoryOf(::OnUserSavedWalletBackupUseCase)
        factoryOf(::RemindWalletBackupLaterUseCase)
        singleOf(::ShieldFundsUseCase)
        singleOf(::NavigateToErrorUseCase)
        factoryOf(::RescanQrUseCase)
        factoryOf(::ShieldFundsFromMessageUseCase)
        factoryOf(::NavigateToReceiveUseCase)
        factoryOf(::NavigateToRequestShieldedUseCase)
        factoryOf(::NavigateToRequestZecUseCase)
        factoryOf(::NavigateToSendUseCase)
        factoryOf(::IsTorEnabledUseCase)
        factoryOf(::OptInExchangeRateUseCase)
        factoryOf(::OptInExchangeRateAndTorUseCase)
        factoryOf(::NavigateToSwapUseCase)
        factoryOf(::CancelSwapUseCase)
        factoryOf(::GetSwapAssetsUseCase)
        factoryOf(::EnsureSwapAssetsLoadedUseCase)
        factoryOf(::FilterSwapAssetsUseCase)
        factoryOf(::FilterSwapBlockchainsUseCase)
        factoryOf(::NavigateToSwapInfoUseCase)
        factoryOf(::GetTotalSpendableBalanceUseCase)
        factoryOf(::IsABContactHintVisibleUseCase)
        factoryOf(::RequestSwapQuoteUseCase)
        factoryOf(::CancelSwapQuoteUseCase)
        factoryOf(::NavigateToSwapQuoteIfAvailableUseCase)
        singleOf(::NavigateToScanGenericAddressUseCase)
        singleOf(::NavigateToSelectABSwapRecipientUseCase)
        singleOf(::NavigateToSelectSwapBlockchainUseCase)
        singleOf(::NavigateToSlippageUseCase)
        singleOf(::NavigateToSwapAssetPickerUseCase)
        singleOf(::NavigateToSelectFiatCurrencyUseCase)
        factoryOf(::ConfirmResyncUseCase)
        factoryOf(::ValidateSwapABContactAddressUseCase)
        // MOB-1396: Pay (Crosspay, EXACT_OUTPUT) is NEAR-only. Its entry refresh and the repository-touching
        // use cases it injects resolve the NEAR-named SwapRepository, so Pay never touches Maya.
        factory {
            NavigateToNearPayUseCase(swapRepository = get(named(SwapProvider.NEAR)), navigationRouter = get())
        }
        factory(named(SwapProvider.NEAR)) {
            GetSwapAssetsUseCase(swapRepository = get(named(SwapProvider.NEAR)))
        }
        factory(named(SwapProvider.NEAR)) {
            CancelSwapUseCase(swapRepository = get(named(SwapProvider.NEAR)), navigationRouter = get())
        }
        factory(named(SwapProvider.NEAR)) {
            NavigateToSwapQuoteIfAvailableUseCase(
                swapRepository = get(named(SwapProvider.NEAR)),
                navigationRouter = get()
            )
        }
        factory(named(SwapProvider.NEAR)) {
            GetPreselectedSwapAssetUseCase(
                swapRepository = get(named(SwapProvider.NEAR)),
                metadataRepository = get(),
                simpleSwapAssetProvider = get()
            )
        }
        factory(named(SwapProvider.NEAR)) {
            RequestSwapQuoteUseCase(
                navigationRouter = get(),
                navigateToErrorUseCase = get(),
                swapRepository = get(named(SwapProvider.NEAR)),
                zashiProposalRepository = get(),
                keystoneProposalRepository = get(),
                accountDataSource = get(),
                synchronizerProvider = get()
            )
        }
        factoryOf(::SaveORSwapUseCase)
        factoryOf(::GetReloadableSwapQuoteUseCase)
        factoryOf(::ShareQRUseCase)
        factoryOf(::GetActivitiesUseCase)
        factoryOf(::GetResyncDataFromHeightUseCase)
        factoryOf(::NavigateToExportPrivateDataUseCase)
        factoryOf(::NavigateToResetWalletUseCase)
        factoryOf(::IsScreenTimeoutDisabledDuringRestoreUseCase)
        singleOf(::UpdateSwapActivityMetadataUseCase)
        factoryOf(::WalletBackupMessageUseCaseImpl) bind WalletBackupMessageUseCase::class
        factoryOf(::ValidateAddressUseCase)
        singleOf(::FixEphemeralAddressUseCase)
        factoryOf(::FixEnhancementUseCase)
        factoryOf(::IsEphemeralAddressLockedUseCase)
        singleOf(::SubmitIncreaseEphemeralGapLimitUseCase)
        factoryOf(::CreateIncreaseEphemeralGapLimitProposalUseCase)
        factoryOf(::ResetZashiUseCase)
        factoryOf(::GetPreselectedSwapAssetUseCase)
        factoryOf(::GetSwapStatusUseCase)
        factoryOf(::ExecuteDebugDBQueryUseCase)
        factoryOf(::SwapSupportMapper)
        factoryOf(::GetAutomaticEndpointUseCase)
        factoryOf(::IsServerAutomaticUseCase)
    }
