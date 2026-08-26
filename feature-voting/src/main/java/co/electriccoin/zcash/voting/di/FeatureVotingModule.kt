package co.electriccoin.zcash.voting.di

import co.electriccoin.zcash.ui.common.provider.HttpPirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.KtorVotingApiProvider
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClientImpl
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProviderImpl
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingConfigRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.VotingSessionStoreImpl
import co.electriccoin.zcash.ui.common.usecase.AuthorizeVotingSubmissionUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateVotingKeystonePcztEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.ParseVotingKeystonePCZTUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.ResolveVotingRoundSessionUseCase
import co.electriccoin.zcash.ui.common.usecase.SkipRemainingKeystoneBundlesUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitVotesUseCase
import co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase
import co.electriccoin.zcash.ui.common.voting.VotingHomeHooks
import co.electriccoin.zcash.ui.common.voting.VotingHomeMessageSource
import co.electriccoin.zcash.ui.common.voting.VotingNavContributor
import co.electriccoin.zcash.ui.common.voting.VotingSettingsEntry
import co.electriccoin.zcash.ui.screen.voting.chainconfig.VoteChainConfigVM
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingVM
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionVM
import co.electriccoin.zcash.ui.screen.voting.howtovote.VoteHowToVoteVM
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailVM
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListVM
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsVM
import co.electriccoin.zcash.ui.screen.voting.scankeystone.viewmodel.ScanKeystoneVotingPCZTViewModel
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingVM
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingVM
import co.electriccoin.zcash.voting.VotingHomeHooksImpl
import co.electriccoin.zcash.voting.VotingHomeMessageSourceImpl
import co.electriccoin.zcash.voting.VotingNavContributorImpl
import co.electriccoin.zcash.voting.VotingSettingsEntryImpl
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Everything the coinholder-voting feature contributes to the app's Koin graph — its own
 * providers, repositories, use cases, worker scheduler, view models, and the implementations of
 * ui-lib's voting contracts (see VotingContracts.kt). Wired in ZcashApplication.startKoin; unlike
 * migration, voting screens were never behind a contract layer before this module extraction, so
 * FeatureVotingImpls.kt is the first implementation of that seam.
 */
val featureVotingModule =
    module {
        // Contract implementations (the ui-lib seam)
        singleOf(::VotingHomeHooksImpl) bind VotingHomeHooks::class
        singleOf(::VotingSettingsEntryImpl) bind VotingSettingsEntry::class
        singleOf(::VotingNavContributorImpl) bind VotingNavContributor::class
        singleOf(::VotingHomeMessageSourceImpl) bind VotingHomeMessageSource::class

        // Providers
        singleOf(::VotingCryptoClientImpl) bind VotingCryptoClient::class
        singleOf(::VotingHotkeySeedProviderImpl) bind VotingHotkeySeedProvider::class
        single<VotingApiProvider> {
            KtorVotingApiProvider(
                httpClientProvider = get(),
                configurationRepository = get(),
                votingChainConfigRepository = get(),
                votingCryptoClient = get()
            )
        }
        singleOf(::HttpPirSnapshotResolver) bind PirSnapshotResolver::class
        singleOf(::VotingShareTrackingScheduler)

        // Repositories
        singleOf(::VotingConfigRepositoryImpl) bind VotingConfigRepository::class
        singleOf(::VotingChainConfigRepositoryImpl) bind VotingChainConfigRepository::class
        singleOf(::VotingApiRepositoryImpl) bind VotingApiRepository::class
        singleOf(::VotingRecoveryRepositoryImpl) bind VotingRecoveryRepository::class
        single<VotingProofPrecomputeRepository> {
            VotingProofPrecomputeRepositoryImpl(
                votingCryptoClient = get(),
                pirSnapshotResolver = get()
            )
        }
        singleOf(::VotingKeystoneRepositoryImpl) bind VotingKeystoneRepository::class
        singleOf(::VotingSessionStoreImpl) bind VotingSessionStore::class

        // Use cases
        factoryOf(::RefreshActiveVotingSessionUseCase)
        // Explicit factory: the defaulted logEndorsementFailure lambda must use its Kotlin
        // default — factoryOf resolves ALL constructor params via Koin and dies on the Function1
        // (mirrors CheckMigrationRecoveryUseCase's registration in featureMigrationModule).
        factory { RefreshVotingRoundsUseCase(get(), get()) }
        factoryOf(::GetAllVotingRoundsUseCase)
        factoryOf(::ResolveVotingRoundSessionUseCase)
        factoryOf(::PrepareVotingRoundUseCase)
        factoryOf(::AuthorizeVotingSubmissionUseCase)
        factoryOf(::SkipRemainingKeystoneBundlesUseCase)
        factoryOf(::SubmitVotesUseCase)
        factoryOf(::TrackVotingSharesUseCase)
        factoryOf(::ParseVotingKeystonePCZTUseCase)
        factoryOf(::CreateVotingKeystonePcztEncoderUseCase)

        // View models
        viewModelOf(::VoteCoinholderPollingVM)
        viewModelOf(::VoteChainConfigVM)
        viewModelOf(::VoteHowToVoteVM)
        viewModelOf(::VoteProposalListVM)
        viewModelOf(::VoteProposalDetailVM)
        viewModelOf(::VoteConfirmSubmissionVM)
        viewModelOf(::VoteTallyingVM)
        viewModelOf(::VoteResultsVM)
        viewModelOf(::SignKeystoneVotingVM)
        viewModelOf(::ScanKeystoneVotingPCZTViewModel)
    }
