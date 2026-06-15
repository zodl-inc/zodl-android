package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.repository.ApplicationStateRepository
import co.electriccoin.zcash.ui.common.repository.ApplicationStateRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.FlexaRepository
import co.electriccoin.zcash.ui.common.repository.FlexaRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.HomeMessageCacheRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageCacheRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepository
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.MockOrchardBalanceRepository
import co.electriccoin.zcash.ui.common.repository.MockOrchardBalanceRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.TransactionFilterRepository
import co.electriccoin.zcash.ui.common.repository.TransactionFilterRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import co.electriccoin.zcash.ui.common.repository.TransactionRepositoryImpl
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
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.repository.WalletRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.WalletSnapshotRepository
import co.electriccoin.zcash.ui.common.repository.WalletSnapshotRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule =
    module {
        singleOf(::WalletRepositoryImpl) bind WalletRepository::class
        singleOf(::ConfigurationRepositoryImpl) bind ConfigurationRepository::class
        singleOf(::ExchangeRateRepositoryImpl) bind ExchangeRateRepository::class
        singleOf(::FlexaRepositoryImpl) bind FlexaRepository::class
        singleOf(::BiometricRepositoryImpl) bind BiometricRepository::class
        singleOf(::KeystoneProposalRepositoryImpl) bind KeystoneProposalRepository::class
        singleOf(::TransactionRepositoryImpl) bind TransactionRepository::class
        singleOf(::TransactionFilterRepositoryImpl) bind TransactionFilterRepository::class
        singleOf(::ZashiProposalRepositoryImpl) bind ZashiProposalRepository::class
        singleOf(::HomeMessageCacheRepositoryImpl) bind HomeMessageCacheRepository::class
        singleOf(::WalletSnapshotRepositoryImpl) bind WalletSnapshotRepository::class
        singleOf(::ApplicationStateRepositoryImpl) bind ApplicationStateRepository::class
        singleOf(::AutomaticServerRepositoryImpl) bind AutomaticServerRepository::class
        singleOf(::SwapRepositoryImpl) bind SwapRepository::class
        singleOf(::EphemeralAddressRepositoryImpl) bind EphemeralAddressRepository::class
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
        singleOf(::MigrationPlanRepositoryImpl) bind MigrationPlanRepository::class
        singleOf(::MockOrchardBalanceRepositoryImpl) bind MockOrchardBalanceRepository::class
    }
