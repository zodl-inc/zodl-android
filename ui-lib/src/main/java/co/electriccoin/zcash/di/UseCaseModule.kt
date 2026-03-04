package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.mapper.GetSwapMessageMapper
import co.electriccoin.zcash.ui.common.usecase.ApplyTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.ApplyTransactionFulltextFiltersUseCase
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
import co.electriccoin.zcash.ui.common.usecase.DeleteABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.DeleteTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.DeriveKeystoneAccountUnifiedAddressUseCase
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
import co.electriccoin.zcash.ui.common.usecase.GetConfigurationUseCase
import co.electriccoin.zcash.ui.common.usecase.GetExchangeRateUseCase
import co.electriccoin.zcash.ui.common.usecase.GetFilteredActivitiesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetFlexaStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetHomeMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.GetKeystoneStatusUseCase
import co.electriccoin.zcash.ui.common.usecase.GetORSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.GetPersistableWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.GetProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSlippageUseCase
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
import co.electriccoin.zcash.ui.common.usecase.GetZashiAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsABContactHintVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.IsEphemeralAddressLockedUseCase
import co.electriccoin.zcash.ui.common.usecase.IsRestoreSuccessDialogVisibleUseCase
import co.electriccoin.zcash.ui.common.usecase.IsScreenTimeoutDisabledDuringRestoreUseCase
import co.electriccoin.zcash.ui.common.usecase.IsTorEnabledUseCase
import co.electriccoin.zcash.ui.common.usecase.MarkTxMemoAsReadUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToAddressBookUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToEstimateBlockHeightUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToExportPrivateDataUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToNearPayUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToReceiveUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRequestShieldedUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToResetWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToScanGenericAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectABSwapRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectSwapBlockchainUseCase
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
import co.electriccoin.zcash.ui.common.usecase.PersistEndpointUseCase
import co.electriccoin.zcash.ui.common.usecase.PrefillSendUseCase
import co.electriccoin.zcash.ui.common.usecase.PreselectSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.ProcessSwapTransactionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshFastestServersUseCase
import co.electriccoin.zcash.ui.common.usecase.RemindWalletBackupLaterUseCase
import co.electriccoin.zcash.ui.common.usecase.RequestSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.RescanBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.RescanQrUseCase
import co.electriccoin.zcash.ui.common.usecase.ResetTransactionFiltersUseCase
import co.electriccoin.zcash.ui.common.usecase.RestoreWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveORSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectSwapAssetUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SendSupportEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SendTransactionAgainUseCase
import co.electriccoin.zcash.ui.common.usecase.SetSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareImageUseCase
import co.electriccoin.zcash.ui.common.usecase.SharePCZTUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsFromMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitIncreaseEphemeralGapLimitUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitKSProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateABContactUseCase
import co.electriccoin.zcash.ui.common.usecase.UpdateSwapActivityMetadataUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateEndpointUseCase
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
import org.koin.dsl.bind
import org.koin.dsl.module

val useCaseModule =
    module {
        factoryOf(::ObserveFastestServersUseCase)
        factoryOf(::GetSelectedEndpointUseCase)
        factoryOf(::RefreshFastestServersUseCase)
        factoryOf(::PersistEndpointUseCase)
        factoryOf(::ValidateEndpointUseCase)
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
        factoryOf(::SendEmailUseCase)
        factoryOf(::SendSupportEmailUseCase)
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
        singleOf(::SubmitKSProposalUseCase)
        factoryOf(::ParseKeystoneSignInRequestUseCase)
        factoryOf(::CancelProposalFlowUseCase)
        factoryOf(::ObserveProposalUseCase)
        factoryOf(::SharePCZTUseCase)
        factoryOf(::CreateKeystoneProposalPCZTEncoderUseCase)
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
        factoryOf(::IsTorEnabledUseCase)
        factoryOf(::OptInExchangeRateUseCase)
        factoryOf(::OptInExchangeRateAndTorUseCase)
        factoryOf(::NavigateToSwapUseCase)
        factoryOf(::CancelSwapUseCase)
        factoryOf(::GetSelectedSwapAssetUseCase)
        factoryOf(::SelectSwapAssetUseCase)
        factoryOf(::GetSwapAssetsUseCase)
        factoryOf(::FilterSwapAssetsUseCase)
        factoryOf(::FilterSwapBlockchainsUseCase)
        factoryOf(::SetSlippageUseCase)
        factoryOf(::GetSlippageUseCase)
        factoryOf(::NavigateToSwapInfoUseCase)
        factoryOf(::GetTotalSpendableBalanceUseCase)
        factoryOf(::IsABContactHintVisibleUseCase)
        factoryOf(::RequestSwapQuoteUseCase)
        factoryOf(::CancelSwapQuoteUseCase)
        factoryOf(::NavigateToSwapQuoteIfAvailableUseCase)
        singleOf(::NavigateToScanGenericAddressUseCase)
        singleOf(::NavigateToSelectABSwapRecipientUseCase)
        singleOf(::NavigateToSelectSwapBlockchainUseCase)
        singleOf(::NavigateToEstimateBlockHeightUseCase)
        factoryOf(::ConfirmResyncUseCase)
        factoryOf(::ValidateSwapABContactAddressUseCase)
        factoryOf(::NavigateToNearPayUseCase)
        factoryOf(::SaveORSwapUseCase)
        factoryOf(::GetORSwapQuoteUseCase)
        factoryOf(::ShareQRUseCase)
        factoryOf(::GetActivitiesUseCase)
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
        factoryOf(::PreselectSwapAssetUseCase)
        factoryOf(::GetSwapStatusUseCase)
        factoryOf(::ExecuteDebugDBQueryUseCase)
        factoryOf(::GetSwapMessageMapper)
    }
