package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProviderImpl
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.provider.BlockchainProviderImpl
import co.electriccoin.zcash.ui.common.provider.CMCApiProvider
import co.electriccoin.zcash.ui.common.provider.CMCApiProviderImpl
import co.electriccoin.zcash.ui.common.provider.CrashReportingStorageProvider
import co.electriccoin.zcash.ui.common.provider.CrashReportingStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.EphemeralAddressStorageProvider
import co.electriccoin.zcash.ui.common.provider.EphemeralAddressStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.provider.GetZcashCurrencyProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteKeystoneStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteKeystoneStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.HttpClientProvider
import co.electriccoin.zcash.ui.common.provider.HttpClientProviderImpl
import co.electriccoin.zcash.ui.common.provider.HttpPirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProvider
import co.electriccoin.zcash.ui.common.provider.IsKeepScreenOnDuringRestoreProviderImpl
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProviderImpl
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProviderImpl
import co.electriccoin.zcash.ui.common.provider.KtorNearApiProvider
import co.electriccoin.zcash.ui.common.provider.KtorSwapkitApiProvider
import co.electriccoin.zcash.ui.common.provider.KtorVotingApiProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.NearApiProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProviderImpl
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProviderImpl
import co.electriccoin.zcash.ui.common.provider.RestoreTimestampStorageProvider
import co.electriccoin.zcash.ui.common.provider.RestoreTimestampStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProvider
import co.electriccoin.zcash.ui.common.provider.SelectedAccountUUIDProviderImpl
import co.electriccoin.zcash.ui.common.provider.ShieldFundsInfoProvider
import co.electriccoin.zcash.ui.common.provider.ShieldFundsInfoProviderImpl
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProvider
import co.electriccoin.zcash.ui.common.provider.SimpleSwapAssetProviderImpl
import co.electriccoin.zcash.ui.common.provider.SwapkitApiProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProviderImpl
import co.electriccoin.zcash.ui.common.provider.TokenIconProvider
import co.electriccoin.zcash.ui.common.provider.TokenIconProviderImpl
import co.electriccoin.zcash.ui.common.provider.TokenNameProvider
import co.electriccoin.zcash.ui.common.provider.TokenNameProviderImpl
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClientImpl
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProviderImpl
import co.electriccoin.zcash.ui.common.provider.WalletBackupConsentStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupConsentStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeCountStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeCountStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeTimestampStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupRemindMeTimestampStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProviderImpl
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val providerModule =
    module {
        factoryOf(::LightWalletEndpointProvider)
        singleOf(::GetVersionInfoProvider)
        singleOf(::GetZcashCurrencyProvider)
        singleOf(::SelectedAccountUUIDProviderImpl) bind SelectedAccountUUIDProvider::class
        singleOf(::PersistableWalletProviderImpl) bind PersistableWalletProvider::class
        singleOf(::PreferredFiatProviderImpl) bind PreferredFiatProvider::class
        singleOf(::IsServerSelectionAutomaticProviderImpl) bind IsServerSelectionAutomaticProvider::class
        singleOf(::SynchronizerProviderImpl) bind SynchronizerProvider::class
        singleOf(::ApplicationStateProviderImpl) bind ApplicationStateProvider::class
        singleOf(::RestoreTimestampStorageProviderImpl) bind RestoreTimestampStorageProvider::class
        singleOf(::WalletBackupRemindMeCountStorageProviderImpl) bind
            WalletBackupRemindMeCountStorageProvider::class
        singleOf(::WalletBackupRemindMeTimestampStorageProviderImpl) bind
            WalletBackupRemindMeTimestampStorageProvider::class
        singleOf(::WalletBackupFlagStorageProviderImpl) bind WalletBackupFlagStorageProvider::class
        singleOf(::WalletBackupConsentStorageProviderImpl) bind WalletBackupConsentStorageProvider::class
        singleOf(::WalletRestoringStateProviderImpl) bind WalletRestoringStateProvider::class
        singleOf(::CrashReportingStorageProviderImpl) bind CrashReportingStorageProvider::class
        singleOf(::ShieldFundsInfoProviderImpl) bind ShieldFundsInfoProvider::class
        singleOf(::IsExchangeRateEnabledStorageProviderImpl) bind IsExchangeRateEnabledStorageProvider::class
        singleOf(::HasSeenHowToVoteStorageProviderImpl) bind HasSeenHowToVoteStorageProvider::class
        singleOf(::HasSeenHowToVoteKeystoneStorageProviderImpl) bind
            HasSeenHowToVoteKeystoneStorageProvider::class
        singleOf(::IsTorEnabledStorageProviderImpl) bind IsTorEnabledStorageProvider::class
        singleOf(::BlockchainProviderImpl) bind BlockchainProvider::class
        singleOf(::TokenIconProviderImpl) bind TokenIconProvider::class
        singleOf(::TokenNameProviderImpl) bind TokenNameProvider::class
        singleOf(::KtorNearApiProvider) bind NearApiProvider::class
        singleOf(::KtorSwapkitApiProvider) bind SwapkitApiProvider::class
        factoryOf(::HttpClientProviderImpl) bind HttpClientProvider::class
        singleOf(::HttpPirSnapshotResolver) bind PirSnapshotResolver::class
        factoryOf(::SimpleSwapAssetProviderImpl) bind SimpleSwapAssetProvider::class
        factoryOf(::IsKeepScreenOnDuringRestoreProviderImpl) bind IsKeepScreenOnDuringRestoreProvider::class
        singleOf(::EphemeralAddressStorageProviderImpl) bind EphemeralAddressStorageProvider::class
        singleOf(::CMCApiProviderImpl) bind CMCApiProvider::class
        factoryOf(::KeystoneSDKProviderImpl) bind KeystoneSDKProvider::class
        singleOf(::VotingCryptoClientImpl) bind VotingCryptoClient::class
        singleOf(::VotingHotkeySeedProviderImpl) bind VotingHotkeySeedProvider::class
        singleOf(::KtorVotingApiProvider) bind VotingApiProvider::class
        singleOf(::VotingShareTrackingScheduler)
    }
